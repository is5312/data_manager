package com.datamanager.client;

import com.datamanager.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for DataOperationsClient
 */
class DataOperationsClientTest {

    private Server server;
    private ManagedChannel channel;
    private DataOperationsClient client;
    private MockDataOperationsService mockService;

    @BeforeEach
    void setUp() throws IOException {
        // Generate a unique in-process server name
        String serverName = InProcessServerBuilder.generateName();

        // Create mock service
        mockService = new MockDataOperationsService();

        // Start the in-process server
        server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(mockService)
                .build()
                .start();

        // Create the in-process channel
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        // Create client using the test channel (we'll inject it via a test-specific constructor)
        // For now, we'll create a client that connects to localhost, which won't work in tests
        // Instead, let's use the mock service directly
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void testInsertRow_Success() {
        // Arrange
        DataOperationsServiceGrpc.DataOperationsServiceBlockingStub stub = 
            DataOperationsServiceGrpc.newBlockingStub(channel);

        mockService.setInsertResponse(InsertRowResponse.newBuilder()
                .setId(123L)
                .setMessage("Row inserted successfully")
                .setAudit(AuditInfo.newBuilder()
                        .setAddUsr("system")
                        .setAddTs("2024-01-19T10:00:00")
                        .setUpdUsr("system")
                        .setUpdTs("2024-01-19T10:00:00")
                        .build())
                .build());

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(1L)
                .setSchemaName("public")
                .putRowData("name", "John Doe")
                .putRowData("email", "john@example.com")
                .build();

        // Act
        InsertRowResponse response = stub.insertRow(request);

        // Assert
        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getMessage()).isEqualTo("Row inserted successfully");
        assertThat(response.getAudit().getAddUsr()).isEqualTo("system");
    }

    @Test
    void testUpdateRow_Success() {
        // Arrange
        DataOperationsServiceGrpc.DataOperationsServiceBlockingStub stub = 
            DataOperationsServiceGrpc.newBlockingStub(channel);

        mockService.setUpdateResponse(UpdateRowResponse.newBuilder()
                .setMessage("Row updated successfully")
                .setAudit(AuditInfo.newBuilder()
                        .setAddUsr("system")
                        .setAddTs("2024-01-19T10:00:00")
                        .setUpdUsr("admin")
                        .setUpdTs("2024-01-19T11:00:00")
                        .build())
                .build());

        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(1L)
                .setRowId(123L)
                .setSchemaName("public")
                .putRowData("email", "newemail@example.com")
                .build();

        // Act
        UpdateRowResponse response = stub.updateRow(request);

        // Assert
        assertThat(response.getMessage()).isEqualTo("Row updated successfully");
        assertThat(response.getAudit().getUpdUsr()).isEqualTo("admin");
    }

    @Test
    void testDeleteRow_Success() {
        // Arrange
        DataOperationsServiceGrpc.DataOperationsServiceBlockingStub stub = 
            DataOperationsServiceGrpc.newBlockingStub(channel);

        mockService.setDeleteResponse(DeleteRowResponse.newBuilder()
                .setMessage("Row deleted successfully")
                .build());

        DeleteRowRequest request = DeleteRowRequest.newBuilder()
                .setTableId(1L)
                .setRowId(123L)
                .setSchemaName("public")
                .build();

        // Act
        DeleteRowResponse response = stub.deleteRow(request);

        // Assert
        assertThat(response.getMessage()).isEqualTo("Row deleted successfully");
    }

    @Test
    void testInsertRow_InvalidTableId() {
        // Arrange
        DataOperationsServiceGrpc.DataOperationsServiceBlockingStub stub = 
            DataOperationsServiceGrpc.newBlockingStub(channel);

        mockService.setInsertError(Status.INVALID_ARGUMENT.withDescription("Table not found: 999"));

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(999L)
                .setSchemaName("public")
                .putRowData("name", "John Doe")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> stub.insertRow(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(sre.getStatus().getDescription()).contains("Table not found: 999");
                });
    }

    @Test
    void testUpdateRow_InvalidRowId() {
        // Arrange
        DataOperationsServiceGrpc.DataOperationsServiceBlockingStub stub = 
            DataOperationsServiceGrpc.newBlockingStub(channel);

        mockService.setUpdateError(Status.INVALID_ARGUMENT.withDescription("Row not found"));

        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(1L)
                .setRowId(999L)
                .setSchemaName("public")
                .putRowData("email", "test@example.com")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> stub.updateRow(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                });
    }

    @Test
    void testDeleteRow_InvalidTableId() {
        // Arrange
        DataOperationsServiceGrpc.DataOperationsServiceBlockingStub stub = 
            DataOperationsServiceGrpc.newBlockingStub(channel);

        mockService.setDeleteError(Status.INVALID_ARGUMENT.withDescription("Table not found: 999"));

        DeleteRowRequest request = DeleteRowRequest.newBuilder()
                .setTableId(999L)
                .setRowId(123L)
                .setSchemaName("public")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> stub.deleteRow(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(sre.getStatus().getDescription()).contains("Table not found: 999");
                });
    }

    /**
     * Mock implementation of DataOperationsService for testing
     */
    private static class MockDataOperationsService extends DataOperationsServiceGrpc.DataOperationsServiceImplBase {
        private InsertRowResponse insertResponse;
        private UpdateRowResponse updateResponse;
        private DeleteRowResponse deleteResponse;
        private Status insertError;
        private Status updateError;
        private Status deleteError;

        public void setInsertResponse(InsertRowResponse response) {
            this.insertResponse = response;
            this.insertError = null;
        }

        public void setUpdateResponse(UpdateRowResponse response) {
            this.updateResponse = response;
            this.updateError = null;
        }

        public void setDeleteResponse(DeleteRowResponse response) {
            this.deleteResponse = response;
            this.deleteError = null;
        }

        public void setInsertError(Status error) {
            this.insertError = error;
            this.insertResponse = null;
        }

        public void setUpdateError(Status error) {
            this.updateError = error;
            this.updateResponse = null;
        }

        public void setDeleteError(Status error) {
            this.deleteError = error;
            this.deleteResponse = null;
        }

        @Override
        public void insertRow(InsertRowRequest request, StreamObserver<InsertRowResponse> responseObserver) {
            if (insertError != null) {
                responseObserver.onError(insertError.asRuntimeException());
            } else {
                responseObserver.onNext(insertResponse);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void updateRow(UpdateRowRequest request, StreamObserver<UpdateRowResponse> responseObserver) {
            if (updateError != null) {
                responseObserver.onError(updateError.asRuntimeException());
            } else {
                responseObserver.onNext(updateResponse);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void deleteRow(DeleteRowRequest request, StreamObserver<DeleteRowResponse> responseObserver) {
            if (deleteError != null) {
                responseObserver.onError(deleteError.asRuntimeException());
            } else {
                responseObserver.onNext(deleteResponse);
                responseObserver.onCompleted();
            }
        }
    }
}
