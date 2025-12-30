package com.datamanager.backend.controller;

import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.service.TableMetadataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST Controller for table operations
 * Handles CRUD operations for tables and synchronous CSV uploads
 */
@RestController
@RequestMapping("/api/schema/tables")
@Slf4j
public class TableController {

    private final TableMetadataService tableMetadataService;
    private final ObjectMapper objectMapper;

    public TableController(
            TableMetadataService tableMetadataService,
            ObjectMapper objectMapper) {
        this.tableMetadataService = tableMetadataService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get all tables
     */
    @GetMapping
    public ResponseEntity<List<TableMetadataDto>> getAllTables() {
        log.info("GET /api/schema/tables - Fetching all tables");
        List<TableMetadataDto> tables = tableMetadataService.getAllTables();
        return ResponseEntity.ok(tables);
    }

    /**
     * Get table by ID
     */
    @GetMapping("/{tableId}")
    public ResponseEntity<TableMetadataDto> getTableById(@PathVariable Long tableId) {
        log.info("GET /api/schema/tables/{} - Fetching table", tableId);
        TableMetadataDto table = tableMetadataService.getTableById(tableId);
        return ResponseEntity.ok(table);
    }

    /**
     * Create a new table
     */
    @PostMapping
    public ResponseEntity<TableMetadataDto> createTable(@RequestParam String label) {
        log.info("POST /api/schema/tables - Creating table with label: {}", label);
        TableMetadataDto created = tableMetadataService.createTable(label);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Create a table from CSV file upload
     */
    @PostMapping("/upload")
    public ResponseEntity<TableMetadataDto> createTableFromCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tableName") String tableName,
            @RequestParam(value = "columnTypes", required = false) String columnTypesJson) {
        log.info("POST /api/schema/tables/upload - Creating table from CSV: {}", tableName);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        List<String> columnTypes = null;
        try {
            if (columnTypesJson != null && !columnTypesJson.isBlank()) {
                columnTypes = objectMapper.readValue(columnTypesJson, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid columnTypes JSON; expected an array of strings");
        }

        TableMetadataDto created = tableMetadataService.createTableFromCsv(file, tableName, columnTypes);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Rename a table
     */
    @PutMapping("/{tableId}/rename")
    public ResponseEntity<TableMetadataDto> renameTable(
            @PathVariable Long tableId,
            @RequestParam String newLabel) {
        log.info("PUT /api/schema/tables/{}/rename - Renaming to: {}", tableId, newLabel);
        TableMetadataDto updated = tableMetadataService.renameTable(tableId, newLabel);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a table
     */
    @DeleteMapping("/{tableId}")
    public ResponseEntity<Void> deleteTable(@PathVariable Long tableId) {
        log.info("DELETE /api/schema/tables/{} - Deleting table", tableId);
        tableMetadataService.deleteTable(tableId);
        return ResponseEntity.noContent().build();
    }
}

