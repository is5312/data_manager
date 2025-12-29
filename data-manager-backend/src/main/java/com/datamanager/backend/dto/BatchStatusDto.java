package com.datamanager.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatusDto {
    private Long batchId;
    private String status;
    private String exitCode;
    private String exitDescription;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long readCount;
    private Long writeCount;
    private Long skipCount;
    private List<String> failureExceptions;
}


