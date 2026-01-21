package com.datamanager.backend.grpc;

import com.datamanager.backend.service.DataService;
import com.datamanager.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;

/**
 * gRPC service implementation for data operations
 * Uses existing DataService to perform insert, update, delete operations
 */
@GrpcService
@Profile("!test")
@Slf4j
public class DataOperationsGrpcService extends DataOperationsServiceGrpc.DataOperationsServiceImplBase {

    private final DataService dataService;

    public DataOperationsGrpcService(DataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public void insertRow(InsertRowRequest request, StreamObserver<InsertRowResponse> responseObserver) {
        try {
            log.info("gRPC InsertRow request - tableId: {}, schema: {}, rowData: {}",
                    request.getTableId(), request.getSchemaName(), request.getRowDataMap());

            // Convert proto map to Java map with Object values
            Map<String, Object> rowData = new HashMap<>(request.getRowDataMap());

            // Call existing service layer
            Map<String, Object> result = dataService.insertRow(request.getTableId(), rowData);

            // Build response
            InsertRowResponse response = InsertRowResponse.newBuilder()
                    .setId(((Number) result.get("id")).longValue())
                    .setMessage((String) result.get("message"))
                    .setAudit(AuditInfo.newBuilder()
                            .setAddUsr(String.valueOf(result.get("add_usr")))
                            .setAddTs(String.valueOf(result.get("add_ts")))
                            .setUpdUsr(String.valueOf(result.get("upd_usr")))
                            .setUpdTs(String.valueOf(result.get("upd_ts")))
                            .build())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC InsertRow completed successfully for tableId: {}", request.getTableId());

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument for InsertRow: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in InsertRow gRPC call", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updateRow(UpdateRowRequest request, StreamObserver<UpdateRowResponse> responseObserver) {
        try {
            log.info("gRPC UpdateRow request - tableId: {}, rowId: {}, schema: {}, rowData: {}",
                    request.getTableId(), request.getRowId(), request.getSchemaName(), request.getRowDataMap());

            // Convert proto map to Java map with Object values
            Map<String, Object> rowData = new HashMap<>(request.getRowDataMap());

            // Call existing service layer
            Map<String, Object> result = dataService.updateRow(
                    request.getTableId(),
                    request.getRowId(),
                    rowData
            );

            // Build response
            UpdateRowResponse response = UpdateRowResponse.newBuilder()
                    .setMessage((String) result.get("message"))
                    .setAudit(AuditInfo.newBuilder()
                            .setAddUsr(String.valueOf(result.get("add_usr")))
                            .setAddTs(String.valueOf(result.get("add_ts")))
                            .setUpdUsr(String.valueOf(result.get("upd_usr")))
                            .setUpdTs(String.valueOf(result.get("upd_ts")))
                            .build())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC UpdateRow completed successfully for tableId: {}, rowId: {}",
                    request.getTableId(), request.getRowId());

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument for UpdateRow: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in UpdateRow gRPC call", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void deleteRow(DeleteRowRequest request, StreamObserver<DeleteRowResponse> responseObserver) {
        try {
            log.info("gRPC DeleteRow request - tableId: {}, rowId: {}, schema: {}",
                    request.getTableId(), request.getRowId(), request.getSchemaName());

            // Call existing service layer
            dataService.deleteRow(request.getTableId(), request.getRowId());

            // Build response
            DeleteRowResponse response = DeleteRowResponse.newBuilder()
                    .setMessage("Row deleted successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC DeleteRow completed successfully for tableId: {}, rowId: {}",
                    request.getTableId(), request.getRowId());

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument for DeleteRow: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in DeleteRow gRPC call", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
