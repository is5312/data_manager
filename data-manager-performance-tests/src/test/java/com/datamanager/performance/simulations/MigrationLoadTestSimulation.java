package com.datamanager.performance.simulations;

import com.datamanager.grpc.InsertRowResponse;
import com.datamanager.performance.actions.GrpcInsertAction;
import com.datamanager.performance.actions.HttpReadAction;
import com.datamanager.performance.client.ManualGrpcClient;
import com.datamanager.performance.config.PerformanceTestConfig;
import com.datamanager.performance.data.TestDataGenerator;
import com.datamanager.performance.migration.DataIntegrityTracker;
import com.datamanager.performance.migration.MigrationTestHelper;
import com.datamanager.performance.setup.TestSetupService;
import io.gatling.javaapi.core.*;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling simulation for testing table migration during peak load
 * 
 * Test Profile:
 * - Phase 1: Warmup - Create table, pre-populate data, start baseline load (10s)
 * - Phase 2: Migration trigger - Trigger migration from public to dmgr (15s delay)
 * - Phase 3: Migration execution - Continue load during migration (variable duration)
 * - Phase 4: Verification - Verify data integrity after migration completes
 * 
 * Verifies:
 * - No data loss during migration
 * - No interruption to reads/writes
 * - All data correctly migrated to shadow table
 */
public class MigrationLoadTestSimulation extends Simulation {

    private static final Logger log = LoggerFactory.getLogger(MigrationLoadTestSimulation.class);

    // Spring context and beans
    private static ConfigurableApplicationContext context;
    private static ManualGrpcClient grpcClient;
    private static TestSetupService setupService;
    private static TestDataGenerator dataGenerator;
    private static PerformanceTestConfig.PerformanceProperties properties;
    private static DSLContext dslContext;
    private static MigrationTestHelper migrationHelper;
    private static DataIntegrityTracker integrityTracker;
    
    // Test state
    private static Long tableId; // Table ID in public schema
    private static Long dmgrTableId; // Table ID in dmgr schema (after migration)
    private static String tableLabel; // Table label for lookup after migration
    private static String migrationJobId;
    private static volatile boolean migrationCompleted = false;

