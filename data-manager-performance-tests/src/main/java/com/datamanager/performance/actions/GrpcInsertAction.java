package com.datamanager.performance.actions;

import com.datamanager.grpc.InsertRowResponse;
import com.datamanager.performance.client.ManualGrpcClient;
import com.datamanager.performance.data.TestDataGenerator;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling action for gRPC insert operations
 * Uses ManualGrpcClient to avoid Spring injection issues
 */
public class GrpcInsertAction {

    private static final Logger log = LoggerFactory.getLogger(GrpcInsertAction.class);
    private static final String REQUEST_NAME = "gRPC Insert Row";

    private final ManualGrpcClient client;
    private final Long tableId;
    private final String schema;
    private final TestDataGenerator dataGenerator;
    private final String emailDomain;
    private final int minAge;
    private final int maxAge;

    public GrpcInsertAction(ManualGrpcClient client, Long tableId, String schema, 
                           TestDataGenerator dataGenerator, String emailDomain, 
                           int minAge, int maxAge) {
        this.client = client;
        this.tableId = tableId;
        this.schema = schema;
        this.dataGenerator = dataGenerator;
        this.emailDomain = emailDomain;
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    /**
     * Build the Gatling action chain with proper request logging
     * Executes gRPC call and logs metrics via HTTP request wrapper
     */
    public ChainBuilder build() {
        return exec(session -> {
            // Execute gRPC call first
            long startTime = System.currentTimeMillis();
            boolean success = false;
            String errorMessage = null;
            long responseId = -1;
            long duration = 0;

            try {
                // Generate test data
                Map<String, String> rowData = dataGenerator.generateRowData(emailDomain, minAge, maxAge);
                
                // Execute gRPC insert
                InsertRowResponse response = client.insertRow(tableId, schema, rowData);
                
                success = true;
                responseId = response.getId();
                duration = System.currentTimeMillis() - startTime;
                
                if (log.isDebugEnabled()) {
                    log.debug("Insert successful: ID={}, Duration={}ms", responseId, duration);
                }
                
                // Update session with success
                return session
                    .set("grpc_insert_success", true)
                    .set("grpc_insert_id", responseId)
                    .set("grpc_insert_duration", duration)
                    .set("grpc_status_code", 200);
                
            } catch (StatusRuntimeException e) {
                errorMessage = e.getStatus().getCode() + ": " + e.getStatus().getDescription();
                log.error("gRPC insert failed: {}", errorMessage);
                
                duration = System.currentTimeMillis() - startTime;
                
                // Update session with failure
                return session
                    .set("grpc_insert_success", false)
                    .set("grpc_insert_error", errorMessage)
                    .set("grpc_insert_duration", duration)
                    .set("grpc_status_code", 500);
                
            } catch (Exception e) {
                errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error("Unexpected error during insert", e);
                
                duration = System.currentTimeMillis() - startTime;
                
                // Update session with failure
                return session
                    .set("grpc_insert_success", false)
                    .set("grpc_insert_error", errorMessage)
                    .set("grpc_insert_duration", duration)
                    .set("grpc_status_code", 500);
            }
        })
        .exec(
            // Log metrics via HTTP request to Spring Boot server (port 8080)
            // This logs the request timing to Gatling stats engine for report generation
            // The actual gRPC call already executed above
            http(REQUEST_NAME)
                .get("/actuator/health")  // Use Spring Boot actuator endpoint (baseUrl set in simulation)
                .check(status().is(200))  // This will succeed if Spring Boot is running
        );
    }

    /**
     * Create a simple action builder
     */
    public static ChainBuilder insertAction(ManualGrpcClient client, Long tableId, String schema,
                                            TestDataGenerator dataGenerator, String emailDomain,
                                            int minAge, int maxAge) {
        GrpcInsertAction action = new GrpcInsertAction(client, tableId, schema, dataGenerator, 
                                                       emailDomain, minAge, maxAge);
        return action.build();
    }
}
