package com.datamanager.backend.service;

import com.datamanager.backend.batch.csv.CsvImportJobConfig;
import com.datamanager.backend.dto.BatchUploadResponseDto;
import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.dto.TableMetadataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class CsvBatchUploadService {

    private final TableMetadataService tableMetadataService;
    private final JobLauncher jobLauncher;
    private final Job csvGzipImportJob;
    private final ObjectMapper objectMapper;

    public CsvBatchUploadService(
            TableMetadataService tableMetadataService,
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            @Qualifier(CsvImportJobConfig.JOB_NAME) Job csvGzipImportJob,
            ObjectMapper objectMapper
    ) {
        this.tableMetadataService = tableMetadataService;
        this.jobLauncher = jobLauncher;
        this.csvGzipImportJob = csvGzipImportJob;
        this.objectMapper = objectMapper;
    }

    public BatchUploadResponseDto startBatchUpload(MultipartFile file, String tableName, List<String> columnTypesOverride, List<Integer> selectedColumnIndices) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        boolean gzip = originalName.endsWith(".gz") || originalName.endsWith(".gzip");

        Path savedPath = saveUploadToDisk(file, originalName);

        // Peek header + larger sample for inference (5000 rows to catch data quality issues)
        // This streams and doesn't decompress the whole file to memory
        CsvPeekResult peek = peekHeadersAndInferTypes(savedPath, gzip, 5000, selectedColumnIndices);

        List<String> finalTypes = applyTypeOverrides(peek.inferredTypes, columnTypesOverride);

        // Create table + columns in header order (label = normalized header)
        TableMetadataDto table = tableMetadataService.createTable(tableName.trim());
        List<String> physicalColumns = new ArrayList<>();

        for (int i = 0; i < peek.headers.size(); i++) {
            String label = peek.headers.get(i);
            String type = i < finalTypes.size() ? finalTypes.get(i) : "VARCHAR";
            ColumnMetadataDto created = tableMetadataService.addColumn(table.getId(), label, type);
            physicalColumns.add(created.getPhysicalName());
        }

        try {
            // Spring Batch's BATCH_JOB_EXECUTION_PARAMS has VARCHAR(2500) limit
            // For large column counts, don't store the full arrays - pass table ID instead
            JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                    .addString("filePath", savedPath.toAbsolutePath().toString())
                    .addString("gzip", Boolean.toString(gzip))
                    .addLong("tableId", table.getId())  // Pass table ID to look up columns from DB
                    .addString("physicalTableName", table.getPhysicalName())
                    .addLong("requestedAt", Instant.now().toEpochMilli());
            
            // Add selected column indices if provided (typically small enough to fit in params)
            if (peek.selectedColumnIndices() != null && !peek.selectedColumnIndices().isEmpty()) {
                try {
                    String indicesJson = objectMapper.writeValueAsString(peek.selectedColumnIndices());
                    if (indicesJson.length() < 2400) {  // Leave room for other params
                        paramsBuilder.addString("selectedColumnIndicesJson", indicesJson);
                        log.info("Storing {} selected column indices in job params", peek.selectedColumnIndices().size());
                    } else {
                        log.warn("Selected column indices JSON too large ({}), will re-fetch from table metadata", indicesJson.length());
                    }
                } catch (Exception e) {
                    log.warn("Failed to serialize selected column indices", e);
                }
            }
            
            JobParameters params = paramsBuilder.toJobParameters();

            JobExecution execution = jobLauncher.run(csvGzipImportJob, params);
            Long batchId = execution.getId();

            return BatchUploadResponseDto.builder()
                    .batchId(batchId)
                    .table(table)
                    .message("Batch upload started")
                    .build();
        } catch (Exception e) {
            log.error("Failed to start batch upload job", e);
            throw new RuntimeException("Failed to start batch upload: " + e.getMessage(), e);
        }
    }

    private Path saveUploadToDisk(MultipartFile file, String originalName) {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "data-manager-uploads");
            Files.createDirectories(dir);
            String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = dir.resolve(System.currentTimeMillis() + "_" + safeName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            log.info("Saved upload to {}", target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save uploaded file: " + e.getMessage(), e);
        }
    }

    private CsvPeekResult peekHeadersAndInferTypes(Path path, boolean gzip, int sampleSize, List<Integer> selectedColumnIndices) {
        try (InputStream raw = Files.newInputStream(path);
             InputStream maybeGzip = gzip ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(maybeGzip, StandardCharsets.UTF_8))) {

            var parser = new CSVParserBuilder()
                    .withSeparator(',')
                    .withQuoteChar('"')
                    .withEscapeChar('\\')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            try (CSVReader csv = new CSVReaderBuilder(reader).withCSVParser(parser).build()) {
                String[] headerRow = csv.readNext();
                if (headerRow == null || headerRow.length == 0) {
                    throw new IllegalArgumentException("CSV file is empty or has no header");
                }

                // Filter columns if indices are provided
                List<String> headerNames = new ArrayList<>();
                if (selectedColumnIndices != null && !selectedColumnIndices.isEmpty()) {
                    log.info("Filtering {} columns from {} total columns", selectedColumnIndices.size(), headerRow.length);
                    for (int idx : selectedColumnIndices) {
                        if (idx >= 0 && idx < headerRow.length) {
                            headerNames.add(headerRow[idx]);
                        } else {
                            log.warn("Column index {} out of bounds (total: {})", idx, headerRow.length);
                        }
                    }
                } else {
                    // Include all columns
                    for (String h : headerRow) headerNames.add(h);
                }
                
                List<String> normalizedHeaders = normalizeAndUniquifyHeaders(headerNames);

                List<String[]> sampleRows = new ArrayList<>();
                int count = 0;
                while (count < sampleSize) {
                    String[] row = csv.readNext();
                    if (row == null) break;
                    
                    // Filter row columns to match selected headers
                    if (selectedColumnIndices != null && !selectedColumnIndices.isEmpty()) {
                        String[] filteredRow = new String[selectedColumnIndices.size()];
                        for (int i = 0; i < selectedColumnIndices.size(); i++) {
                            int idx = selectedColumnIndices.get(i);
                            filteredRow[i] = (idx >= 0 && idx < row.length) ? row[idx] : "";
                        }
                        sampleRows.add(filteredRow);
                    } else {
                        sampleRows.add(row);
                    }
                    count++;
                }

                List<String> inferredTypes = inferColumnTypes(normalizedHeaders, sampleRows);
                log.info("Type inference for {} columns: {}", normalizedHeaders.size(), inferredTypes);
                return new CsvPeekResult(normalizedHeaders, inferredTypes, selectedColumnIndices);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read CSV header/sample: " + e.getMessage(), e);
        }
    }

    private List<String> normalizeAndUniquifyHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();

        for (int i = 0; i < rawHeaders.size(); i++) {
            String h = rawHeaders.get(i) == null ? "" : rawHeaders.get(i).trim();
            if (h.isBlank()) h = "column_" + (i + 1);
            h = h.replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9_]", "_");

            String candidate = h;
            int suffix = 2;
            while (used.contains(candidate)) {
                candidate = h + "_" + suffix;
                suffix++;
            }
            used.add(candidate);
            headers.add(candidate);
        }
        return headers;
    }

    private List<String> inferColumnTypes(List<String> headers, List<String[]> records) {
        List<String> types = new ArrayList<>();

        for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
            boolean allBooleans = true;
            boolean allIntegers = true;
            boolean allDecimals = true;
            boolean allDates = true;
            boolean allTimestamps = true;
            boolean sawAnyValue = false;

            for (String[] record : records) {
                if (record == null || colIndex >= record.length) continue;
                String v = normalizeCell(record[colIndex]);
                if (v == null) continue;
                sawAnyValue = true;

                if (!isBoolean(v)) allBooleans = false;
                if (!isInteger(v)) allIntegers = false;
                if (!isDecimal(v)) allDecimals = false;
                if (!isDate(v)) allDates = false;
                if (!isTimestamp(v)) allTimestamps = false;
            }

            // CONSERVATIVE: Default to TEXT for all columns to avoid type mismatch errors.
            // Users can change column types after upload if needed.
            // Previous logic was too aggressive and caused errors when sample didn't
            // represent the full dataset (e.g., row 5001+ had text in numeric columns).
            if (!sawAnyValue) {
                types.add("TEXT");
            } else if (allBooleans && sawAnyValue) {
                types.add("TEXT"); // Even booleans default to TEXT for safety
            } else if (allIntegers && sawAnyValue) {
                types.add("TEXT"); // Conservative: use TEXT, users can change to BIGINT later
            } else if (allDecimals && sawAnyValue) {
                types.add("TEXT"); // Conservative: use TEXT, users can change to DECIMAL later
            } else if (allDates && sawAnyValue) {
                types.add("TEXT"); // Conservative: use TEXT, users can change to DATE later
            } else if (allTimestamps && sawAnyValue) {
                types.add("TEXT"); // Conservative: use TEXT, users can change to TIMESTAMP later
            } else {
                types.add("TEXT");
            }
        }

        return types;
    }

    private List<String> applyTypeOverrides(List<String> inferred, List<String> override) {
        if (override == null || override.isEmpty()) return inferred;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < inferred.size(); i++) {
            String o = i < override.size() ? override.get(i) : null;
            if (o != null && !o.isBlank()) out.add(o.trim());
            else out.add(inferred.get(i));
        }
        return out;
    }

    private String normalizeCell(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.equalsIgnoreCase("null")) return null;
        return v;
    }

    private boolean isBoolean(String v) {
        String s = v.trim().toLowerCase();
        return s.equals("true") || s.equals("false") || s.equals("t") || s.equals("f")
                || s.equals("yes") || s.equals("no") || s.equals("y") || s.equals("n")
                || s.equals("1") || s.equals("0");
    }

    private boolean isInteger(String v) {
        try {
            Long.parseLong(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDecimal(String v) {
        try {
            new java.math.BigDecimal(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDate(String v) {
        try {
            java.time.LocalDate.parse(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTimestamp(String v) {
        try {
            java.time.LocalDateTime.parse(v);
            return true;
        } catch (Exception ignored) {
            // fall through
        }
        try {
            java.time.OffsetDateTime.parse(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private record CsvPeekResult(List<String> headers, List<String> inferredTypes, List<Integer> selectedColumnIndices) {}
}


