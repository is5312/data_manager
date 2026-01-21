package com.datamanager.backend.grpc;

import com.datamanager.backend.service.DataService;
import com.datamanager.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataOperationsGrpcService
 */
@ExtendWith(MockitoExtension.class)
class DataOperationsGrpcServiceTest {

    @Mock
    private DataService dataService;

    @Mock
    private StreamObserver<InsertRowResponse> insertResponseObserver;

    @Mock
    private StreamObserver<UpdateRowResponse> updateResponseObserver;

    @Mock
    private StreamObserver<DeleteRowResponse> deleteResponseObserver;

    @InjectMocks
    private DataOperationsGrpcService grpcService;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(dataService, insertResponseObserver, updateResponseObserver, deleteResponseObserver);
    }

    @Test
    void insertRow_Success() {
        // Arrange
        long tableId = 1L;
        Map<String, String> requestData = new HashMap<>();
        requestData.put("name", "John Doe");
        requestData.put("email", "john@example.com");

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
                .putAllRowData(requestData)
                .build();

        // Act
        grpcService.insertRow(request, insertResponseObserver);

        // Assert
        ArgumentCaptor<InsertRowResponse> responseCaptor = ArgumentCaptor.forClass(InsertRowResponse.class);
        verify(insertResponseObserver).onNext(responseCaptor.capture());
        verify(insertResponseObserver).onCompleted();
        verify(insertResponseObserver, never()).onError(any());

        InsertRowResponse response = responseCaptor.getValue();
        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getMessage()).isEqualTo("Row inserted successfully");
        assertThat(response.getAudit().getAddUsr()).isEqualTo("system");
        assertThat(response.getAudit().getAddTs()).isEqualTo("2024-01-19T10:00:00");
    }

    @Test
    void insertRow_InvalidTableId() {
        // Arrange
        long tableId = 999L;
        Map<String, String> requestData = new HashMap<>();
        requestData.put("name", "John Doe");

        when(dataService.insertRow(eq(tableId), anyMap()))
                .thenThrow(new IllegalArgumentException("Table not found: 999"));

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName("public")
                .putAllRowData(requestData)
                .build();

        // Act
        grpcService.insertRow(request, insertResponseObserver);

        // Assert
        ArgumentCaptor<StatusRuntimeException> errorCaptor = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(insertResponseObserver).onError(errorCaptor.capture());
        verify(insertResponseObserver, never()).onNext(any());
        verify(insertResponseObserver, never()).onCompleted();

        StatusRuntimeException error = errorCaptor.getValue();
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(error.getStatus().getDescription()).contains("Table not found: 999");
    }

    @Test
    void insertRow_InternalError() {
        // Arrange
        long tableId = 1L;
        Map<String, String> requestData = new HashMap<>();
        requestData.put("name", "John Doe");

        when(dataService.insertRow(eq(tableId), anyMap()))
                .thenThrow(new RuntimeException("Database connection failed"));

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName("public")
                .putAllRowData(requestData)
                .build();

        // Act
        grpcService.insertRow(request, insertResponseObserver);

        // Assert
        ArgumentCaptor<StatusRuntimeException> errorCaptor = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(insertResponseObserver).onError(errorCaptor.capture());

        StatusRuntimeException error = errorCaptor.getValue();
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(error.getStatus().getDescription()).contains("Internal error");
    }

    @Test
    void updateRow_Success() {
        // Arrange
        long tableId = 1L;
        long rowId = 123L;
        Map<String, String> requestData = new HashMap<>();
        requestData.put("email", "newemail@example.com");

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
                .putAllRowData(requestData)
                .build();

        // Act
        grpcService.updateRow(request, updateResponseObserver);

        // Assert
        ArgumentCaptor<UpdateRowResponse> responseCaptor = ArgumentCaptor.forClass(UpdateRowResponse.class);
        verify(updateResponseObserver).onNext(responseCaptor.capture());
        verify(updateResponseObserver).onCompleted();
        verify(updateResponseObserver, never()).onError(any());

        UpdateRowResponse response = responseCaptor.getValue();
        assertThat(response.getMessage()).isEqualTo("Row updated successfully");
        assertThat(response.getAudit().getUpdUsr()).isEqualTo("admin");
        assertThat(response.getAudit().getUpdTs()).isEqualTo("2024-01-19T11:00:00");
    }

    @Test
    void updateRow_InvalidRowId() {
        // Arrange
        long tableId = 1L;
        long rowId = 999L;
        Map<String, String> requestData = new HashMap<>();
        requestData.put("email", "newemail@example.com");

        when(dataService.updateRow(eq(tableId), eq(rowId), anyMap()))
                .thenThrow(new IllegalArgumentException("Row not found"));

        UpdateRowRequest request = UpdateRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName("public")
                .putAllRowData(requestData)
                .build();

        // Act
        grpcService.updateRow(request, updateResponseObserver);

        // Assert
        ArgumentCaptor<StatusRuntimeException> errorCaptor = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(updateResponseObserver).onError(errorCaptor.capture());

        StatusRuntimeException error = errorCaptor.getValue();
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void deleteRow_Success() {
        // Arrange
        long tableId = 1L;
        long rowId = 123L;

        doNothing().when(dataService).deleteRow(eq(tableId), eq(rowId));

        DeleteRowRequest request = DeleteRowRequest.newBuilder()
                .setTableId(tableId)
                .setRowId(rowId)
                .setSchemaName("public")
                .build();

        // Act
        grpcService.deleteRow(request, deleteResponseObserver);

        // Assert
        ArgumentCaptor<DeleteRowResponse> responseCaptor = ArgumentCaptor.forClass(DeleteRowResponse.class);
        verify(deleteResponseObserver).onNext(responseCaptor.capture());
        verify(deleteResponseObserver).onCompleted();
        verify(deleteResponseObserver, never()).onError(any());

        DeleteRowResponse response = responseCaptor.getValue();
        assertThat(response.getMessage()).isEqualTo("Row deleted successfully");
        verify(dataService).deleteRow(tableId, rowId);
    }

    @Test
    void deleteRow_InvalidTableId() {
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

        // Act
        grpcService.deleteRow(request, deleteResponseObserver);

        // Assert
        ArgumentCaptor<StatusRuntimeException> errorCaptor = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(deleteResponseObserver).onError(errorCaptor.capture());

        StatusRuntimeException error = errorCaptor.getValue();
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(error.getStatus().getDescription()).contains("Table not found");
    }

    @Test
    void insertRow_WithEmptyRowData() {
        // Arrange
        long tableId = 1L;
        Map<String, String> emptyData = new HashMap<>();

        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("id", 124L);
        serviceResponse.put("message", "Row inserted successfully");
        serviceResponse.put("add_usr", "system");
        serviceResponse.put("add_ts", "2024-01-19T10:00:00");
        serviceResponse.put("upd_usr", "system");
        serviceResponse.put("upd_ts", "2024-01-19T10:00:00");

        when(dataService.insertRow(eq(tableId), anyMap())).thenReturn(serviceResponse);

        InsertRowRequest request = InsertRowRequest.newBuilder()
                .setTableId(tableId)
                .setSchemaName("public")
                .putAllRowData(emptyData)
                .build();

        // Act
        grpcService.insertRow(request, insertResponseObserver);

        // Assert
        verify(insertResponseObserver).onNext(any());
        verify(insertResponseObserver).onCompleted();
    }

    @Test
    void updateRow_WithMultipleFields() {
        // Arrange
        long tableId = 1L;
        long rowId = 123L;
        Map<String, String> requestData = new HashMap<>();
        requestData.put("name", "Jane Doe");
        requestData.put("email", "jane@example.com");
        requestData.put("age", "25");

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
                .setSchemaName("dmgr")
                .putAllRowData(requestData)
                .build();

        // Act
        grpcService.updateRow(request, updateResponseObserver);

        // Assert
        verify(updateResponseObserver).onNext(any());
        verify(updateResponseObserver).onCompleted();
        
        // Verify the service was called with the correct data
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dataService).updateRow(eq(tableId), eq(rowId), dataCaptor.capture());
        
        Map<String, Object> capturedData = dataCaptor.getValue();
        assertThat(capturedData).hasSize(3);
        assertThat(capturedData.get("name")).isEqualTo("Jane Doe");
        assertThat(capturedData.get("email")).isEqualTo("jane@example.com");
        assertThat(capturedData.get("age")).isEqualTo("25");
    }
}
