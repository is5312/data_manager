package com.datamanager.backend.service.impl;

import com.datamanager.backend.dto.BatchStatusDto;
import com.datamanager.backend.service.BatchStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service implementation for batch job status operations
 */
@Service
@Slf4j
public class BatchStatusServiceImpl implements BatchStatusService {

    private final JobExplorer jobExplorer;

    public BatchStatusServiceImpl(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    @Override
    public BatchStatusDto getBatchStatus(Long batchId) {
        log.info("Getting batch status for batch ID: {}", batchId);

        JobExecution exec = jobExplorer.getJobExecution(batchId);
        if (exec == null) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }

        // Calculate metrics from step executions
        long read = exec.getStepExecutions().stream().mapToLong(se -> se.getReadCount()).sum();
        long write = exec.getStepExecutions().stream().mapToLong(se -> se.getWriteCount()).sum();
        long skip = exec.getStepExecutions().stream().mapToLong(se -> se.getSkipCount()).sum();

        // Extract failure exceptions
        List<String> failureMessages = extractFailureMessages(exec);

        return BatchStatusDto.builder()
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
    }

    /**
     * Extract all failure messages from job execution and step executions
     */
    private List<String> extractFailureMessages(JobExecution exec) {
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
                parseFailureMessagesFromExitDescription(failureMessages, exitDesc);
            }
        }

        return failureMessages;
    }

    /**
     * Parse failure messages from exit description string
     */
    private void parseFailureMessagesFromExitDescription(List<String> failureMessages, String exitDesc) {
        String[] lines = exitDesc.split("\n");

        // Look for the most informative error messages
        for (String line : lines) {
            String trimmedLine = line.trim();

            // Capture the batch entry with VALUES (shows actual data)
            if (trimmedLine.contains("Batch entry") && trimmedLine.contains("VALUES")) {
                // Extract up to "was aborted:"
                int abortIdx = trimmedLine.indexOf(" was aborted:");
                if (abortIdx > 0) {
                    String batchInfo = trimmedLine.substring(0, abortIdx);
                    failureMessages.add("Batch Entry: " + batchInfo);
                }
            }

            // Capture lines with "ERROR:" (the actual Postgres error)
            if (trimmedLine.contains("ERROR:") &&
                    (trimmedLine.contains("PSQLException") || trimmedLine.contains("was aborted: ERROR:"))) {
                // Extract just the ERROR message
                int errorIdx = trimmedLine.indexOf("ERROR:");
                if (errorIdx >= 0) {
                    failureMessages.add("⚠️ " + trimmedLine.substring(errorIdx));
                }
            }

            // Capture Postgres hints
            if (trimmedLine.contains("Hint:")) {
                failureMessages.add("💡 " + trimmedLine);
            }

            // Capture position information
            if (trimmedLine.contains("Position:") && !trimmedLine.contains("at ")) {
                failureMessages.add("📍 " + trimmedLine);
            }

            // Stop after collecting enough context
            if (failureMessages.size() >= 15) break;
        }

        // If still empty, take first meaningful lines
        if (failureMessages.isEmpty()) {
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("at ")) {
                    failureMessages.add(trimmedLine);
                    if (failureMessages.size() >= 5) break;
                }
            }
        }
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

