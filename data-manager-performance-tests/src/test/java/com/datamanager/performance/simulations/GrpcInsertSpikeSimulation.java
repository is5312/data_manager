package com.datamanager.performance.simulations;

import com.datamanager.performance.PerformanceTestApplication;
import com.datamanager.performance.actions.GrpcInsertAction;
import com.datamanager.performance.client.ManualGrpcClient;
import com.datamanager.performance.config.PerformanceTestConfig;
import com.datamanager.performance.data.TestDataGenerator;
import com.datamanager.performance.setup.TestSetupService;
import io.gatling.javaapi.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling simulation for gRPC insert spike testing
 * 
 * Test Profile:
 * - Phase 1: Normal load (10 req/s) for 5 seconds
 * - Phase 2: SPIKE (500 req/s) for 20 seconds
 * - Phase 3: Recovery to normal load (10 req/s) for 5 seconds
 * 
 * Total duration: 30 seconds
 * Total requests: ~10,000+
 */
public class GrpcInsertSpikeSimulation extends Simulation {

    private static final Logger log = LoggerFactory.getLogger(GrpcInsertSpikeSimulation.class);

    // Spring context and beans
    private static ConfigurableApplicationContext context;
    private static ManualGrpcClient grpcClient;
    private static TestSetupService setupService;
    private static TestDataGenerator dataGenerator;
    private static PerformanceTestConfig.PerformanceProperties properties;
    private static Long tableId;
    private static volatile int failureCount = 0;
    private static volatile int successCount = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 10;

