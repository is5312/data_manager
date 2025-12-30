package com.datamanager.backend.controller;

import com.datamanager.backend.dto.BatchStatusDto;
import com.datamanager.backend.dto.BatchUploadResponseDto;
import com.datamanager.backend.service.CsvBatchUploadService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * REST Controller for batch upload operations
 * Handles batch CSV uploads and batch status tracking
 */
@RestController
@RequestMapping("/api/schema")
@Slf4j
public class BatchController {

    private final CsvBatchUploadService csvBatchUploadService;
    private final JobExplorer jobExplorer;
    private final ObjectMapper objectMapper;

    public BatchController(
            CsvBatchUploadService csvBatchUploadService,
            JobExplorer jobExplorer,
            ObjectMapper objectMapper) {
        this.csvBatchUploadService = csvBatchUploadService;
        this.jobExplorer = jobExplorer;
        this.objectMapper = objectMapper;
    }

    /**
     * Batch upload (CSV or .gz/.gzip containing CSV).
     *
     * Saves the file, peeks header/sample (streaming), creates the table, then starts an async Spring Batch job.
     * Returns the batch job execution id.
     */
    @PostMapping("/tables/upload/batch")
    public ResponseEntity<BatchUploadResponseDto> batchUploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tableName") String tableName,
            @RequestParam(value = "columnTypes", required = false) String columnTypesJson,
            @RequestParam(value = "selectedColumnIndices", required = false) String selectedColumnIndicesJson
    ) {
        if (csvBatchUploadService == null) {
            throw new IllegalStateException("CsvBatchUploadService not configured");
        }

        List<String> columnTypes = null;
        try {
            if (columnTypesJson != null && !columnTypesJson.isBlank()) {
                columnTypes = objectMapper.readValue(columnTypesJson, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid columnTypes JSON; expected an array of strings");
        }

        List<Integer> selectedColumnIndices = null;
        try {
            if (selectedColumnIndicesJson != null && !selectedColumnIndicesJson.isBlank()) {
                selectedColumnIndices = objectMapper.readValue(selectedColumnIndicesJson, new TypeReference<List<Integer>>() {});
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid selectedColumnIndices JSON; expected an array of integers");
        }

        BatchUploadResponseDto response = csvBatchUploadService.startBatchUpload(file, tableName, columnTypes, selectedColumnIndices);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get progress for a running (or completed) batch upload job.
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchStatusDto> getBatchStatus(@PathVariable Long batchId) {
        if (jobExplorer == null) {
            throw new IllegalStateException("JobExplorer not configured");
        }
        JobExecution exec = jobExplorer.getJobExecution(batchId);
        if (exec == null) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }

        long read = exec.getStepExecutions().stream().mapToLong(se -> se.getReadCount()).sum();
        long write = exec.getStepExecutions().stream().mapToLong(se -> se.getWriteCount()).sum();
        long skip = exec.getStepExecutions().stream().mapToLong(se -> se.getSkipCount()).sum();

        // Extract all failure exceptions including nested causes from job and steps
        List<String> failureMessages = new ArrayList<>();
        
        // Check job-level exceptions
        for (Throwable ex : exec.getAllFailureExceptions()) {
            addExceptionChain(failureMessages, ex);
        }
        
        // Check step-level exceptions (where most batch errors occur)
        for (org.springframework.batch.core.StepExecution step : exec.getStepExecutions()) {
            // First try to get detailed errors from execution context (added by our listener)
            try {
                if (step.getExecutionContext().containsKey("detailedErrors")) {
                    String detailedErrors = step.getExecutionContext().getString("detailedErrors");
                    if (detailedErrors != null && !detailedErrors.isBlank()) {
                        // Split into lines and add each
                        for (String line : detailedErrors.split("\n")) {
                            if (!line.trim().isEmpty()) {
                                failureMessages.add(line);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read detailedErrors from execution context: {}", e.getMessage());
            }
            
            // Also check failure exceptions
            for (Throwable ex : step.getFailureExceptions()) {
                addExceptionChain(failureMessages, ex);
            }
        }
        
        // If no exceptions found in collections, parse from exitDescription
        if (failureMessages.isEmpty() && exec.getExitStatus() != null) {
            String exitDesc = exec.getExitStatus().getExitDescription();
            if (exitDesc != null && !exitDesc.isBlank()) {
                // Extract key error information from the stack trace
                String[] lines = exitDesc.split("\n");
                
                // Look for the most informative error messages
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    
                    // Capture the batch entry with VALUES (shows actual data)
                    if (line.contains("Batch entry") && line.contains("VALUES")) {
                        // Extract up to "was aborted:"
                        int abortIdx = line.indexOf(" was aborted:");
                        if (abortIdx > 0) {
                            String batchInfo = line.substring(0, abortIdx);
                            failureMessages.add("Batch Entry: " + batchInfo);
                        }
                    }
                    
                    // Capture lines with "ERROR:" (the actual Postgres error)
                    if (line.contains("ERROR:") && 
                        (line.contains("PSQLException") || line.contains("was aborted: ERROR:"))) {
                        // Extract just the ERROR message
                        int errorIdx = line.indexOf("ERROR:");
                        if (errorIdx >= 0) {
                            failureMessages.add("⚠️ " + line.substring(errorIdx));
                        }
                    }
                    
                    // Capture Postgres hints
                    if (line.contains("Hint:")) {
                        failureMessages.add("💡 " + line);
                    }
                    
                    // Capture position information
                    if (line.contains("Position:") && !line.contains("at ")) {
                        failureMessages.add("📍 " + line);
                    }
                    
                    // Stop after collecting enough context
                    if (failureMessages.size() >= 15) break;
                }
                
                // If still empty, take first meaningful lines
                if (failureMessages.isEmpty()) {
                    for (String line : lines) {
                        if (!line.trim().isEmpty() && !line.trim().startsWith("at ")) {
                            failureMessages.add(line.trim());
                            if (failureMessages.size() >= 5) break;
                        }
                    }
                }
            }
        }

        BatchStatusDto dto = BatchStatusDto.builder()
                .batchId(batchId)
                .status(exec.getStatus().toString())
                .exitCode(exec.getExitStatus() != null ? exec.getExitStatus().getExitCode() : null)
                .exitDescription(exec.getExitStatus() != null ? exec.getExitStatus().getExitDescription() : null)
                .startTime(exec.getStartTime())
                .endTime(exec.getEndTime())
                .readCount(read)
                .writeCount(write)
                .skipCount(skip)
                .failureExceptions(failureMessages)
                .build();

        return ResponseEntity.ok(dto);
    }

    /**
     * Helper method to extract full exception chain
     */
    private void addExceptionChain(List<String> messages, Throwable ex) {
        messages.add(ex.getClass().getName() + ": " + ex.getMessage());
        
        // Drill down to root causes
        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && depth < 10) {
            messages.add("  Caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
            cause = cause.getCause();
            depth++;
        }
    }
}

