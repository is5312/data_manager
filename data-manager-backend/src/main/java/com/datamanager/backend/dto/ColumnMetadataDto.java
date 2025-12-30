package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Column Metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadataDto {

    private Long id;
    private Long tableId;
    private String label;
    private String physicalName;
    private String tablePhysicalName;
    private String type;
    private String description;
    private Integer versionNo;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