    // Initialize Spring context and set up test environment
    static {
        try {
            log.info("=".repeat(80));
            log.info("Initializing gRPC Insert Spike Performance Test");
            log.info("=".repeat(80));
            
            // Start Spring context
            System.setProperty("spring.profiles.active", "performance");
            context = SpringApplication.run(PerformanceTestApplication.class, new String[]{});
            
            // Get beans
            setupService = context.getBean(TestSetupService.class);
            dataGenerator = context.getBean(TestDataGenerator.class);
            properties = context.getBean(PerformanceTestConfig.PerformanceProperties.class);
            
            log.info("Spring context initialized successfully");
            
            // Set up test environment
            log.info("Setting up test environment...");
            tableId = setupService.setupTestEnvironment();
            log.info("Test table created with ID: {} in schema: {}", tableId, properties.getSchema());
            
            // Create manual gRPC client (bypasses Spring injection issues)
            log.info("Creating manual gRPC client...");
            grpcClient = new ManualGrpcClient("localhost", 9090);
            
            // Warmup gRPC client - send a test request
            log.info("Warming up gRPC client channel...");
            try {
                Map<String, String> warmupData = new HashMap<>();
                warmupData.put("name", "warmup");
                warmupData.put("email", "warmup@test.com");
                warmupData.put("age", "25");
                grpcClient.insertRow(tableId, properties.getSchema(), warmupData);
                log.info("gRPC client warmup successful - channel is ready!");
            } catch (Exception e) {
                log.error("========================================");
                log.error("gRPC client warmup FAILED!");
                log.error("Error: {}", e.getMessage());
                log.error("Make sure backend server is running on localhost:9090");
                log.error("========================================");
                throw new RuntimeException("Failed to initialize gRPC client", e);
            }
            
            // Register shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down performance test...");
                if (grpcClient != null) {
                    try {
                        grpcClient.shutdown();
                    } catch (InterruptedException e) {
                        log.warn("Error shutting down gRPC client", e);
                    }
                }
                if (properties.getCleanup().isDeleteTableAfterTest() && tableId != null) {
                    log.info("Cleaning up test table...");
                    setupService.cleanupTestData(tableId);
                }
                if (context != null) {
                    context.close();
                }
                log.info("Shutdown complete");
            }));
            
            log.info("=".repeat(80));
            log.info("Test Configuration:");
            log.info("  Schema: {}", properties.getSchema());
            log.info("  Table ID: {}", tableId);
            log.info("  Normal RPS: {}", properties.getSpike().getNormalRps());
            log.info("  Spike RPS: {}", properties.getSpike().getSpikeRps());
            log.info("  Normal Duration: {}s", properties.getSpike().getNormalDurationSeconds());
            log.info("  Spike Duration: {}s", properties.getSpike().getSpikeDurationSeconds());
            log.info("=".repeat(80));
            
        } catch (Exception e) {
            log.error("Failed to initialize performance test", e);
            throw new RuntimeException("Failed to initialize performance test", e);
        }
    }

    // Define the test scenario
    private ScenarioBuilder createScenario() {
        return scenario("gRPC Insert Spike Test")
            .exec(session -> {
                // Log start of user simulation
                if (log.isDebugEnabled()) {
                    log.debug("Starting user simulation");
                }
                return session;
            })
            .exec(GrpcInsertAction.insertAction(
                grpcClient,
                tableId,
                properties.getSchema(),
                dataGenerator,
                properties.getData().getEmailDomain(),
                properties.getData().getMinAge(),
                properties.getData().getMaxAge()
            ))
            .exec(session -> {
                // Check if insert was successful
                Boolean success = session.getBoolean("grpc_insert_success");
                if (success != null && success) {
                    successCount++;
                    failureCount = 0; // Reset consecutive failure counter on success
                } else {
                    String error = session.getString("grpc_insert_error");
                    failureCount++;
                    
                    if (log.isWarnEnabled()) {
                        log.warn("Insert failed ({}): {}", failureCount, error);
                    }
                    
                    // Stop simulation if too many consecutive failures
                    if (failureCount >= MAX_CONSECUTIVE_FAILURES) {
                        log.error("========================================");
                        log.error("STOPPING TEST: {} consecutive failures detected", failureCount);
                        log.error("Last error: {}", error);
                        log.error("========================================");
                        System.exit(1); // Force stop the test
                    }
                }
                return session;
            });
    }

    // Configure the simulation
    {
        ScenarioBuilder scenario = createScenario();
        
        OpenInjectionStep phase1 = constantUsersPerSec(properties.getSpike().getNormalRps())
            .during(Duration.ofSeconds(properties.getSpike().getNormalDurationSeconds()));
        
        OpenInjectionStep phase2 = constantUsersPerSec(properties.getSpike().getSpikeRps())
            .during(Duration.ofSeconds(properties.getSpike().getSpikeDurationSeconds()));
        
        OpenInjectionStep phase3 = constantUsersPerSec(properties.getSpike().getNormalRps())
            .during(Duration.ofSeconds(properties.getSpike().getNormalDurationSeconds()));
        
        setUp(
            scenario.injectOpen(
                phase1,  // Normal load
                phase2,  // SPIKE
                phase3   // Recovery
            )
        )
        .protocols(
            // HTTP protocol for logging metrics (points to Spring Boot HTTP server, not gRPC)
            http.baseUrl("http://localhost:8080")
        )
        .assertions(
            // Disabled assertions to allow test to complete even with failures
            // We'll use manual failure detection instead
        )
        .maxDuration(Duration.ofSeconds(40));  // Safety timeout - slightly longer than test duration
        
        // Log test start
        log.info("=".repeat(80));
        log.info("Starting Gatling simulation...");
        log.info("Expected total requests: ~{}", calculateExpectedRequests());
        log.info("=".repeat(80));
    }

    private int calculateExpectedRequests() {
        int normalDuration = properties.getSpike().getNormalDurationSeconds();
        int spikeDuration = properties.getSpike().getSpikeDurationSeconds();
        int normalRps = properties.getSpike().getNormalRps();
        int spikeRps = properties.getSpike().getSpikeRps();
        
        return (normalDuration * normalRps * 2) + (spikeDuration * spikeRps);
    }

    // Main method for running the simulation
    public static void main(String[] args) {
        // This allows running the simulation directly via: java -cp ... GrpcInsertSpikeSimulation
        io.gatling.app.Gatling.main(new String[]{
            "--simulation", GrpcInsertSpikeSimulation.class.getName(),
            "--results-folder", "build/gatling-results",
            "--run-description", "gRPC Insert Spike Test"
        });
    }
}
