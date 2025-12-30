package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for Table Metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMetadataDto {

    private Long id;
    private String label;
    private String physicalName;
    private String description;
    private Integer versionNo;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<ColumnMetadataDto> columns;
}
