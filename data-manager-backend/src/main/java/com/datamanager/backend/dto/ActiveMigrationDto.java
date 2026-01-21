package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Active Migration Check
 * Used to check if a table has an active migration job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveMigrationDto {

    private boolean hasActiveMigration;
    private String jobId;
    private String status;
}
