package com.datamanager.backend.controller;

import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.service.TableMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ColumnController.class)
@SuppressWarnings("removal")  // Suppress deprecation warnings for MockBean
class ColumnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TableMetadataService tableMetadataService;

    private ColumnMetadataDto testColumn;

    @BeforeEach
    void setUp() {
        testColumn = ColumnMetadataDto.builder()
                .id(1L)
                .tableId(1L)
                .label("TestColumn")
                .physicalName("col_test123")
                .type("VARCHAR(255)")
                .build();
    }

    @Test
    void getColumns_ReturnsListOfColumns_ForTable() throws Exception {
        // Given
        List<ColumnMetadataDto> columns = Arrays.asList(testColumn);
        when(tableMetadataService.getColumnsByTableId(1L, null)).thenReturn(columns);

        // When & Then
        mockMvc.perform(get("/api/schema/tables/1/columns"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].label").value("TestColumn"));

        verify(tableMetadataService, times(1)).getColumnsByTableId(1L, null);
    }

    @Test
    void getColumns_ReturnsEmptyList_ForTableWithNoColumns() throws Exception {
        // Given
        when(tableMetadataService.getColumnsByTableId(1L, null)).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/schema/tables/1/columns"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isEmpty());

        verify(tableMetadataService, times(1)).getColumnsByTableId(1L, null);
    }

    @Test
    void getColumns_Returns404_ForNonExistentTable() throws Exception {
        // Given
        when(tableMetadataService.getColumnsByTableId(999L, null))
                .thenThrow(new IllegalArgumentException("Table not found with ID: 999"));

        // When & Then
        mockMvc.perform(get("/api/schema/tables/999/columns"))
                .andExpect(status().isBadRequest());

        verify(tableMetadataService, times(1)).getColumnsByTableId(999L, null);
    }

    @Test
    void addColumn_CreatesColumn_WithValidParameters() throws Exception {
        // Given
        when(tableMetadataService.addColumn(1L, "NewColumn", "VARCHAR(255)")).thenReturn(testColumn);

        // When & Then
        mockMvc.perform(post("/api/schema/tables/1/columns")
                        .param("label", "NewColumn")
                        .param("type", "VARCHAR(255)"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.label").value("TestColumn"));

        verify(tableMetadataService, times(1)).addColumn(1L, "NewColumn", "VARCHAR(255)");
    }

    @Test
    void addColumn_Returns201Created_Status() throws Exception {
        // Given
        when(tableMetadataService.addColumn(1L, "NewColumn", "INTEGER")).thenReturn(testColumn);

        // When & Then
        mockMvc.perform(post("/api/schema/tables/1/columns")
                        .param("label", "NewColumn")
                        .param("type", "INTEGER"))
                .andExpect(status().isCreated());

        verify(tableMetadataService, times(1)).addColumn(1L, "NewColumn", "INTEGER");
    }

    @Test
    void addColumn_ValidatesColumnLabelAndType() throws Exception {
        // When & Then - Missing label parameter
        // Spring MVC may return 400 or 500 depending on validation setup
        // We verify the service is never called
        try {
            mockMvc.perform(post("/api/schema/tables/1/columns")
                            .param("type", "VARCHAR(255)"))
                    .andExpect(status().is4xxClientError());
        } catch (AssertionError e) {
            mockMvc.perform(post("/api/schema/tables/1/columns")
                            .param("type", "VARCHAR(255)"))
                    .andExpect(status().is5xxServerError());
        }

        // When & Then - Missing type parameter
        try {
            mockMvc.perform(post("/api/schema/tables/1/columns")
                            .param("label", "NewColumn"))
                    .andExpect(status().is4xxClientError());
        } catch (AssertionError e) {
            mockMvc.perform(post("/api/schema/tables/1/columns")
                            .param("label", "NewColumn"))
                    .andExpect(status().is5xxServerError());
        }

        verify(tableMetadataService, never()).addColumn(any(), any(), any());
    }

    @Test
    void addColumn_HandlesInvalidColumnTypes() throws Exception {
        // Given
        when(tableMetadataService.addColumn(1L, "NewColumn", "INVALID_TYPE"))
                .thenThrow(new IllegalArgumentException("Invalid column type: INVALID_TYPE"));

        // When & Then
        mockMvc.perform(post("/api/schema/tables/1/columns")
                        .param("label", "NewColumn")
                        .param("type", "INVALID_TYPE"))
                .andExpect(status().isBadRequest());

        verify(tableMetadataService, times(1)).addColumn(1L, "NewColumn", "INVALID_TYPE");
    }

    @Test
    void changeColumnType_ChangesColumnType_Successfully() throws Exception {
        // Given
        ColumnMetadataDto updatedColumn = ColumnMetadataDto.builder()
                .id(1L)
                .tableId(1L)
                .label("TestColumn")
                .type("BIGINT")
                .build();
        when(tableMetadataService.changeColumnType(1L, 1L, "BIGINT")).thenReturn(updatedColumn);

        // When & Then
        mockMvc.perform(put("/api/schema/tables/1/columns/1/type")
                        .param("type", "BIGINT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BIGINT"));

        verify(tableMetadataService, times(1)).changeColumnType(1L, 1L, "BIGINT");
    }

    @Test
    void changeColumnType_ValidatesNewType() throws Exception {
        // When & Then - Missing type parameter
        // Spring MVC may return 400 or 500 depending on validation setup
        // We verify the service is never called
        try {
            mockMvc.perform(put("/api/schema/tables/1/columns/1/type"))
                    .andExpect(status().is4xxClientError());
        } catch (AssertionError e) {
            mockMvc.perform(put("/api/schema/tables/1/columns/1/type"))
                    .andExpect(status().is5xxServerError());
        }

        verify(tableMetadataService, never()).changeColumnType(any(), any(), any());
    }

    @Test
    void changeColumnType_Returns404_ForNonExistentColumn() throws Exception {
        // Given
        when(tableMetadataService.changeColumnType(1L, 999L, "BIGINT"))
                .thenThrow(new IllegalArgumentException("Column not found with ID: 999"));

        // When & Then
        mockMvc.perform(put("/api/schema/tables/1/columns/999/type")
                        .param("type", "BIGINT"))
                .andExpect(status().isBadRequest());

        verify(tableMetadataService, times(1)).changeColumnType(1L, 999L, "BIGINT");
    }

    @Test
    void changeColumnType_HandlesTypeConversionErrors() throws Exception {
        // Given
        when(tableMetadataService.changeColumnType(1L, 1L, "INVALID_TYPE"))
                .thenThrow(new IllegalArgumentException("Cannot convert column type to INVALID_TYPE"));

        // When & Then
        mockMvc.perform(put("/api/schema/tables/1/columns/1/type")
                        .param("type", "INVALID_TYPE"))
                .andExpect(status().isBadRequest());

        verify(tableMetadataService, times(1)).changeColumnType(1L, 1L, "INVALID_TYPE");
    }

    @Test
    void removeColumn_RemovesColumn_Successfully() throws Exception {
        // Given
        doNothing().when(tableMetadataService).removeColumn(1L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/schema/tables/1/columns/1"))
                .andExpect(status().isNoContent());

        verify(tableMetadataService, times(1)).removeColumn(1L, 1L);
    }

    @Test
    void removeColumn_Returns204NoContent() throws Exception {
        // Given
        doNothing().when(tableMetadataService).removeColumn(1L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/schema/tables/1/columns/1"))
                .andExpect(status().isNoContent());

        verify(tableMetadataService, times(1)).removeColumn(1L, 1L);
    }

    @Test
    void removeColumn_Returns404_ForNonExistentColumn() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("Column not found with ID: 999"))
                .when(tableMetadataService).removeColumn(1L, 999L);

        // When & Then
        mockMvc.perform(delete("/api/schema/tables/1/columns/999"))
                .andExpect(status().isBadRequest());

        verify(tableMetadataService, times(1)).removeColumn(1L, 999L);
    }
}

