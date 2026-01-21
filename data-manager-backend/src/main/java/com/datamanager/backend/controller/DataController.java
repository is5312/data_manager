package com.datamanager.backend.controller;

import com.datamanager.backend.service.DataService;
import com.datamanager.backend.service.StreamingDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/**
 * REST Controller for table data operations
 * Delegates business logic to service layer
 */
@RestController
@RequestMapping("/api/data")
@Slf4j
public class DataController {

    private final DataService dataService;
    private final StreamingDataService streamingDataService;

    public DataController(DataService dataService, StreamingDataService streamingDataService) {
        this.dataService = dataService;
        this.streamingDataService = streamingDataService;
    }

    /**
     * Stream all rows from a table as CSV using PostgreSQL COPY command
     * This is much faster than fetching rows individually for large datasets
     */
    @GetMapping(value = "/tables/{tableId}/rows/stream", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> streamTableDataAsCsv(
            @PathVariable Long tableId,
            @RequestParam(required = false, defaultValue = "public") String schema) {
        log.info("GET /api/data/tables/{}/rows/stream - Streaming table data as CSV from schema: {}", tableId, schema);

        StreamingResponseBody stream = streamingDataService.streamTableDataAsCsv(tableId, schema);

        // Use tableId for filename (service handles table lookup and validation)
        String filename = "table_" + tableId + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(stream);
    }

    /**
     * Stream all rows from a table as Apache Arrow IPC format
     * Uses binary protocol for maximum efficiency - much faster than CSV or JSON
     * Frontend can load Arrow directly into DuckDB for instant querying
     */
    @GetMapping(value = "/tables/{tableId}/rows/arrow", produces = "application/vnd.apache.arrow.stream")
    public ResponseEntity<StreamingResponseBody> streamTableDataAsArrow(
            @PathVariable Long tableId,
            @RequestParam(required = false, defaultValue = "public") String schema) {
        log.info("GET /api/data/tables/{}/rows/arrow - Streaming table data as Arrow IPC from schema: {}", tableId, schema);

        // Get total row count for progress tracking (service handles validation)
        long totalRows = 0;
        try {
            totalRows = streamingDataService.getTableRowCount(tableId, schema);
        } catch (Exception e) {
            log.warn("Could not get row count, progress will not be available", e);
        }

        StreamingResponseBody stream = streamingDataService.streamTableDataAsArrow(tableId, schema);

        String filename = "table_" + tableId + ".arrow";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apache.arrow.stream"))
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .header("X-Total-Rows", String.valueOf(totalRows))
                .body(stream);
    }

    /**
     * Insert a new row into a table
     * Returns the complete inserted row including audit columns
     */
    @PostMapping("/tables/{tableId}/rows")
    public ResponseEntity<Map<String, Object>> insertRow(
            @PathVariable Long tableId,
            @RequestBody Map<String, Object> rowData) {
        log.info("POST /api/data/tables/{}/rows - Inserting row", tableId);

        Map<String, Object> response = dataService.insertRow(tableId, rowData);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update a row in a table
     * Returns the complete updated row including audit columns
     */
    @PutMapping("/tables/{tableId}/rows/{rowId}")
    public ResponseEntity<Map<String, Object>> updateRow(
            @PathVariable Long tableId,
            @PathVariable Long rowId,
            @RequestBody Map<String, Object> rowData) {
        log.info("PUT /api/data/tables/{}/rows/{} - Updating row", tableId, rowId);

        Map<String, Object> response = dataService.updateRow(tableId, rowId, rowData);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a row from a table
     */
    @DeleteMapping("/tables/{tableId}/rows/{rowId}")
    public ResponseEntity<Void> deleteRow(
            @PathVariable Long tableId,
            @PathVariable Long rowId) {
        log.info("DELETE /api/data/tables/{}/rows/{} - Deleting row", tableId, rowId);

        dataService.deleteRow(tableId, rowId);
        return ResponseEntity.noContent().build();
    }
}
