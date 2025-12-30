package com.datamanager.backend.controller;

import com.datamanager.backend.dto.BatchStatusDto;
import com.datamanager.backend.dto.BatchUploadResponseDto;
import com.datamanager.backend.service.BatchStatusService;
import com.datamanager.backend.service.CsvBatchUploadService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST Controller for batch upload operations
 * Handles batch CSV uploads and batch status tracking
 */
@RestController
@RequestMapping("/api/schema")
@Slf4j
public class BatchController {

    private final CsvBatchUploadService csvBatchUploadService;
    private final BatchStatusService batchStatusService;
    private final ObjectMapper objectMapper;

    public BatchController(
            CsvBatchUploadService csvBatchUploadService,
            BatchStatusService batchStatusService,
            ObjectMapper objectMapper) {
        this.csvBatchUploadService = csvBatchUploadService;
        this.batchStatusService = batchStatusService;
        this.objectMapper = objectMapper;
    }

    /**
     * Batch upload (CSV or .gz/.gzip containing CSV).
     *
     * Saves the file, peeks header/sample (streaming), creates the table, then
     * starts an async Spring Batch job.
     * Returns the batch job execution id.
     */
    @PostMapping("/tables/upload/batch")
    public ResponseEntity<BatchUploadResponseDto> batchUploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tableName") String tableName,
            @RequestParam(value = "columnTypes", required = false) String columnTypesJson,
            @RequestParam(value = "selectedColumnIndices", required = false) String selectedColumnIndicesJson,
            @RequestParam(value = "delimiter", required = false) Character delimiter,
            @RequestParam(value = "quoteChar", required = false) Character quoteChar,
            @RequestParam(value = "escapeChar", required = false) Character escapeChar) {
        if (csvBatchUploadService == null) {
            throw new IllegalStateException("CsvBatchUploadService not configured");
        }

        List<String> columnTypes = null;
        try {
            if (columnTypesJson != null && !columnTypesJson.isBlank()) {
                columnTypes = objectMapper.readValue(columnTypesJson, new TypeReference<List<String>>() {
                });
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid columnTypes JSON; expected an array of strings");
        }

        List<Integer> selectedColumnIndices = null;
        try {
            if (selectedColumnIndicesJson != null && !selectedColumnIndicesJson.isBlank()) {
                selectedColumnIndices = objectMapper.readValue(selectedColumnIndicesJson,
                        new TypeReference<List<Integer>>() {
                        });
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid selectedColumnIndices JSON; expected an array of integers");
        }

        BatchUploadResponseDto response = csvBatchUploadService.startBatchUpload(file, tableName, columnTypes,
                selectedColumnIndices, delimiter, quoteChar, escapeChar);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get progress for a running (or completed) batch upload job.
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchStatusDto> getBatchStatus(@PathVariable Long batchId) {
        log.info("GET /api/schema/batches/{} - Getting batch status", batchId);
        BatchStatusDto status = batchStatusService.getBatchStatus(batchId);
        return ResponseEntity.ok(status);
    }
}
