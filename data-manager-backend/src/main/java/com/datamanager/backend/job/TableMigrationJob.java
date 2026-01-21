package com.datamanager.backend.job;

import com.datamanager.backend.dto.MigrationResponseDto;
import com.datamanager.backend.service.TableMigrationService;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/**
 * Background job for table migration
 * Executes migrations asynchronously using JobRunr
 */
@Component
@Slf4j
public class TableMigrationJob {

    private final TableMigrationService migrationService;

    public TableMigrationJob(TableMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    /**
     * Execute table migration from source to target schema
     * 
     * @param tableId Table ID to migrate
     * @param sourceSchema Source schema name
     * @param targetSchema Target schema name
     * @return Migration response with details
     */
    @Job(name = "Migrate Table %0 from %1 to %2", retries = 0)
    public MigrationResponseDto execute(Long tableId, String sourceSchema, String targetSchema) {
        log.info("Starting async migration for table {} from {} to {}", tableId, sourceSchema, targetSchema);
        
        try {
            MigrationResponseDto result = migrationService.migrateTable(tableId, sourceSchema, targetSchema);
            log.info("Migration completed successfully for table {}: {}", tableId, result.getMessage());
            return result;
        } catch (Exception e) {
            log.error("Migration failed for table {} from {} to {}", tableId, sourceSchema, targetSchema, e);
            throw new RuntimeException("Migration failed: " + e.getMessage(), e);
        }
    }
}
