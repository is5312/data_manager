package com.datamanager.client;

import com.datamanager.grpc.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * gRPC client service for data operations using grpc-spring-boot-starter.
 * The @GrpcClient annotation automatically manages the channel lifecycle.
 * 
 * Configuration is done via application.yml:
 * grpc.client.dataOperations.address=static://localhost:9090
 * grpc.client.dataOperations.negotiationType=PLAINTEXT
 */
@Service
public class DataOperationsClient {

    @GrpcClient("dataOperations")
    private DataOperationsServiceGrpc.DataOperationsServiceBlockingStub blockingStub;

    /**
     * Insert a new row into a table
     * Thread-safe method for concurrent access
     *
     * @param tableId  The table ID
     * @param schema   The schema name (e.g., "public", "dmgr")
     * @param rowData  Map of column names to values
     * @return InsertRowResponse containing the new row ID and audit information
     */
    public synchronized InsertRowResponse insertRow(long tableId, String schema, Map<String, String> rowData) {
        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName(schema)
                .putAllRowData(rowData)
                .build();

        return blockingStub.insertRow(request);
    }

    /**
     * Update an existing row in a table
     *
     * @param tableId  The table ID
     * @param rowId    The row ID to update
     * @param schema   The schema name (e.g., "public", "dmgr")
     * @param rowData  Map of column names to new values
     * @return UpdateRowResponse containing audit information
     */
    public UpdateRowResponse updateRow(long tableId, long rowId, String schema, Map<String, String> rowData) {
        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName(schema)
                .putAllRowData(rowData)
                .build();

        return blockingStub.updateRow(request);
    }

    /**
     * Delete a row from a table
     *
     * @param tableId  The table ID
     * @param rowId    The row ID to delete
     * @param schema   The schema name (e.g., "public", "dmgr")
     * @return DeleteRowResponse confirming deletion
     */
    public DeleteRowResponse deleteRow(long tableId, long rowId, String schema) {
        DeleteRowRequest request = DeleteRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName(schema)
                .build();

        return blockingStub.deleteRow(request);
    }

    // Example usage methods for convenience

    /**
     * Insert a row with data
     */
    public long insertRowSimple(long tableId, String schema, String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even");
        }

        Map<String, String> rowData = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            rowData.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }

        InsertRowResponse response = insertRow(tableId, schema, rowData);
        return response.getId();
    }

    /**
     * Update a row with data
     */
    public void updateRowSimple(long tableId, long rowId, String schema, String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even");
        }

        Map<String, String> rowData = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            rowData.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }

        updateRow(tableId, rowId, schema, rowData);
    }
}
