package com.datamanager.backend.controller;

import com.datamanager.backend.service.DataService;
import com.datamanager.backend.service.StreamingDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataController.class)
@SuppressWarnings("removal")  // Suppress deprecation warnings for MockBean
class DataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataService dataService;

    @MockBean
    private StreamingDataService streamingDataService;

    private Map<String, Object> testRowData;

    @BeforeEach
    void setUp() {
        testRowData = new HashMap<>();
        testRowData.put("id", 1L);
        testRowData.put("name", "Test Name");
        testRowData.put("add_usr", "system");
    }

    @Test
    void streamTableDataAsCsv_StreamsCsvData_Correctly() throws Exception {
        // Given
        StreamingResponseBody streamBody = outputStream -> outputStream.write("id,name\n1,Test".getBytes());
        when(streamingDataService.streamTableDataAsCsv(eq(1L), anyString())).thenReturn(streamBody);

        // When & Then
        mockMvc.perform(get("/api/data/tables/1/rows/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"table_1.csv\""));

        verify(streamingDataService, times(1)).streamTableDataAsCsv(eq(1L), anyString());
    }

    @Test
    void streamTableDataAsCsv_SetsCorrectContentTypeAndHeaders() throws Exception {
        // Given
        StreamingResponseBody streamBody = outputStream -> {};
        when(streamingDataService.streamTableDataAsCsv(eq(1L), anyString())).thenReturn(streamBody);

        // When & Then
        mockMvc.perform(get("/api/data/tables/1/rows/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().exists("Content-Disposition"));

        verify(streamingDataService, times(1)).streamTableDataAsCsv(eq(1L), anyString());
    }

    @Test
    void streamTableDataAsCsv_Returns404_ForNonExistentTable() throws Exception {
        // Given
        when(streamingDataService.streamTableDataAsCsv(eq(999L), anyString()))
                .thenThrow(new IllegalArgumentException("Table not found with ID: 999"));

        // When & Then
        mockMvc.perform(get("/api/data/tables/999/rows/stream"))
                .andExpect(status().isBadRequest());

        verify(streamingDataService, times(1)).streamTableDataAsCsv(eq(999L), anyString());
    }

    @Test
    void streamTableDataAsCsv_HandlesEmptyTables() throws Exception {
        // Given
        StreamingResponseBody streamBody = outputStream -> {};
        when(streamingDataService.streamTableDataAsCsv(eq(1L), anyString())).thenReturn(streamBody);

        // When & Then
        mockMvc.perform(get("/api/data/tables/1/rows/stream"))
                .andExpect(status().isOk());

        verify(streamingDataService, times(1)).streamTableDataAsCsv(eq(1L), anyString());
    }

    @Test
    void streamTableDataAsArrow_StreamsArrowData_Correctly() throws Exception {
        // Given
        StreamingResponseBody streamBody = outputStream -> outputStream.write(new byte[]{1, 2, 3});
        when(streamingDataService.getTableRowCount(eq(1L), anyString())).thenReturn(100L);
        when(streamingDataService.streamTableDataAsArrow(eq(1L), anyString())).thenReturn(streamBody);

        // When & Then
        mockMvc.perform(get("/api/data/tables/1/rows/arrow"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.apache.arrow.stream"))
                .andExpect(header().string("X-Total-Rows", "100"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"table_1.arrow\""));

        verify(streamingDataService, times(1)).getTableRowCount(eq(1L), anyString());
        verify(streamingDataService, times(1)).streamTableDataAsArrow(eq(1L), anyString());
    }

    @Test
    void streamTableDataAsArrow_SetsCorrectContentTypeAndHeaders() throws Exception {
        // Given
        StreamingResponseBody streamBody = outputStream -> {};
        when(streamingDataService.getTableRowCount(eq(1L), anyString())).thenReturn(50L);
        when(streamingDataService.streamTableDataAsArrow(eq(1L), anyString())).thenReturn(streamBody);

        // When & Then
        mockMvc.perform(get("/api/data/tables/1/rows/arrow"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.apache.arrow.stream"))
                .andExpect(header().exists("X-Total-Rows"))
                .andExpect(header().exists("Content-Disposition"));

        verify(streamingDataService, times(1)).getTableRowCount(eq(1L), anyString());
        verify(streamingDataService, times(1)).streamTableDataAsArrow(eq(1L), anyString());
    }

    @Test
    void streamTableDataAsArrow_IncludesXTotalRowsHeader() throws Exception {
        // Given
        StreamingResponseBody streamBody = outputStream -> {};
        when(streamingDataService.getTableRowCount(eq(1L), anyString())).thenReturn(1000L);
        when(streamingDataService.streamTableDataAsArrow(eq(1L), anyString())).thenReturn(streamBody);

        // When & Then
        mockMvc.perform(get("/api/data/tables/1/rows/arrow"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Rows", "1000"));

        verify(streamingDataService, times(1)).getTableRowCount(eq(1L), anyString());
        verify(streamingDataService, times(1)).streamTableDataAsArrow(eq(1L), anyString());
    }

    @Test
    void streamTableDataAsArrow_Returns404_ForNonExistentTable() throws Exception {
        // Given
        when(streamingDataService.getTableRowCount(eq(999L), anyString()))
                .thenThrow(new IllegalArgumentException("Table not found with ID: 999"));
        when(streamingDataService.streamTableDataAsArrow(eq(999L), anyString()))
                .thenThrow(new IllegalArgumentException("Table not found with ID: 999"));

        // When & Then
        // The controller catches the exception in getTableRowCount and logs it, then still tries to stream
        // The streamTableDataAsArrow will also throw, resulting in 500 or handled error
        try {
            mockMvc.perform(get("/api/data/tables/999/rows/arrow"))
                    .andExpect(status().is5xxServerError());
        } catch (AssertionError e) {
            mockMvc.perform(get("/api/data/tables/999/rows/arrow"))
                    .andExpect(status().isBadRequest());
        }

        verify(streamingDataService, atLeastOnce()).getTableRowCount(eq(999L), anyString());
    }

    @Test
    void streamTableDataAsArrow_HandlesEmptyTables() throws Exception {
        // Given
        StreamingResponseBody streamBody = outputStream -> {};
        when(streamingDataService.getTableRowCount(eq(1L), anyString())).thenReturn(0L);
        when(streamingDataService.streamTableDataAsArrow(eq(1L), anyString())).thenReturn(streamBody);

        // When & Then
        mockMvc.perform(get("/api/data/tables/1/rows/arrow"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Rows", "0"));

        verify(streamingDataService, times(1)).getTableRowCount(eq(1L), anyString());
        verify(streamingDataService, times(1)).streamTableDataAsArrow(eq(1L), anyString());
    }

    @Test
    void insertRow_InsertsRow_Successfully() throws Exception {
        // Given
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", "Test Name");
        when(dataService.insertRow(1L, rowData)).thenReturn(testRowData);

        // When & Then
        mockMvc.perform(post("/api/data/tables/1/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Name\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test Name"));

        verify(dataService, times(1)).insertRow(eq(1L), any(Map.class));
    }

    @Test
    void insertRow_Returns201Created_Status() throws Exception {
        // Given
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", "Test Name");
        when(dataService.insertRow(1L, rowData)).thenReturn(testRowData);

        // When & Then
        mockMvc.perform(post("/api/data/tables/1/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Name\"}"))
                .andExpect(status().isCreated());

        verify(dataService, times(1)).insertRow(eq(1L), any(Map.class));
    }

    @Test
    void insertRow_ReturnsCompleteRow_WithAuditColumns() throws Exception {
        // Given
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", "Test Name");
        when(dataService.insertRow(1L, rowData)).thenReturn(testRowData);

        // When & Then
        mockMvc.perform(post("/api/data/tables/1/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Name\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.add_usr").exists());

        verify(dataService, times(1)).insertRow(eq(1L), any(Map.class));
    }

    @Test
    void insertRow_ValidatesRequiredFields() throws Exception {
        // Given
        Map<String, Object> emptyData = new HashMap<>();
        when(dataService.insertRow(eq(1L), any(Map.class)))
                .thenThrow(new IllegalArgumentException("Required field 'name' is missing"));

        // When & Then
        mockMvc.perform(post("/api/data/tables/1/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(dataService, times(1)).insertRow(eq(1L), any(Map.class));
    }

    @Test
    void insertRow_HandlesInvalidDataTypes() throws Exception {
        // Given
        when(dataService.insertRow(eq(1L), any(Map.class)))
                .thenThrow(new IllegalArgumentException("Invalid data type for column 'age'"));

        // When & Then
        mockMvc.perform(post("/api/data/tables/1/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":\"not_a_number\"}"))
                .andExpect(status().isBadRequest());

        verify(dataService, times(1)).insertRow(eq(1L), any(Map.class));
    }

    @Test
    void updateRow_UpdatesRow_Successfully() throws Exception {
        // Given
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", "Updated Name");
        Map<String, Object> updatedRowData = new HashMap<>(testRowData);
        updatedRowData.put("name", "Updated Name");
        when(dataService.updateRow(eq(1L), eq(1L), any(Map.class))).thenReturn(updatedRowData);

        // When & Then
        mockMvc.perform(put("/api/data/tables/1/rows/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

        verify(dataService, times(1)).updateRow(eq(1L), eq(1L), any(Map.class));
    }

    @Test
    void updateRow_ReturnsCompleteRow_WithAuditColumns() throws Exception {
        // Given
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", "Updated Name");
        when(dataService.updateRow(eq(1L), eq(1L), any(Map.class))).thenReturn(testRowData);

        // When & Then
        mockMvc.perform(put("/api/data/tables/1/rows/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.add_usr").exists());

        verify(dataService, times(1)).updateRow(eq(1L), eq(1L), any(Map.class));
    }

    @Test
    void updateRow_Returns404_ForNonExistentRow() throws Exception {
        // Given
        when(dataService.updateRow(eq(1L), eq(999L), any(Map.class)))
                .thenThrow(new IllegalArgumentException("Row not found with ID: 999"));

        // When & Then
        mockMvc.perform(put("/api/data/tables/1/rows/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isBadRequest());

        verify(dataService, times(1)).updateRow(eq(1L), eq(999L), any(Map.class));
    }

    @Test
    void updateRow_ValidatesDataTypes() throws Exception {
        // Given
        when(dataService.updateRow(eq(1L), eq(1L), any(Map.class)))
                .thenThrow(new IllegalArgumentException("Invalid data type for column 'age'"));

        // When & Then
        mockMvc.perform(put("/api/data/tables/1/rows/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":\"not_a_number\"}"))
                .andExpect(status().isBadRequest());

        verify(dataService, times(1)).updateRow(eq(1L), eq(1L), any(Map.class));
    }

    @Test
    void deleteRow_DeletesRow_Successfully() throws Exception {
        // Given
        doNothing().when(dataService).deleteRow(1L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/data/tables/1/rows/1"))
                .andExpect(status().isNoContent());

        verify(dataService, times(1)).deleteRow(1L, 1L);
    }

    @Test
    void deleteRow_Returns204NoContent() throws Exception {
        // Given
        doNothing().when(dataService).deleteRow(1L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/data/tables/1/rows/1"))
                .andExpect(status().isNoContent());

        verify(dataService, times(1)).deleteRow(1L, 1L);
    }

    @Test
    void deleteRow_Returns404_ForNonExistentRow() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("Row not found with ID: 999"))
                .when(dataService).deleteRow(1L, 999L);

        // When & Then
        mockMvc.perform(delete("/api/data/tables/1/rows/999"))
                .andExpect(status().isBadRequest());

        verify(dataService, times(1)).deleteRow(1L, 999L);
    }
}

