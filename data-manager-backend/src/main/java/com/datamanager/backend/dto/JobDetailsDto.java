package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Job Details
 * Contains detailed information about a background job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailsDto {

    private String jobId;
    private String status;  // ENQUEUED, PROCESSING, SUCCEEDED, FAILED
    private String jobName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private MigrationResponseDto result;  // Present if succeeded
    private String failureReason;  // Present if failed
}
