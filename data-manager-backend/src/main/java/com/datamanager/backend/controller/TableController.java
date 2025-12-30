package com.datamanager.backend.controller;

import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.service.TableMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for table operations
 * Handles CRUD operations for tables
 * Note: CSV uploads are handled by BatchController using batch processing
 */
@RestController
@RequestMapping("/api/schema/tables")
@Slf4j
public class TableController {

    private final TableMetadataService tableMetadataService;

    public TableController(TableMetadataService tableMetadataService) {
        this.tableMetadataService = tableMetadataService;
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

