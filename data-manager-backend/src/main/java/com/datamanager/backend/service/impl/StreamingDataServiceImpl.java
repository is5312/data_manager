package com.datamanager.backend.service.impl;

import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.StreamingDataService;
import com.datamanager.backend.util.ArrowStreamingUtil;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Service implementation for streaming data operations
 */
@Service
@Slf4j
public class StreamingDataServiceImpl implements StreamingDataService {

    private final BaseReferenceTableRepository tableRepository;
    private final DataSource dataSource;
    private final DataSource arrowDataSource;

    public StreamingDataServiceImpl(
            BaseReferenceTableRepository tableRepository,
            DataSource dataSource,
            @Qualifier("arrowDataSource") DataSource arrowDataSource) {
        this.tableRepository = tableRepository;
        this.dataSource = dataSource;
        this.arrowDataSource = arrowDataSource;
    }

    @Override
    public StreamingResponseBody streamTableDataAsCsv(Long tableId) {
        log.info("Streaming table data as CSV for table ID: {}", tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        final String tableName = table.getTblLink();

        return outputStream -> {
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
                log.info("Streamed {} rows for table {}", rowCount, tableName);

            } catch (Exception e) {
                log.error("Error streaming table data as CSV", e);
                throw new RuntimeException("Failed to stream table data: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public StreamingResponseBody streamTableDataAsArrow(Long tableId) {
        log.info("Streaming table data as Arrow for table ID: {}", tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        final String tableName = table.getTblLink();

        return outputStream -> {
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                // Use dedicated Arrow datasource with binary protocol
                conn = arrowDataSource.getConnection();
                conn.setAutoCommit(false); // Required for fetch size to work

                // Quote table name to handle mixed-case identifiers
                String quotedTableName = "\"" + tableName.replace("\"", "\"\"") + "\"";
                String sql = "SELECT * FROM " + quotedTableName;

                log.info("Executing query for Arrow streaming: {}", sql);

                stmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                stmt.setFetchSize(10000); // Stream in chunks

                rs = stmt.executeQuery();

                // Stream ResultSet as Arrow IPC format
                long rowCount = ArrowStreamingUtil.streamResultSetAsArrow(rs, outputStream);

                log.info("Streamed {} rows as Arrow for table {}", rowCount, tableName);

            } catch (Exception e) {
                log.error("Error streaming table data as Arrow", e);
                throw new RuntimeException("Failed to stream table data as Arrow: " + e.getMessage(), e);
            } finally {
                // Clean up JDBC resources
                try {
                    if (rs != null) rs.close();
                    if (stmt != null) stmt.close();
                    if (conn != null) {
                        conn.rollback(); // Clean up transaction
                        conn.close();
                    }
                } catch (Exception e) {
                    log.warn("Error closing JDBC resources", e);
                }
            }
        };
    }

    @Override
    public long getTableRowCount(Long tableId) {
        log.debug("Getting row count for table ID: {}", tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM \"" + table.getTblLink().replace("\"", "\"\"") + "\"")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (Exception e) {
            log.warn("Could not get row count for table {}", tableId, e);
            throw new RuntimeException("Failed to get row count: " + e.getMessage(), e);
        }
    }
}

