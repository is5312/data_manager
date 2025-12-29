package com.datamanager.backend.batch.csv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Step listener that captures detailed error information including root causes
 * and stores them in the execution context for later retrieval.
 */
@Slf4j
@Component
public class DetailedErrorStepListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // Nothing to do before
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // If step failed, enhance the exit description with detailed error info
        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.FAILED.getExitCode())) {
            StringBuilder details = new StringBuilder();
            
            // Get all failure exceptions
            for (Throwable ex : stepExecution.getFailureExceptions()) {
                appendDetailedError(details, ex);
            }
            
            if (details.length() > 0) {
                // Store in execution context for later retrieval
                String errorDetails = details.toString();
                stepExecution.getExecutionContext().putString("detailedErrors", errorDetails);
                
                // Also log it
                log.error("Batch step failed with detailed errors:\n{}", errorDetails);
                
                // Update exit description with key details
                return new ExitStatus(ExitStatus.FAILED.getExitCode(), 
                    stepExecution.getExitStatus().getExitDescription() + "\n\nDETAILED_ERRORS:\n" + errorDetails);
            }
        }
        
        return stepExecution.getExitStatus();
    }

    private void appendDetailedError(StringBuilder sb, Throwable ex) {
        if (ex == null) return;
        
        // Add main exception
        sb.append("❌ ").append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append("\n");
        
        // Drill down through causes
        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && depth < 10) {
            String message = cause.getMessage();
            if (message != null) {
                // Extract key information from the message
                if (message.contains("ERROR:")) {
                    sb.append("   ⚠️  ").append(extractErrorMessage(message)).append("\n");
                } else if (message.contains("Hint:")) {
                    sb.append("   💡 ").append(message).append("\n");
                } else if (message.contains("Batch entry") && message.contains("VALUES")) {
                    sb.append("   📊 ").append(extractBatchEntry(message)).append("\n");
                } else {
                    sb.append("   ↳ ").append(cause.getClass().getSimpleName())
                      .append(": ").append(truncate(message, 200)).append("\n");
                }
            }
            cause = cause.getCause();
            depth++;
        }
    }

    private String extractErrorMessage(String message) {
        int errorIdx = message.indexOf("ERROR:");
        if (errorIdx >= 0) {
            // Get just the ERROR line, not the full stack
            String errorPart = message.substring(errorIdx);
            int newlineIdx = errorPart.indexOf("\n");
            if (newlineIdx > 0) {
                errorPart = errorPart.substring(0, newlineIdx);
            }
            return errorPart.trim();
        }
        return message;
    }

    private String extractBatchEntry(String message) {
        // Extract the VALUES clause to show actual data
        int valuesIdx = message.indexOf("VALUES");
        if (valuesIdx >= 0) {
            String valuesPart = message.substring(valuesIdx);
            int abortIdx = valuesPart.indexOf(" was aborted:");
            if (abortIdx > 0) {
                valuesPart = valuesPart.substring(0, abortIdx);
            }
            return "Batch data: " + truncate(valuesPart, 300);
        }
        return truncate(message, 200);
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }
}

