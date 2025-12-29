package com.datamanager.backend.batch.csv;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Streaming CSV reader (optionally gzip) that returns a Map keyed by physical column names.
 * This never decompresses the whole file to disk; it reads sequentially from the file path.
 * Supports filtering columns by selectedColumnIndices.
 */
public class CsvRowItemReader implements ItemStreamReader<Map<String, Object>> {

    private final String filePath;
    private final boolean gzip;
    private final List<String> physicalColumns;
    private final List<String> columnTypes;
    private final List<Integer> selectedColumnIndices;  // null means all columns

    private CSVReader reader;
    private boolean headerSkipped = false;

    public CsvRowItemReader(String filePath, boolean gzip, List<String> physicalColumns, List<String> columnTypes) {
        this(filePath, gzip, physicalColumns, columnTypes, null);
    }

    public CsvRowItemReader(String filePath, boolean gzip, List<String> physicalColumns, List<String> columnTypes, List<Integer> selectedColumnIndices) {
        this.filePath = filePath;
        this.gzip = gzip;
        this.physicalColumns = physicalColumns;
        this.columnTypes = columnTypes;
        this.selectedColumnIndices = selectedColumnIndices;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            InputStream in = Files.newInputStream(Path.of(filePath));
            if (gzip) {
                in = new GZIPInputStream(in);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            // OpenCSV is more tolerant of imperfect CSVs while still streaming.
            var parser = new CSVParserBuilder()
                    .withSeparator(',')
                    .withQuoteChar('"')
                    .withEscapeChar('\\')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            this.reader = new CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .build();
            this.headerSkipped = false;
        } catch (Exception e) {
            throw new ItemStreamException("Failed to open CSV reader for " + filePath, e);
        }
    }

    @Override
    public Map<String, Object> read() throws Exception {
        if (reader == null) {
            return null;
        }

        if (!headerSkipped) {
            // Skip header row
            reader.readNext();
            headerSkipped = true;
        }

        while (true) {
            String[] values = reader.readNext();
            if (values == null) {
                return null;
            }

            Map<String, Object> rowMap = new HashMap<>();
            boolean hasAnyValue = false;

            for (int i = 0; i < physicalColumns.size(); i++) {
                String col = physicalColumns.get(i);
                String type = i < columnTypes.size() ? columnTypes.get(i) : "VARCHAR";
                
                // Map to the original CSV column index if filtering is active
                int csvColumnIndex = (selectedColumnIndices != null && i < selectedColumnIndices.size()) 
                    ? selectedColumnIndices.get(i) 
                    : i;
                
                String raw = csvColumnIndex < values.length ? values[csvColumnIndex] : null;
                Object typed = parseTypedValue(raw, type);
                // Always include all keys (with null for empty cells) so named-parameter inserts bind reliably.
                rowMap.put(col, typed);
                if (typed != null) {
                    hasAnyValue = true;
                }
            }

            // Skip completely empty rows
            if (hasAnyValue) {
                return rowMap;
            }
        }
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (Exception e) {
            throw new ItemStreamException("Failed to close CSV reader", e);
        } finally {
            reader = null;
            headerSkipped = false;
        }
    }

    private Object parseTypedValue(String rawValue, String inferredType) {
        String v = normalizeCell(rawValue);
        if (v == null) return null;

        String type = inferredType == null ? "TEXT" : inferredType.toUpperCase().trim();
        try {
            return switch (type) {
                case "BOOLEAN", "BOOL" -> parseBoolean(v);
                case "INTEGER", "INT", "BIGINT", "LONG" -> Long.parseLong(v);
                case "DECIMAL", "NUMERIC" -> new BigDecimal(v);
                case "DOUBLE", "DOUBLE PRECISION" -> Double.parseDouble(v);
                case "FLOAT", "REAL" -> Float.parseFloat(v);
                case "DATE" -> LocalDate.parse(v);
                case "TIMESTAMP" -> parseTimestamp(v);
                default -> v; // TEXT, VARCHAR, etc.
            };
        } catch (NumberFormatException e) {
            // Log but still return the string - DB will reject if type mismatch
            System.err.println("WARN: Failed to parse '" + v + "' as " + type + ": " + e.getMessage());
            return v;
        } catch (DateTimeParseException e) {
            System.err.println("WARN: Failed to parse '" + v + "' as " + type + ": " + e.getMessage());
            return v;
        } catch (Exception e) {
            System.err.println("ERROR: Unexpected error parsing '" + v + "' as " + type + ": " + e.getMessage());
            return v;
        }
    }

    private String normalizeCell(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.equalsIgnoreCase("null")) return null;
        return v;
    }

    private Boolean parseBoolean(String v) {
        String s = v.trim().toLowerCase();
        return switch (s) {
            case "true", "t", "yes", "y", "1" -> true;
            case "false", "f", "no", "n", "0" -> false;
            default -> null;
        };
    }

    private Object parseTimestamp(String v) {
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        OffsetDateTime odt = OffsetDateTime.parse(v);
        return odt.toLocalDateTime();
    }
}


