package com.datamanager.backend.service;

import com.datamanager.backend.dto.BatchStatusDto;

/**
 * Service interface for batch job status operations
 * Handles retrieval and formatting of batch job execution status
 */
public interface BatchStatusService {

    /**
     * Get status information for a batch job execution
     * 
     * @param batchId The batch job execution ID
     * @return BatchStatusDto containing status, metrics, and failure information
     * @throws IllegalArgumentException if batch not found
     */
    BatchStatusDto getBatchStatus(Long batchId);
}

