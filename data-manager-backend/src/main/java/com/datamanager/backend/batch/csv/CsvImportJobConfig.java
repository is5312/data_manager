package com.datamanager.backend.batch.csv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class CsvImportJobConfig {

    public static final String JOB_NAME = "csvGzipImportJob";

    @Bean(name = JOB_NAME)
    public Job csvGzipImportJob(JobRepository jobRepository, Step csvGzipImportStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(csvGzipImportStep)
                .build();
    }

    @Bean
    public Step csvGzipImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemStreamReader<Map<String, Object>> csvRowReader,
            JdbcBatchItemWriter<Map<String, Object>> csvRowWriter,
            DetailedErrorStepListener errorListener) {
        return new StepBuilder("csvGzipImportStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(10000, transactionManager) // Optimized batch size for
                                                                                            // performance
                .reader(csvRowReader)
                .writer(csvRowWriter)
                .listener(errorListener)
                // Skip rows that fail due to size/data errors (e.g., row too big)
                .faultTolerant()
                .skip(org.springframework.dao.DataAccessException.class)
                .skipLimit(10000) // Allow skipping up to 10,000 bad rows
                .build();
    }

    @Bean
    @StepScope
    public ItemStreamReader<Map<String, Object>> csvRowReader(
            ObjectMapper objectMapper,
            @Value("#{jobParameters['filePath']}") String filePath,
            @Value("#{jobParameters['gzip']}") String gzip,
            @Value("#{jobParameters['tableId']}") Long tableId,
            @Value("#{jobParameters['selectedColumnIndicesJson']}") String selectedColumnIndicesJson,
            @Value("#{jobParameters['delimiter']}") String delimiterStr,
            @Value("#{jobParameters['quoteChar']}") String quoteCharStr,
            @Value("#{jobParameters['escapeChar']}") String escapeCharStr,
            com.datamanager.backend.service.TableMetadataService tableMetadataService) {
        // Fetch columns from database (avoids VARCHAR(2500) limit for large column
        // counts)
        List<com.datamanager.backend.dto.ColumnMetadataDto> columns = tableMetadataService.getColumnsByTableId(tableId);

        List<String> physicalColumns = columns.stream()
                .map(com.datamanager.backend.dto.ColumnMetadataDto::getPhysicalName)
                .toList();

        List<String> columnTypes = columns.stream()
                .map(com.datamanager.backend.dto.ColumnMetadataDto::getType)
                .toList();

        // Parse selected column indices (if provided)
        List<Integer> selectedColumnIndices = null;
        try {
            if (selectedColumnIndicesJson != null && !selectedColumnIndicesJson.isBlank()) {
                selectedColumnIndices = objectMapper.readValue(selectedColumnIndicesJson,
                        new TypeReference<List<Integer>>() {
                        });
            }
        } catch (Exception e) {
            // If parsing fails, proceed without filtering (use all columns)
            selectedColumnIndices = null;
        }

        boolean isGzip = "true".equalsIgnoreCase(gzip);

        Character delimiter = (delimiterStr != null && !delimiterStr.isEmpty()) ? delimiterStr.charAt(0) : ',';
        Character quoteChar = (quoteCharStr != null && !quoteCharStr.isEmpty()) ? quoteCharStr.charAt(0) : '"';
        Character escapeChar = (escapeCharStr != null && !escapeCharStr.isEmpty()) ? escapeCharStr.charAt(0) : '\\';

        return new CsvRowItemReader(filePath, isGzip, physicalColumns, columnTypes, selectedColumnIndices, delimiter,
                quoteChar, escapeChar);
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<Map<String, Object>> csvRowWriter(
            DataSource dataSource,
            @Value("#{jobParameters['tableId']}") Long tableId,
            @Value("#{jobParameters['physicalTableName']}") String physicalTableName,
            com.datamanager.backend.service.TableMetadataService tableMetadataService) {
        // Fetch columns from database
        List<com.datamanager.backend.dto.ColumnMetadataDto> columns = tableMetadataService.getColumnsByTableId(tableId);
        List<String> physicalColumns = columns.stream()
                .map(com.datamanager.backend.dto.ColumnMetadataDto::getPhysicalName)
                .toList();

        // Quote identifiers because our generated ULIDs can contain uppercase
        // characters.
        // Postgres folds unquoted identifiers to lowercase.
        String quotedTable = quoteIdentifier(physicalTableName);
        String columnsSql = physicalColumns.stream().map(CsvImportJobConfig::quoteIdentifier)
                .reduce((a, b) -> a + ", " + b).orElse("");
        String valuesSql = physicalColumns.stream().map(c -> ":" + c).reduce((a, b) -> a + ", " + b).orElse("");
        String sql = "INSERT INTO " + quotedTable + " (" + columnsSql + ") VALUES (" + valuesSql + ")";

        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);

        return new JdbcBatchItemWriterBuilder<Map<String, Object>>()
                .namedParametersJdbcTemplate(template)
                .sql(sql)
                .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource(item))
                .assertUpdates(false) // Don't fail on skipped rows
                .build();
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }
        // Identifiers are generated/sanitized elsewhere; still escape quotes
        // defensively.
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
