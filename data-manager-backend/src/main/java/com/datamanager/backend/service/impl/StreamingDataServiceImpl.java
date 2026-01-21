package com.datamanager.backend.service.impl;

import com.datamanager.backend.config.MigrationProperties;
import com.datamanager.backend.dao.SchemaDao;
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
import java.util.List;

/**
 * Service implementation for streaming data operations
 */
@Service
@Slf4j
public class StreamingDataServiceImpl implements StreamingDataService {

    private final BaseReferenceTableRepository tableRepository;
    private final SchemaDao schemaDao;
    private final MigrationProperties migrationProperties;
    private final DataSource dataSource;
    private final DataSource arrowDataSource;

    public StreamingDataServiceImpl(
            BaseReferenceTableRepository tableRepository,
            SchemaDao schemaDao,
            MigrationProperties migrationProperties,
            DataSource dataSource,
            @Qualifier("arrowDataSource") DataSource arrowDataSource) {
        this.tableRepository = tableRepository;
        this.schemaDao = schemaDao;
        this.migrationProperties = migrationProperties;
        this.dataSource = dataSource;
        this.arrowDataSource = arrowDataSource;
    }

    @Override
    public StreamingResponseBody streamTableDataAsCsv(Long tableId, String schemaName) {
        final String finalSchemaName = (schemaName == null || schemaName.isBlank()) ? "public" : schemaName;
        log.info("Streaming table data as CSV for table ID: {} from schema: {}", tableId, finalSchemaName);

        BaseReferenceTable table = getTableFromSchema(tableId, finalSchemaName);
        final String tableName = table.getTblLink();

        return outputStream -> {
            try (Connection conn = dataSource.getConnection()) {
                // Use PostgreSQL's COPY command for maximum performance
                CopyManager copyManager = new CopyManager(conn.unwrap(BaseConnection.class));

                // Quote schema and table name to handle mixed-case identifiers
                String quotedSchema = "\"" + finalSchemaName.replace("\"", "\"\"") + "\"";
                String quotedTable = "\"" + tableName.replace("\"", "\"\"") + "\"";
                String schemaQualifiedTable = quotedSchema + "." + quotedTable;

                // COPY command with CSV format (includes header row)
                String copySQL = String.format(
                        "COPY %s TO STDOUT WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',', QUOTE '\"', ESCAPE '\"')",
                        schemaQualifiedTable);

                log.info("Executing COPY command: {}", copySQL);
                long rowCount = copyManager.copyOut(copySQL, outputStream);
                log.info("Streamed {} rows for table {}.{}", rowCount, finalSchemaName, tableName);

            } catch (Exception e) {
                log.error("Error streaming table data as CSV", e);
                throw new RuntimeException("Failed to stream table data: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public StreamingResponseBody streamTableDataAsArrow(Long tableId, String schemaName) {
        final String finalSchemaName = (schemaName == null || schemaName.isBlank()) ? "public" : schemaName;
        log.info("Streaming table data as Arrow for table ID: {} from schema: {}", tableId, finalSchemaName);

        BaseReferenceTable table = getTableFromSchema(tableId, finalSchemaName);
        final String tableName = table.getTblLink();

        return outputStream -> {
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                // Use dedicated Arrow datasource with binary protocol
                conn = arrowDataSource.getConnection();
                conn.setAutoCommit(false); // Required for fetch size to work

                // Quote schema and table name to handle mixed-case identifiers
                String quotedSchema = "\"" + finalSchemaName.replace("\"", "\"\"") + "\"";
                String quotedTable = "\"" + tableName.replace("\"", "\"\"") + "\"";
                String schemaQualifiedTable = quotedSchema + "." + quotedTable;
                String sql = "SELECT * FROM " + schemaQualifiedTable;

                log.info("Executing query for Arrow streaming: {}", sql);

                stmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                stmt.setFetchSize(10000); // Stream in chunks

                rs = stmt.executeQuery();

                // Stream ResultSet as Arrow IPC format
                long rowCount = ArrowStreamingUtil.streamResultSetAsArrow(rs, outputStream);

                log.info("Streamed {} rows as Arrow for table {}.{}", rowCount, finalSchemaName, tableName);

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
    public long getTableRowCount(Long tableId, String schemaName) {
        final String finalSchemaName = (schemaName == null || schemaName.isBlank()) ? "public" : schemaName;
        log.debug("Getting row count for table ID: {} from schema: {}", tableId, finalSchemaName);

        BaseReferenceTable table = getTableFromSchema(tableId, finalSchemaName);

        // Quote schema and table name to handle mixed-case identifiers
        String quotedSchema = "\"" + finalSchemaName.replace("\"", "\"\"") + "\"";
        String quotedTable = "\"" + table.getTblLink().replace("\"", "\"\"") + "\"";
        String schemaQualifiedTable = quotedSchema + "." + quotedTable;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM " + schemaQualifiedTable)) {
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

    /**
     * Get table from the specified schema
     */
    private BaseReferenceTable getTableFromSchema(Long tableId, String schemaName) {
        List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schemaName);
        return tables.stream()
                .filter(t -> t.getId().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Table not found with ID: " + tableId + " in schema: " + schemaName));
    }
}

