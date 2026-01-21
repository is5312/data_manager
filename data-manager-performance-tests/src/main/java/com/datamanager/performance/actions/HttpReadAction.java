package com.datamanager.performance.actions;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling action for HTTP read operations
 * Reads table data via REST API and logs metrics to Gatling stats engine
 */
public class HttpReadAction {

    private static final Logger log = LoggerFactory.getLogger(HttpReadAction.class);
    private static final String REQUEST_NAME = "HTTP Read Table Data";

    private final Long tableId;
    private final String schema;
    private final String backendBaseUrl;

    public HttpReadAction(Long tableId, String schema, String backendBaseUrl) {
        this.tableId = tableId;
        this.schema = schema;
        this.backendBaseUrl = backendBaseUrl;
    }

    /**
     * Build the Gatling action chain for reading table data
     */
    public ChainBuilder build() {
        return exec(session -> {
            // Get schema from session if available, otherwise use default
            String readSchema = session.contains("read_schema") 
                ? session.getString("read_schema") 
                : schema;
            
            // Execute HTTP read request
            String url = String.format("%s/api/data/tables/%d/rows/arrow?schema=%s", 
                    backendBaseUrl, tableId, readSchema);
            
            return session.set("read_url", url);
        })
        .exec(
            // Log metrics via HTTP request (Gatling will track this for reports)
            http(REQUEST_NAME)
                .get(session -> session.getString("read_url"))
                .check(status().is(200))
        );
    }

    /**
     * Create a simple action builder with static table ID and schema
     */
    public static ChainBuilder readAction(Long tableId, String schema, String backendBaseUrl) {
        HttpReadAction action = new HttpReadAction(tableId, schema, backendBaseUrl);
        return action.build();
    }

    /**
     * Create a simple action builder with dynamic table ID and schema from session
     */
    public static ChainBuilder readAction(java.util.function.Function<Session, Long> tableIdSupplier,
                                         java.util.function.Function<Session, String> schemaSupplier,
                                         String backendBaseUrl) {
        return exec(session -> {
            Long currentTableId = tableIdSupplier.apply(session);
            String currentSchema = schemaSupplier.apply(session);
            HttpReadAction action = new HttpReadAction(currentTableId, currentSchema, backendBaseUrl);
            return session;
        })
        .exec(session -> {
            Long currentTableId = tableIdSupplier.apply(session);
            String currentSchema = schemaSupplier.apply(session);
            String url = String.format("%s/api/data/tables/%d/rows/arrow?schema=%s", 
                    backendBaseUrl, currentTableId, currentSchema);
            return session.set("read_url", url);
        })
        .exec(
            http(REQUEST_NAME)
                .get(session -> session.getString("read_url"))
                .check(status().is(200))
        );
    }
}
