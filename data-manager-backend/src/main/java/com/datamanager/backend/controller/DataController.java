package com.datamanager.backend.controller;

import com.datamanager.backend.dao.DataDao;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.util.ArrowStreamingUtil;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

/**
 * REST Controller for table data operations
 */
@RestController
@RequestMapping("/api/data")
@Slf4j
public class DataController {

    private final DataDao dataDao;
    private final BaseReferenceTableRepository tableRepository;
    private final DataSource dataSource;
    private final DataSource arrowDataSource;

    public DataController(
            DataDao dataDao,
            BaseReferenceTableRepository tableRepository,
            DataSource dataSource,
            @Qualifier("arrowDataSource") DataSource arrowDataSource) {
        this.dataDao = dataDao;
        this.tableRepository = tableRepository;
        this.dataSource = dataSource;
        this.arrowDataSource = arrowDataSource;
    }

    /**
     * Stream all rows from a table as CSV using PostgreSQL COPY command
     * This is much faster than fetching rows individually for large datasets
     */
    @GetMapping(value = "/tables/{tableId}/rows/stream", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> streamTableDataAsCsv(@PathVariable Long tableId) {
        log.info("GET /api/data/tables/{}/rows/stream - Streaming table data as CSV", tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        StreamingResponseBody stream = outputStream -> {
            try (Connection conn = dataSource.getConnection()) {
                // Use PostgreSQL's COPY command for maximum performance
                CopyManager copyManager = new CopyManager(conn.unwrap(BaseConnection.class));

                // Quote table name to handle mixed-case identifiers
                String quotedTableName = "\"" + table.getTblLink().replace("\"", "\"\"") + "\"";

                // COPY command with CSV format (includes header row)
                String copySQL = String.format(
                        "COPY %s TO STDOUT WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',', QUOTE '\"', ESCAPE '\"')",
                        quotedTableName);

                log.info("Executing COPY command: {}", copySQL);
                long rowCount = copyManager.copyOut(copySQL, outputStream);
                log.info("Streamed {} rows for table {}", rowCount, table.getTblLink());

            } catch (Exception e) {
                log.error("Error streaming table data", e);
                throw new RuntimeException("Failed to stream table data: " + e.getMessage(), e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"" + table.getTblLink() + ".csv\"")
                .body(stream);
    }

    /**
     * Stream all rows from a table as Apache Arrow IPC format
     * Uses binary protocol for maximum efficiency - much faster than CSV or JSON
     * Frontend can load Arrow directly into DuckDB for instant querying
     */
    @GetMapping(value = "/tables/{tableId}/rows/arrow", produces = "application/vnd.apache.arrow.stream")
    public ResponseEntity<StreamingResponseBody> streamTableDataAsArrow(@PathVariable Long tableId) {
        log.info("GET /api/data/tables/{}/rows/arrow - Streaming table data as Arrow IPC", tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        // Get total row count first for progress tracking
        long totalRows = 0;
        try (Connection countConn = dataSource.getConnection();
                PreparedStatement countStmt = countConn.prepareStatement(
                        "SELECT COUNT(*) FROM \"" + table.getTblLink().replace("\"", "\"\"") + "\"")) {
            ResultSet countRs = countStmt.executeQuery();
            if (countRs.next()) {
                totalRows = countRs.getLong(1);
            }
            log.info("Total rows for table {}: {}", table.getTblLink(), totalRows);
        } catch (Exception e) {
            log.warn("Could not get row count, progress will not be available", e);
        }

        final long finalTotalRows = totalRows;

        StreamingResponseBody stream = outputStream -> {
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                // Use dedicated Arrow datasource with binary protocol
                conn = arrowDataSource.getConnection();
                conn.setAutoCommit(false); // Required for fetch size to work

                // Quote table name to handle mixed-case identifiers
                String quotedTableName = "\"" + table.getTblLink().replace("\"", "\"\"") + "\"";
                String sql = "SELECT * FROM " + quotedTableName;

                log.info("Executing query for Arrow streaming: {}", sql);

                stmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                stmt.setFetchSize(10000); // Stream in chunks

                rs = stmt.executeQuery();

                // Stream ResultSet as Arrow IPC format
                long rowCount = ArrowStreamingUtil.streamResultSetAsArrow(rs, outputStream);

                log.info("Streamed {} rows as Arrow for table {}", rowCount, table.getTblLink());

            } catch (Exception e) {
                log.error("Error streaming table data as Arrow", e);
                throw new RuntimeException("Failed to stream table data as Arrow: " + e.getMessage(), e);
            } finally {
                // Clean up JDBC resources
                try {
                    if (rs != null)
                        rs.close();
                    if (stmt != null)
                        stmt.close();
                    if (conn != null) {
                        conn.rollback(); // Clean up transaction
                        conn.close();
                    }
                } catch (Exception e) {
                    log.warn("Error closing JDBC resources", e);
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apache.arrow.stream"))
                .header("Content-Disposition", "inline; filename=\"" + table.getTblLink() + ".arrow\"")
                .header("X-Total-Rows", String.valueOf(finalTotalRows))
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

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        Map<String, Object> auditData = dataDao.insertRow(table.getTblLink(), rowData);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", auditData.get("id"));
        response.put("message", "Row inserted successfully");
        response.put("add_usr", auditData.get("add_usr"));
        response.put("add_ts", auditData.get("add_ts"));
        response.put("upd_usr", auditData.get("upd_usr"));
        response.put("upd_ts", auditData.get("upd_ts"));

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

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        Map<String, Object> auditData = dataDao.updateRow(table.getTblLink(), rowId, rowData);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Row updated successfully");
        response.put("add_usr", auditData.get("add_usr"));
        response.put("add_ts", auditData.get("add_ts"));
        response.put("upd_usr", auditData.get("upd_usr"));
        response.put("upd_ts", auditData.get("upd_ts"));

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

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        dataDao.deleteRow(table.getTblLink(), rowId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Exception handler for IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /**
     * Exception handler for general exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An error occurred: " + e.getMessage());
    }
}
