package com.datamanager.performance.client;

import com.datamanager.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manual gRPC client that creates and manages its own channel
 * This bypasses Spring's @GrpcClient injection to avoid initialization issues
 */
public class ManualGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ManualGrpcClient.class);

    private final ManagedChannel channel;
    private final DataOperationsServiceGrpc.DataOperationsServiceBlockingStub blockingStub;

    public ManualGrpcClient(String host, int port) {
        log.info("Creating manual gRPC client for {}:{}", host, port);
        
        this.channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .maxInboundMessageSize(10 * 1024 * 1024)  // 10MB
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build();
        
        this.blockingStub = DataOperationsServiceGrpc.newBlockingStub(channel);
        
        log.info("Manual gRPC client created successfully");
    }

    public InsertRowResponse insertRow(long tableId, String schema, Map<String, String> rowData) {
        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName(schema)
                .putAllRowData(rowData)
                .build();

        return blockingStub.insertRow(request);
    }

    public UpdateRowResponse updateRow(long tableId, long rowId, String schema, Map<String, String> rowData) {
        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName(schema)
                .putAllRowData(rowData)
                .build();

        return blockingStub.updateRow(request);
    }

    public DeleteRowResponse deleteRow(long tableId, long rowId) {
        DeleteRowRequest request = DeleteRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .build();

        return blockingStub.deleteRow(request);
    }

    public void shutdown() throws InterruptedException {
        log.info("Shutting down gRPC client");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
