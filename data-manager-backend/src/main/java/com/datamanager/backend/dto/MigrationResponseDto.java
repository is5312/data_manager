package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Migration Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationResponseDto {

    private String status;
    private String message;
    private String shadowTableName;
    private String targetSchema;
    private Long tableId;
    private String details;
}
