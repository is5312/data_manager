package com.datamanager.backend.controller;

import com.datamanager.backend.dto.ActiveMigrationDto;
import com.datamanager.backend.dto.JobDetailsDto;
import com.datamanager.backend.dto.MigrationJobResponseDto;
import com.datamanager.backend.dto.MigrationResponseDto;
import com.datamanager.backend.job.TableMigrationJob;
import com.datamanager.backend.service.TableMigrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jobrunr.jobs.JobId;

/**
 * REST Controller for table migration operations with async job support
 */
@RestController
@RequestMapping("/api/schema/migration")
@Slf4j
public class MigrationController {

    private final TableMigrationService migrationService;
    private final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final TableMigrationJob tableMigrationJob;
    private final ObjectMapper objectMapper;

    public MigrationController(
            TableMigrationService migrationService,
            JobScheduler jobScheduler,
            StorageProvider storageProvider,
            TableMigrationJob tableMigrationJob,
            ObjectMapper objectMapper) {
        this.migrationService = migrationService;
        this.jobScheduler = jobScheduler;
        this.storageProvider = storageProvider;
        this.tableMigrationJob = tableMigrationJob;
        this.objectMapper = objectMapper;
    }

    /**
     * Get list of available schemas from configuration
     */
    @GetMapping("/schemas")
    public ResponseEntity<List<String>> getAvailableSchemas() {
        log.info("GET /api/schema/migration/schemas - Fetching available schemas");
        List<String> schemas = migrationService.getAvailableSchemas();
        return ResponseEntity.ok(schemas);
    }

    /**
     * Migrate a table from source schema to target schema (async)
     * Returns immediately with job ID
     */
    @PostMapping("/tables/{tableId}/migrate")
    public ResponseEntity<MigrationJobResponseDto> migrateTable(
            @PathVariable Long tableId,
            @RequestParam String sourceSchema,
            @RequestParam String targetSchema) {
        log.info("POST /api/schema/migration/tables/{}/migrate - Queueing migration from {} to {}", 
                tableId, sourceSchema, targetSchema);

        // Note: Duplicate job checking is simplified in this version
        // Advanced duplicate detection can be added by querying the storage provider directly

        // Enqueue the migration job using BackgroundJob with explicit parameters
        JobId jobId = BackgroundJob.<TableMigrationJob>enqueue(x -> x.execute(tableId, sourceSchema, targetSchema));
        
        log.info("Migration job queued successfully with ID: {}", jobId);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(MigrationJobResponseDto.builder()
                        .jobId(jobId.asUUID().toString())
                        .status("ENQUEUED")
                        .message("Migration job queued successfully")
                        .tableId(tableId)
                        .sourceSchema(sourceSchema)
                        .targetSchema(targetSchema)
                        .build());
    }

    /**
     * Get job status by job ID
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobDetailsDto> getJobStatus(@PathVariable String jobId) {
        log.info("GET /api/schema/migration/jobs/{} - Fetching job status", jobId);
        
        try {
            Job job = storageProvider.getJobById(UUID.fromString(jobId));
            
            JobDetailsDto details = JobDetailsDto.builder()
                    .jobId(job.getId().toString())
                    .status(job.getState().toString())
                    .jobName(job.getJobName())
                    .createdAt(LocalDateTime.ofInstant(job.getCreatedAt(), ZoneId.systemDefault()))
                    .updatedAt(LocalDateTime.ofInstant(job.getUpdatedAt(), ZoneId.systemDefault()))
                    .build();

            // If job succeeded, extract the result
            if (job.getState() == StateName.SUCCEEDED && job.getJobDetails() != null) {
                try {
                    Object result = job.getJobDetails().getCacheable();
                    if (result instanceof MigrationResponseDto) {
                        details.setResult((MigrationResponseDto) result);
                    }
                } catch (Exception e) {
                    log.warn("Could not extract job result", e);
                }
            }

            // If job failed, extract the failure reason
            if (job.getState() == StateName.FAILED) {
                try {
                    org.jobrunr.jobs.states.JobState jobState = job.getJobState();
                    if (jobState instanceof org.jobrunr.jobs.states.FailedState) {
                        details.setFailureReason(((org.jobrunr.jobs.states.FailedState) jobState).getException().getMessage());
                    } else {
                        details.setFailureReason("Unknown error");
                    }
                } catch (Exception e) {
                    details.setFailureReason("Error retrieving failure reason");
                }
            }

            return ResponseEntity.ok(details);
        } catch (Exception e) {
            log.error("Error fetching job status for ID: {}", jobId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List all migration jobs with optional status filter and pagination
     * Note: This is a simplified implementation
     */
    @GetMapping("/jobs")
    public ResponseEntity<Page<JobDetailsDto>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/schema/migration/jobs - Listing jobs (status={}, page={}, size={})", status, page, size);

        // Return empty list for now - can be enhanced with StorageProvider queries
        List<JobDetailsDto> jobDetails = new ArrayList<>();
        Page<JobDetailsDto> pagedResult = new PageImpl<>(jobDetails, Pageable.ofSize(size).withPage(page), 0);
        return ResponseEntity.ok(pagedResult);
    }

    /**
     * Check if a table has an active migration to the specified target schema
     * Note: This is a simplified implementation that always returns false
     */
    @GetMapping("/tables/{tableId}/active")
    public ResponseEntity<ActiveMigrationDto> checkActiveMigration(
            @PathVariable Long tableId,
            @RequestParam String targetSchema) {
        log.info("GET /api/schema/migration/tables/{}/active - Checking for active migration to {}", 
                tableId, targetSchema);

        // Return false for now - can be enhanced with StorageProvider queries
        return ResponseEntity.ok(ActiveMigrationDto.builder()
                .hasActiveMigration(false)
                .build());
    }
}
