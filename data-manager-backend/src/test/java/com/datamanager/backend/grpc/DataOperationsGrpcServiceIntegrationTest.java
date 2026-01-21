package com.datamanager.backend.grpc;

import com.datamanager.backend.service.DataService;
import com.datamanager.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Integration tests for DataOperationsGrpcService using in-process gRPC server
 */
@ExtendWith(MockitoExtension.class)
class DataOperationsGrpcServiceIntegrationTest {

    @Mock
    private DataService dataService;

    private final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    private DataOperationsServiceGrpc.DataOperationsServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() throws IOException {
        // Generate a unique in-process server name
        String serverName = InProcessServerBuilder.generateName();

        // Create the service
        DataOperationsGrpcService service = new DataOperationsGrpcService(dataService);

        // Create the in-process server
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start());

        // Create the in-process channel
        ManagedChannel channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        // Create the blocking stub
        blockingStub = DataOperationsServiceGrpc.newBlockingStub(channel);
    }

    // GrpcCleanupRule automatically cleans up registered servers and channels

    @Test
    void insertRow_EndToEnd_Success() {
        // Arrange
        long tableId = 1L;
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("id", 123L);
        serviceResponse.put("message", "Row inserted successfully");
        serviceResponse.put("add_usr", "system");
        serviceResponse.put("add_ts", "2024-01-19T10:00:00");
        serviceResponse.put("upd_usr", "system");
        serviceResponse.put("upd_ts", "2024-01-19T10:00:00");

        when(dataService.insertRow(eq(tableId), anyMap())).thenReturn(serviceResponse);

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName("public")
                .putRowData("name", "John Doe")
                .putRowData("email", "john@example.com")
                .build();

        // Act
        InsertRowResponse response = blockingStub.insertRow(request);

        // Assert
        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getMessage()).isEqualTo("Row inserted successfully");
        assertThat(response.getAudit().getAddUsr()).isEqualTo("system");
        assertThat(response.getAudit().getAddTs()).isEqualTo("2024-01-19T10:00:00");
    }

    @Test
    void insertRow_EndToEnd_InvalidTableId() {
        // Arrange
        long tableId = 999L;
        when(dataService.insertRow(eq(tableId), anyMap()))
                .thenThrow(new IllegalArgumentException("Table not found: 999"));

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName("public")
                .putRowData("name", "John Doe")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.insertRow(request))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT")
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(sre.getStatus().getDescription()).contains("Table not found: 999");
                });
    }

    @Test
    void updateRow_EndToEnd_Success() {
        // Arrange
        long tableId = 1L;
        long rowId = 123L;
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("message", "Row updated successfully");
        serviceResponse.put("add_usr", "system");
        serviceResponse.put("add_ts", "2024-01-19T10:00:00");
        serviceResponse.put("upd_usr", "admin");
        serviceResponse.put("upd_ts", "2024-01-19T11:00:00");

        when(dataService.updateRow(eq(tableId), eq(rowId), anyMap())).thenReturn(serviceResponse);

        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName("public")
                .putRowData("email", "newemail@example.com")
                .build();

        // Act
        UpdateRowResponse response = blockingStub.updateRow(request);

        // Assert
        assertThat(response.getMessage()).isEqualTo("Row updated successfully");
        assertThat(response.getAudit().getUpdUsr()).isEqualTo("admin");
        assertThat(response.getAudit().getUpdTs()).isEqualTo("2024-01-19T11:00:00");
    }

    @Test
    void updateRow_EndToEnd_InvalidRowId() {
        // Arrange
        long tableId = 1L;
        long rowId = 999L;
        when(dataService.updateRow(eq(tableId), eq(rowId), anyMap()))
                .thenThrow(new IllegalArgumentException("Row not found"));

        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName("public")
                .putRowData("email", "newemail@example.com")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.updateRow(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                });
    }

    @Test
    void deleteRow_EndToEnd_Success() {
        // Arrange
        long tableId = 1L;
        long rowId = 123L;

        DeleteRowRequest request = DeleteRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName("public")
                .build();

        // Act
        DeleteRowResponse response = blockingStub.deleteRow(request);

        // Assert
        assertThat(response.getMessage()).isEqualTo("Row deleted successfully");
    }

    @Test
    void deleteRow_EndToEnd_InvalidTableId() {
        // Arrange
        long tableId = 999L;
        long rowId = 123L;
        doThrow(new IllegalArgumentException("Table not found: 999"))
                .when(dataService).deleteRow(eq(tableId), eq(rowId));

        DeleteRowRequest request = DeleteRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName("public")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> blockingStub.deleteRow(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(sre.getStatus().getDescription()).contains("Table not found");
                });
    }

    @Test
    void insertRow_WithDifferentSchema() {
        // Arrange
        long tableId = 2L;
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("id", 456L);
        serviceResponse.put("message", "Row inserted successfully");
        serviceResponse.put("add_usr", "system");
        serviceResponse.put("add_ts", "2024-01-19T10:00:00");
        serviceResponse.put("upd_usr", "system");
        serviceResponse.put("upd_ts", "2024-01-19T10:00:00");

        when(dataService.insertRow(eq(tableId), anyMap())).thenReturn(serviceResponse);

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName("dmgr")  // Different schema
                .putRowData("name", "Test User")
                .build();

        // Act
        InsertRowResponse response = blockingStub.insertRow(request);

        // Assert
        assertThat(response.getId()).isEqualTo(456L);
        assertThat(response.getMessage()).isEqualTo("Row inserted successfully");
    }

    @Test
    void updateRow_WithMultipleFields() {
        // Arrange
        long tableId = 1L;
        long rowId = 123L;
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("message", "Row updated successfully");
        serviceResponse.put("add_usr", "system");
        serviceResponse.put("add_ts", "2024-01-19T10:00:00");
        serviceResponse.put("upd_usr", "admin");
        serviceResponse.put("upd_ts", "2024-01-19T11:00:00");

        when(dataService.updateRow(eq(tableId), eq(rowId), anyMap())).thenReturn(serviceResponse);

        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName("public")
                .putRowData("name", "Jane Doe")
                .putRowData("email", "jane@example.com")
                .putRowData("age", "25")
                .build();

        // Act
        UpdateRowResponse response = blockingStub.updateRow(request);

        // Assert
        assertThat(response.getMessage()).isEqualTo("Row updated successfully");
    }
}
