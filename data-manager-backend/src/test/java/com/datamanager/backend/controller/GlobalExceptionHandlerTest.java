package com.datamanager.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TableController.class, GlobalExceptionHandler.class})
@SuppressWarnings("removal")  // Suppress deprecation warnings for MockBean
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.datamanager.backend.service.TableMetadataService tableMetadataService;

    @Test
    void handleIllegalArgumentException_Returns400BadRequest() throws Exception {
        // Given
        when(tableMetadataService.getTableById(eq(999L), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenThrow(new IllegalArgumentException("Table not found with ID: 999"));

        // When & Then
        mockMvc.perform(get("/api/schema/tables/999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Table not found")));

        verify(tableMetadataService, times(1)).getTableById(eq(999L), org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    void handleIllegalArgumentException_IncludesErrorMessage_InResponseBody() throws Exception {
        // Given
        String errorMessage = "Invalid table name: cannot be empty";
        when(tableMetadataService.createTable("", "DESIGN_TIME"))
                .thenThrow(new IllegalArgumentException(errorMessage));

        // When & Then
        mockMvc.perform(post("/api/schema/tables")
                        .param("label", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(errorMessage));

        verify(tableMetadataService, times(1)).createTable("", "DESIGN_TIME");
    }

    @Test
    void handleException_Returns500InternalServerError() throws Exception {
        // Given
        when(tableMetadataService.getAllTables(anyString()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        mockMvc.perform(get("/api/schema/tables"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("error")));

        verify(tableMetadataService, times(1)).getAllTables(anyString());
    }

    @Test
    void handleException_IncludesErrorMessage_InResponseBody() throws Exception {
        // Given
        String errorMessage = "Unexpected database error";
        when(tableMetadataService.getAllTables(anyString()))
                .thenThrow(new RuntimeException(errorMessage));

        // When & Then
        mockMvc.perform(get("/api/schema/tables"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMessage)));

        verify(tableMetadataService, times(1)).getAllTables(anyString());
    }

    @Test
    void handleException_LogsExceptionDetails() throws Exception {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        when(tableMetadataService.getAllTables(anyString())).thenThrow(exception);

        // When & Then
        mockMvc.perform(get("/api/schema/tables"))
                .andExpect(status().isInternalServerError());

        verify(tableMetadataService, times(1)).getAllTables(anyString());
        // Note: Actual logging verification would require a logging framework test setup
    }
}

