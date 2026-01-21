package com.datamanager.performance.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Helper service for migration testing
 * Handles migration API calls and data verification
 */
@Slf4j
public class MigrationTestHelper {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DSLContext dsl;
    private final String backendBaseUrl;

    public MigrationTestHelper(String backendBaseUrl, DSLContext dsl) {
        this.backendBaseUrl = backendBaseUrl;
        this.dsl = dsl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Trigger migration via REST API
     * @return job ID for tracking migration status
     */
    public String triggerMigration(Long tableId, String sourceSchema, String targetSchema) throws IOException, InterruptedException {
        String url = String.format("%s/api/schema/migration/tables/%d/migrate?sourceSchema=%s&targetSchema=%s",
                backendBaseUrl, tableId, sourceSchema, targetSchema);

        log.info("Triggering migration: POST {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 202) {
            throw new RuntimeException("Migration trigger failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String jobId = json.get("jobId").asText();
        log.info("Migration job queued with ID: {}", jobId);
        return jobId;
    }

    /**
     * Poll migration job status until completion
     * @return true if migration succeeded, false if failed
     */
    public boolean waitForMigrationCompletion(String jobId, Duration timeout) throws IOException, InterruptedException, TimeoutException {
        String url = String.format("%s/api/schema/migration/jobs/%s", backendBaseUrl, jobId);
        Instant startTime = Instant.now();

        log.info("Polling migration job status: {}", jobId);

        while (true) {
            if (Duration.between(startTime, Instant.now()).compareTo(timeout) > 0) {
                throw new TimeoutException("Migration did not complete within timeout: " + timeout);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to get job status: HTTP {}", response.statusCode());
                Thread.sleep(1000);
                continue;
            }

            JsonNode json = objectMapper.readTree(response.body());
            String status = json.get("status").asText();

            log.debug("Migration job {} status: {}", jobId, status);

            if ("SUCCEEDED".equals(status)) {
                log.info("Migration completed successfully!");
                return true;
            } else if ("FAILED".equals(status)) {
                String failureReason = json.has("failureReason") ? json.get("failureReason").asText() : "Unknown";
                log.error("Migration failed: {}", failureReason);
                return false;
            }

            // Still processing, wait and retry
            Thread.sleep(1000);
        }
    }

    /**
     * Get row count from a table in a specific schema
     */
    public long getRowCount(String schema, String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM \"%s\".\"%s\"", schema, tableName);
        Result<Record> result = dsl.fetch(sql);
        return result.get(0).get(0, Long.class);
    }

    /**
     * Get all row IDs from a table in a specific schema
     */
    public Set<Long> getAllRowIds(String schema, String tableName) {
        String sql = String.format("SELECT id FROM \"%s\".\"%s\" ORDER BY id", schema, tableName);
        Result<Record> result = dsl.fetch(sql);
        Set<Long> ids = new HashSet<>();
        for (Record record : result) {
            ids.add(record.get(0, Long.class));
        }
        return ids;
    }

    /**
     * Get shadow table name from metadata after migration
     */
    public String getShadowTableName(Long tableId, String schema) {
        String sql = String.format("SELECT tbl_link FROM \"%s\".base_reference_table WHERE id = ?", schema);
        Record record = dsl.fetchOne(sql, tableId);
        if (record == null) {
            throw new RuntimeException("Table not found in schema: " + schema);
        }
        return record.get(0, String.class);
    }

    /**
     * Verify no duplicate rows exist
     */
    public boolean verifyNoDuplicates(String schema, String tableName) {
        String sql = String.format(
                "SELECT id, COUNT(*) as cnt FROM \"%s\".\"%s\" GROUP BY id HAVING COUNT(*) > 1",
                schema, tableName);
        Result<Record> result = dsl.fetch(sql);
        boolean hasDuplicates = !result.isEmpty();
        if (hasDuplicates) {
            log.error("Found {} duplicate rows in {}.{}", result.size(), schema, tableName);
        }
        return !hasDuplicates;
    }
}
