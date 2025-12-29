package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponseDto {
    private Long batchId;
    private TableMetadataDto table;
    private String message;
}


