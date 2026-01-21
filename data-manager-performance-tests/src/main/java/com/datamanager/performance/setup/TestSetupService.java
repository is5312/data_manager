package com.datamanager.performance.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Service to set up the test environment:
 * - Create dmgr schema if not exists
 * - Create test table with required columns
 * - Return table ID for performance testing
 */
public class TestSetupService {

    private static final Logger log = LoggerFactory.getLogger(TestSetupService.class);

    private final DataSource dataSource;

    public TestSetupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Set up the complete test environment
     * @return table ID to use for performance testing
     */
    public Long setupTestEnvironment() {
        createDmgrSchema();
        ensureMetadataTablesExist();
        String tableLabel = "perf_test_" + System.currentTimeMillis();
        return createTestTable(tableLabel);
    }

    /**
     * Create dmgr schema if it doesn't exist
     */
    public void createDmgrSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            log.info("Creating dmgr schema if not exists...");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS dmgr");
            log.info("dmgr schema is ready");
            
        } catch (SQLException e) {
            log.error("Failed to create dmgr schema", e);
            throw new RuntimeException("Failed to create dmgr schema", e);
        }
    }

    /**
     * Ensure metadata tables exist in dmgr schema
     */
    private void ensureMetadataTablesExist() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            log.info("Ensuring metadata tables exist in dmgr schema...");
            
            // Create base_reference_table if not exists
            String createRefTableSql = """
                CREATE TABLE IF NOT EXISTS dmgr.base_reference_table (
                    id BIGSERIAL PRIMARY KEY,
                    tbl_label VARCHAR(255) NOT NULL,
                    tbl_link VARCHAR(255) NOT NULL UNIQUE,
                    description TEXT,
                    version_no INTEGER DEFAULT 1,
                    add_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    add_usr VARCHAR(255) DEFAULT 'system',
                    upd_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    upd_usr VARCHAR(255) DEFAULT 'system'
                )
                """;
            stmt.execute(createRefTableSql);
            
            // Create base_column_map if not exists
            String createColumnMapSql = """
                CREATE TABLE IF NOT EXISTS dmgr.base_column_map (
                    id BIGSERIAL PRIMARY KEY,
                    tbl_id BIGINT NOT NULL,
                    tbl_link VARCHAR(255) NOT NULL,
                    col_label VARCHAR(255) NOT NULL,
                    col_link VARCHAR(255) NOT NULL,
                    description TEXT,
                    version_no INTEGER DEFAULT 1,
                    add_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    add_usr VARCHAR(255) DEFAULT 'system',
                    upd_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    upd_usr VARCHAR(255) DEFAULT 'system',
                    CONSTRAINT fk_base_column_map_tbl_id 
                        FOREIGN KEY (tbl_id) REFERENCES dmgr.base_reference_table(id) ON DELETE CASCADE
                )
                """;
            stmt.execute(createColumnMapSql);
            
            log.info("Metadata tables are ready in dmgr schema");
            
        } catch (SQLException e) {
            log.error("Failed to create metadata tables", e);
            throw new RuntimeException("Failed to create metadata tables", e);
        }
    }

    /**
     * Create a test table with standard columns for performance testing
     * @param tableLabel logical name for the table
     * @return table ID from metadata
     */
    public Long createTestTable(String tableLabel) {
        String physicalTableName = "tbl_" + tableLabel.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            log.info("Creating test table: {} (physical: {})", tableLabel, physicalTableName);
            
            // Create physical table
            String createTableSql = String.format("""
                CREATE TABLE IF NOT EXISTS dmgr.%s (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255),
                    age INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    add_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    add_usr VARCHAR(255) DEFAULT 'system',
                    upd_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    upd_usr VARCHAR(255) DEFAULT 'system'
                )
                """, physicalTableName);
            stmt.execute(createTableSql);
            
            // Insert metadata into base_reference_table
            String insertMetadataSql = String.format("""
                INSERT INTO dmgr.base_reference_table 
                (tbl_label, tbl_link, description, add_usr, upd_usr)
                VALUES ('%s', '%s', 'Performance test table', 'perf_test', 'perf_test')
                ON CONFLICT (tbl_link) DO NOTHING
                RETURNING id
                """, tableLabel, physicalTableName);
            
            Long tableId = null;
            ResultSet rs = stmt.executeQuery(insertMetadataSql);
            if (rs.next()) {
                tableId = rs.getLong("id");
            } else {
                // Table already exists, get its ID
                String getIdSql = String.format(
                    "SELECT id FROM dmgr.base_reference_table WHERE tbl_link = '%s'", 
                    physicalTableName);
                rs = stmt.executeQuery(getIdSql);
                if (rs.next()) {
                    tableId = rs.getLong("id");
                }
            }
            
            if (tableId == null) {
                throw new RuntimeException("Failed to get table ID after creation");
            }
            
            // Insert column metadata
            insertColumnMetadata(stmt, tableId, physicalTableName);
            
            log.info("Test table created successfully with ID: {}", tableId);
            return tableId;
            
        } catch (SQLException e) {
            log.error("Failed to create test table", e);
            throw new RuntimeException("Failed to create test table", e);
        }
    }

    private void insertColumnMetadata(Statement stmt, Long tableId, String physicalTableName) throws SQLException {
        String[] columns = {
            "INSERT INTO dmgr.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Name', 'name', 'perf_test', 'perf_test') ON CONFLICT DO NOTHING",
            "INSERT INTO dmgr.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Email', 'email', 'perf_test', 'perf_test') ON CONFLICT DO NOTHING",
            "INSERT INTO dmgr.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Age', 'age', 'perf_test', 'perf_test') ON CONFLICT DO NOTHING",
            "INSERT INTO dmgr.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Created At', 'created_at', 'perf_test', 'perf_test') ON CONFLICT DO NOTHING"
        };
        
        for (String columnSql : columns) {
            stmt.execute(String.format(columnSql, tableId, physicalTableName));
        }
    }

    /**
     * Clean up test data (optional)
     * @param tableId table ID to clean up
     */
    public void cleanupTestData(Long tableId) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            log.info("Cleaning up test data for table ID: {}", tableId);
            
            // Get physical table name
            String getTableNameSql = String.format(
                "SELECT tbl_link FROM dmgr.base_reference_table WHERE id = %d", tableId);
            ResultSet rs = stmt.executeQuery(getTableNameSql);
            
            if (rs.next()) {
                String physicalTableName = rs.getString("tbl_link");
                
                // Drop physical table
                String dropTableSql = String.format("DROP TABLE IF EXISTS dmgr.%s CASCADE", physicalTableName);
                stmt.execute(dropTableSql);
                
                // Delete column metadata (should cascade automatically)
                String deleteColumnsSql = String.format(
                    "DELETE FROM dmgr.base_column_map WHERE tbl_id = %d", tableId);
                stmt.execute(deleteColumnsSql);
                
                // Delete table metadata
                String deleteTableSql = String.format(
                    "DELETE FROM dmgr.base_reference_table WHERE id = %d", tableId);
                stmt.execute(deleteTableSql);
                
                log.info("Successfully cleaned up test data");
            } else {
                log.warn("Table with ID {} not found for cleanup", tableId);
            }
            
        } catch (SQLException e) {
            log.error("Failed to clean up test data", e);
            // Don't throw exception during cleanup
        }
    }

    /**
     * Verify gRPC connection is available
     * @return true if connection is successful
     */
    public boolean verifyGrpcConnection() {
        // This will be implemented by the actual test to verify gRPC client connectivity
        log.info("gRPC connection verification should be done by the test harness");
        return true;
    }
}
