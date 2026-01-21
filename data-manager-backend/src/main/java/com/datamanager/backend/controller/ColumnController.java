package com.datamanager.backend.controller;

import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.service.TableMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for column operations
 * Handles CRUD operations for table columns
 */
@RestController
@RequestMapping("/api/schema/tables/{tableId}/columns")
@Slf4j
public class ColumnController {

    private final TableMetadataService tableMetadataService;

    public ColumnController(TableMetadataService tableMetadataService) {
        this.tableMetadataService = tableMetadataService;
    }

    /**
     * Get all columns for a table
     */
    @GetMapping
    public ResponseEntity<List<ColumnMetadataDto>> getColumns(
            @PathVariable Long tableId,
            @RequestParam(required = false) String schema) {
        log.info("GET /api/schema/tables/{}/columns - Fetching columns from schema: {}", tableId, schema);
        List<ColumnMetadataDto> columns = tableMetadataService.getColumnsByTableId(tableId, schema);
        return ResponseEntity.ok(columns);
    }

    /**
     * Add a column to a table
     */
    @PostMapping
    public ResponseEntity<ColumnMetadataDto> addColumn(
            @PathVariable Long tableId,
            @RequestParam String label,
            @RequestParam String type) {
        log.info("POST /api/schema/tables/{}/columns - Adding column: {} ({})", tableId, label, type);
        ColumnMetadataDto created = tableMetadataService.addColumn(tableId, label, type);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Change an existing column's SQL type
     */
    @PutMapping("/{columnId}/type")
    public ResponseEntity<ColumnMetadataDto> changeColumnType(
            @PathVariable Long tableId,
            @PathVariable Long columnId,
            @RequestParam String type) {
        log.info("PUT /api/schema/tables/{}/columns/{}/type - Changing type to {}", tableId, columnId, type);
        ColumnMetadataDto updated = tableMetadataService.changeColumnType(tableId, columnId, type);
        return ResponseEntity.ok(updated);
    }

    /**
     * Remove a column from a table
     */
    @DeleteMapping("/{columnId}")
    public ResponseEntity<Void> removeColumn(
            @PathVariable Long tableId,
            @PathVariable Long columnId) {
        log.info("DELETE /api/schema/tables/{}/columns/{} - Removing column", tableId, columnId);
        tableMetadataService.removeColumn(tableId, columnId);
        return ResponseEntity.noContent().build();
    }
}