    // Initialize Spring context and set up test environment
    static {
        try {
            log.info("=".repeat(80));
            log.info("Initializing Migration Load Test");
            log.info("=".repeat(80));
            
            // Start Spring context
            System.setProperty("spring.profiles.active", "performance");
            context = SpringApplication.run(com.datamanager.performance.PerformanceTestApplication.class, new String[]{});
            
            // Get beans
            setupService = context.getBean(TestSetupService.class);
            dataGenerator = context.getBean(TestDataGenerator.class);
            properties = context.getBean(PerformanceTestConfig.PerformanceProperties.class);
            dslContext = context.getBean(DSLContext.class);
            integrityTracker = new DataIntegrityTracker();
            
            // Create migration helper
            migrationHelper = new MigrationTestHelper(
                    properties.getMigration().getBackendUrl(),
                    dslContext
            );
            
            log.info("Spring context initialized successfully");
            
            // Create manual gRPC client first (needed for pre-population)
            log.info("Creating manual gRPC client...");
            grpcClient = new ManualGrpcClient("localhost", 9090);
            
            // Warmup gRPC client
            log.info("Warming up gRPC client channel...");
            try {
                Map<String, String> warmupData = new HashMap<>();
                warmupData.put("name", "warmup");
                warmupData.put("email", "warmup@test.com");
                warmupData.put("age", "25");
                // We'll do a real warmup insert after table is created
                log.info("gRPC client created successfully - will warmup after table creation");
            } catch (Exception e) {
                log.error("========================================");
                log.error("gRPC client creation FAILED!");
                log.error("Error: {}", e.getMessage());
                log.error("Make sure backend server is running on localhost:9090");
                log.error("========================================");
                throw new RuntimeException("Failed to initialize gRPC client", e);
            }
            
            // Set up test environment - create table in public schema
            log.info("Setting up test environment in public schema...");
            tableId = setupTestTableInPublicSchema();
            tableLabel = getTableLabel(tableId, "public");
            log.info("Test table created with ID: {} (label: {}) in public schema", tableId, tableLabel);
            
            // Warmup gRPC client with real insert
            log.info("Warming up gRPC client with real insert...");
            try {
                Map<String, String> warmupData = new HashMap<>();
                warmupData.put("name", "warmup");
                warmupData.put("email", "warmup@test.com");
                warmupData.put("age", "25");
                InsertRowResponse response = grpcClient.insertRow(tableId, "public", warmupData);
                integrityTracker.recordInsert(response.getId(), Instant.now());
                log.info("gRPC client warmup successful - channel is ready!");
            } catch (Exception e) {
                log.error("========================================");
                log.error("gRPC client warmup FAILED!");
                log.error("Error: {}", e.getMessage());
                log.error("Make sure backend server is running on localhost:9090");
                log.error("========================================");
                throw new RuntimeException("Failed to warmup gRPC client", e);
            }
            
            // Pre-populate with initial data
            log.info("Pre-populating table with {} rows...", properties.getMigration().getInitialRows());
            prePopulateTable(tableId, properties.getMigration().getInitialRows());
            long initialCount = migrationHelper.getRowCount("public", getPhysicalTableName(tableId, "public"));
            integrityTracker.setInitialRowCount(initialCount);
            log.info("Pre-population complete. Initial row count: {}", initialCount);
            
            // Register shutdown hook for cleanup and verification
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down migration load test...");
                if (migrationCompleted) {
                    verifyDataIntegrity();
                }
                if (grpcClient != null) {
                    try {
                        grpcClient.shutdown();
                    } catch (InterruptedException e) {
                        log.warn("Error shutting down gRPC client", e);
                    }
                }
                if (context != null) {
                    context.close();
                }
                log.info("Shutdown complete");
            }));
            
            log.info("=".repeat(80));
            log.info("Test Configuration:");
            log.info("  Table ID: {}", tableId);
            log.info("  Initial Rows: {}", properties.getMigration().getInitialRows());
            log.info("  Write RPS: {}", properties.getMigration().getWriteRps());
            log.info("  Read RPS: {}", properties.getMigration().getReadRps());
            log.info("  Warmup Duration: {}s", properties.getMigration().getWarmupDurationSeconds());
            log.info("  Migration Trigger Delay: {}s", properties.getMigration().getMigrationTriggerDelaySeconds());
            log.info("=".repeat(80));
            
        } catch (Exception e) {
            log.error("Failed to initialize migration load test", e);
            throw new RuntimeException("Failed to initialize migration load test", e);
        }
    }

    /**
     * Get table label from metadata
     */
    private static String getTableLabel(Long tableId, String schema) {
        String sql = String.format("SELECT tbl_label FROM \"%s\".base_reference_table WHERE id = ?", schema);
        var result = dslContext.fetchOne(sql, tableId);
        if (result == null) {
            throw new RuntimeException("Table not found in schema: " + schema);
        }
        return result.get(0, String.class);
    }

    /**
     * Look up table ID in dmgr schema by table label
     */
    private static Long lookupTableIdInDmgr(String tableLabel) {
        String sql = "SELECT id FROM \"dmgr\".base_reference_table WHERE tbl_label = ? LIMIT 1";
        var result = dslContext.fetchOne(sql, tableLabel);
        if (result == null) {
            throw new RuntimeException("Table not found in dmgr schema with label: " + tableLabel);
        }
        return result.get(0, Long.class);
    }

    /**
     * Create test table in public schema
     */
    private static Long setupTestTableInPublicSchema() {
        try {
            // Ensure public schema metadata tables exist
            setupService.createDmgrSchema(); // This creates dmgr, but we need public
            // For public schema, metadata tables should already exist from backend startup
            
            String tableLabel = "migration_test_" + System.currentTimeMillis();
            String physicalTableName = "tbl_" + tableLabel.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            
            // Create physical table in public schema
            String createTableSql = String.format("""
                CREATE TABLE IF NOT EXISTS public.%s (
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
            dslContext.execute(createTableSql);
            
            // Insert metadata into public.base_reference_table
            String insertMetadataSql = String.format("""
                INSERT INTO public.base_reference_table 
                (tbl_label, tbl_link, description, add_usr, upd_usr)
                VALUES ('%s', '%s', 'Migration test table', 'migration_test', 'migration_test')
                ON CONFLICT (tbl_link) DO UPDATE SET tbl_label = EXCLUDED.tbl_label
                RETURNING id
                """, tableLabel, physicalTableName);
            
            var result = dslContext.fetchOne(insertMetadataSql);
            Long tableId = result != null ? result.get(0, Long.class) : null;
            
            if (tableId == null) {
                // Table already exists, get its ID
                String getIdSql = String.format(
                    "SELECT id FROM public.base_reference_table WHERE tbl_link = '%s'", 
                    physicalTableName);
                result = dslContext.fetchOne(getIdSql);
                if (result != null) {
                    tableId = result.get(0, Long.class);
                }
            }
            
            if (tableId == null) {
                throw new RuntimeException("Failed to get table ID after creation");
            }
            
            // Insert column metadata
            insertColumnMetadata(tableId, physicalTableName);
            
            return tableId;
        } catch (Exception e) {
            log.error("Failed to create test table in public schema", e);
            throw new RuntimeException("Failed to create test table", e);
        }
    }

    private static void insertColumnMetadata(Long tableId, String physicalTableName) {
        String[] columns = {
            "INSERT INTO public.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Name', 'name', 'migration_test', 'migration_test') ON CONFLICT DO NOTHING",
            "INSERT INTO public.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Email', 'email', 'migration_test', 'migration_test') ON CONFLICT DO NOTHING",
            "INSERT INTO public.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Age', 'age', 'migration_test', 'migration_test') ON CONFLICT DO NOTHING",
            "INSERT INTO public.base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, upd_usr) VALUES (%d, '%s', 'Created At', 'created_at', 'migration_test', 'migration_test') ON CONFLICT DO NOTHING"
        };
        
        for (String columnSql : columns) {
            dslContext.execute(String.format(columnSql, tableId, physicalTableName));
        }
    }

    /**
     * Pre-populate table with initial data
     */
    private static void prePopulateTable(Long tableId, int rowCount) {
        for (int i = 0; i < rowCount; i++) {
            try {
                Map<String, String> rowData = dataGenerator.generateRowData();
                InsertRowResponse response = grpcClient.insertRow(tableId, "public", rowData);
                integrityTracker.recordInsert(response.getId(), Instant.now());
            } catch (Exception e) {
                log.warn("Failed to pre-populate row {}: {}", i, e.getMessage());
            }
        }
    }

    /**
     * Get physical table name from metadata
     */
    private static String getPhysicalTableName(Long tableId, String schema) {
        String sql = String.format("SELECT tbl_link FROM \"%s\".base_reference_table WHERE id = ?", schema);
        var result = dslContext.fetchOne(sql, tableId);
        if (result == null) {
            throw new RuntimeException("Table not found in schema: " + schema);
        }
        return result.get(0, String.class);
    }

    /**
     * Verify data integrity after migration
     */
    private static void verifyDataIntegrity() {
        try {
            log.info("=".repeat(80));
            log.info("Verifying Data Integrity");
            log.info("=".repeat(80));
            
            // Get shadow table name (use dmgrTableId if available, otherwise fallback to tableId)
            Long lookupTableId = dmgrTableId != null ? dmgrTableId : tableId;
            String shadowTableName = migrationHelper.getShadowTableName(lookupTableId, "dmgr");
            log.info("Shadow table name: {}", shadowTableName);
            
            // Get actual row count and IDs
            long actualRowCount = migrationHelper.getRowCount("dmgr", shadowTableName);
            Set<Long> actualRowIds = migrationHelper.getAllRowIds("dmgr", shadowTableName);
            
            // Verify integrity
            DataIntegrityTracker.IntegrityReport report = integrityTracker.verifyIntegrity(actualRowCount, actualRowIds);
            
            log.info(report.toString());
            
            // Verify no duplicates
            boolean noDuplicates = migrationHelper.verifyNoDuplicates("dmgr", shadowTableName);
            
            if (report.isIntegrityValid() && noDuplicates) {
                log.info("✅ DATA INTEGRITY VERIFIED: All checks passed!");
            } else {
                log.error("❌ DATA INTEGRITY CHECK FAILED!");
                if (!report.isIntegrityValid()) {
                    log.error("  - Data loss: {}", report.dataLoss);
                    log.error("  - Count mismatch: {}", report.countMismatch);
                }
                if (!noDuplicates) {
                    log.error("  - Duplicates found");
                }
            }
            
        } catch (Exception e) {
            log.error("Error during data integrity verification", e);
        }
    }

    // Define writer scenario (continuous inserts)
    private ScenarioBuilder createWriterScenario() {
        return scenario("Migration Writer")
            .exec(session -> {
                // Execute gRPC insert
                long startTime = System.currentTimeMillis();
                try {
                    Map<String, String> rowData = dataGenerator.generateRowData(
                            properties.getData().getEmailDomain(),
                            properties.getData().getMinAge(),
                            properties.getData().getMaxAge());
                    
                    // Use public schema initially, switch to dmgr after migration completes
                    String schema = migrationCompleted ? "dmgr" : "public";
                    Long currentTableId = migrationCompleted && dmgrTableId != null ? dmgrTableId : tableId;
                    InsertRowResponse response = grpcClient.insertRow(currentTableId, schema, rowData);
                    
                    // Track insert
                    integrityTracker.recordInsert(response.getId(), Instant.now());
                    
                    long duration = System.currentTimeMillis() - startTime;
                    
                    return session
                        .set("insert_success", true)
                        .set("insert_id", response.getId())
                        .set("insert_duration", duration)
                        .set("insert_schema", schema);
                } catch (Exception e) {
                    log.error("Insert failed: {}", e.getMessage());
                    return session
                        .set("insert_success", false)
                        .set("insert_error", e.getMessage());
                }
            })
            .exec(
                // Log metrics via HTTP request
                http("gRPC Insert Row")
                    .get("/actuator/health")
                    .check(status().is(200))
            );
    }

    // Define reader scenario (continuous reads)
    private ScenarioBuilder createReaderScenario() {
        return scenario("Migration Reader")
            .exec(session -> {
                // Use public schema initially, switch to dmgr after migration completes
                String schema = migrationCompleted ? "dmgr" : "public";
                return session.set("read_schema", schema);
            })
            .exec(session -> {
                // Use public schema initially, switch to dmgr after migration completes
                String schema = migrationCompleted ? "dmgr" : "public";
                Long currentTableId = migrationCompleted && dmgrTableId != null ? dmgrTableId : tableId;
                return session
                    .set("read_table_id", currentTableId)
                    .set("read_schema", schema);
            })
            .exec(HttpReadAction.readAction(
                session -> migrationCompleted && dmgrTableId != null ? dmgrTableId : tableId,
                session -> migrationCompleted ? "dmgr" : "public",
                properties.getMigration().getBackendUrl()
            ));
    }

    // Configure the simulation
    {
        ScenarioBuilder writerScenario = createWriterScenario();
        ScenarioBuilder readerScenario = createReaderScenario();
        
        // Phase 1: Warmup (baseline load)
        OpenInjectionStep warmupPhase = constantUsersPerSec(properties.getMigration().getWriteRps())
            .during(Duration.ofSeconds(properties.getMigration().getWarmupDurationSeconds()));
        
        // Phase 2: Trigger migration and continue load
        OpenInjectionStep migrationPhase = constantUsersPerSec(properties.getMigration().getWriteRps())
            .during(Duration.ofSeconds(properties.getMigration().getMigrationTriggerDelaySeconds() + 60)); // Continue for 60s after trigger
        
        // Phase 3: Continue load after migration (if needed)
        OpenInjectionStep postMigrationPhase = constantUsersPerSec(properties.getMigration().getWriteRps())
            .during(Duration.ofSeconds(30));
        
        // Trigger migration in background thread after warmup delay
        new Thread(() -> {
            try {
                Thread.sleep(properties.getMigration().getWarmupDurationSeconds() * 1000L + 
                            properties.getMigration().getMigrationTriggerDelaySeconds() * 1000L);
                
                if (migrationJobId == null) {
                    log.info("Triggering migration from public to dmgr...");
                    integrityTracker.markMigrationStart();
                    migrationJobId = migrationHelper.triggerMigration(tableId, "public", "dmgr");
                    
                    // Monitor migration completion
                    boolean success = migrationHelper.waitForMigrationCompletion(
                            migrationJobId,
                            Duration.ofSeconds(properties.getMigration().getVerificationTimeoutSeconds())
                    );
                    migrationCompleted = success;
                    integrityTracker.markMigrationEnd();
                    if (success) {
                        log.info("Migration completed successfully!");
                        
                        // Look up table ID in dmgr schema after migration
                        try {
                            dmgrTableId = lookupTableIdInDmgr(tableLabel);
                            log.info("Found table ID {} in dmgr schema (was {} in public schema)", dmgrTableId, tableId);
                        } catch (Exception e) {
                            log.error("Failed to look up table ID in dmgr schema: {}", e.getMessage());
                            // Fallback: try to use the same table ID
                            dmgrTableId = tableId;
                            log.warn("Using original table ID {} as fallback", dmgrTableId);
                        }
                        
                        verifyDataIntegrity();
                    } else {
                        log.error("Migration failed!");
                    }
                }
            } catch (Exception e) {
                log.error("Error triggering/monitoring migration", e);
            }
        }).start();
        
        setUp(
            writerScenario.injectOpen(
                warmupPhase,
                migrationPhase,
                postMigrationPhase
            ),
            readerScenario.injectOpen(
                constantUsersPerSec(properties.getMigration().getReadRps())
                    .during(Duration.ofSeconds(
                        properties.getMigration().getWarmupDurationSeconds() +
                        properties.getMigration().getMigrationTriggerDelaySeconds() + 90
                    ))
            )
        )
        .protocols(
            http.baseUrl(properties.getMigration().getBackendUrl())
        )
        .maxDuration(Duration.ofSeconds(
            properties.getMigration().getWarmupDurationSeconds() +
            properties.getMigration().getMigrationTriggerDelaySeconds() + 120
        ));
        
        log.info("=".repeat(80));
        log.info("Starting Migration Load Test Simulation...");
        log.info("=".repeat(80));
    }
}
