package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Migration Job Response
 * Returned when a migration job is queued
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationJobResponseDto {

    private String jobId;
    private String status;  // ENQUEUED, DUPLICATE
    private Long tableId;
    private String sourceSchema;
    private String targetSchema;
    private String message;
}
