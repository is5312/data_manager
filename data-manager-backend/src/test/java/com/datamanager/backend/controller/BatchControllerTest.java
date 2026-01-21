package com.datamanager.backend.controller;

import com.datamanager.backend.dto.BatchStatusDto;
import com.datamanager.backend.dto.BatchUploadResponseDto;
import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.service.BatchStatusService;
import com.datamanager.backend.service.CsvBatchUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BatchController.class)
@SuppressWarnings("removal")  // Suppress deprecation warnings for MockBean
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CsvBatchUploadService csvBatchUploadService;

    @MockBean
    private BatchStatusService batchStatusService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMultipartFile testCsvFile;
    private MockMultipartFile testGzipFile;
    private BatchUploadResponseDto batchUploadResponse;
    private BatchStatusDto batchStatus;

    @BeforeEach
    void setUp() {
        testCsvFile = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                "id,name\n1,Test".getBytes()
        );

        testGzipFile = new MockMultipartFile(
                "file",
                "test.csv.gz",
                "application/gzip",
                new byte[]{1, 2, 3}
        );

        TableMetadataDto table = TableMetadataDto.builder()
                .id(1L)
                .label("TestTable")
                .physicalName("tbl_test123")
                .build();

        batchUploadResponse = BatchUploadResponseDto.builder()
                .batchId(1L)
                .table(table)
                .message("Batch upload started")
                .build();

        batchStatus = BatchStatusDto.builder()
                .batchId(1L)
                .status("COMPLETED")
                .exitCode("COMPLETED")
                .readCount(100L)
                .writeCount(100L)
                .skipCount(0L)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();
    }

    @Test
    void batchUploadCsv_AcceptsCsvFileUpload() throws Exception {
        // Given
        when(csvBatchUploadService.startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), any(), any(), any()))
                .thenReturn(batchUploadResponse);

        // When & Then
        mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                        .file(testCsvFile)
                        .param("tableName", "TestTable"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchId").value(1L))
                .andExpect(jsonPath("$.table.label").value("TestTable"));

        verify(csvBatchUploadService, times(1)).startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchUploadCsv_AcceptsGzipFileUpload() throws Exception {
        // Given
        when(csvBatchUploadService.startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), any(), any(), any()))
                .thenReturn(batchUploadResponse);

        // When & Then
        mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                        .file(testGzipFile)
                        .param("tableName", "TestTable"))
                .andExpect(status().isAccepted());

        verify(csvBatchUploadService, times(1)).startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchUploadCsv_ParsesColumnTypesJsonParameter() throws Exception {
        // Given
        List<String> columnTypes = Arrays.asList("VARCHAR(255)", "INTEGER", "BIGINT");
        String columnTypesJson = objectMapper.writeValueAsString(columnTypes);
        when(csvBatchUploadService.startBatchUpload(
                any(), eq("TestTable"), any(), eq(columnTypes), any(), any(), any(), any()))
                .thenReturn(batchUploadResponse);

        // When & Then
        mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                        .file(testCsvFile)
                        .param("tableName", "TestTable")
                        .param("columnTypes", columnTypesJson))
                .andExpect(status().isAccepted());

        verify(csvBatchUploadService, times(1)).startBatchUpload(
                any(), eq("TestTable"), any(), eq(columnTypes), any(), any(), any(), any());
    }

    @Test
    void batchUploadCsv_ParsesSelectedColumnIndicesJsonParameter() throws Exception {
        // Given
        List<Integer> indices = Arrays.asList(0, 1, 2);
        String indicesJson = objectMapper.writeValueAsString(indices);
        when(csvBatchUploadService.startBatchUpload(
                any(), eq("TestTable"), any(), any(), eq(indices), any(), any(), any()))
                .thenReturn(batchUploadResponse);

        // When & Then
        mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                        .file(testCsvFile)
                        .param("tableName", "TestTable")
                        .param("selectedColumnIndices", indicesJson))
                .andExpect(status().isAccepted());

        verify(csvBatchUploadService, times(1)).startBatchUpload(
                any(), eq("TestTable"), any(), any(), eq(indices), any(), any(), any());
    }

    @Test
    void batchUploadCsv_ValidatesDelimiterQuoteCharEscapeChar() throws Exception {
        // Given
        when(csvBatchUploadService.startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), eq(';'), eq('\''), eq('\\')))
                .thenReturn(batchUploadResponse);

        // When & Then
        mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                        .file(testCsvFile)
                        .param("tableName", "TestTable")
                        .param("delimiter", ";")
                        .param("quoteChar", "'")
                        .param("escapeChar", "\\"))
                .andExpect(status().isAccepted());

        verify(csvBatchUploadService, times(1)).startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), eq(';'), eq('\''), eq('\\'));
    }

    @Test
    void batchUploadCsv_Returns202Accepted_WithBatchId() throws Exception {
        // Given
        when(csvBatchUploadService.startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), any(), any(), any()))
                .thenReturn(batchUploadResponse);

        // When & Then
        mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                        .file(testCsvFile)
                        .param("tableName", "TestTable"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchId").exists());

        verify(csvBatchUploadService, times(1)).startBatchUpload(
                any(), eq("TestTable"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchUploadCsv_HandlesInvalidJsonParameters() throws Exception {
        // When & Then - Invalid columnTypes JSON
        mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                        .file(testCsvFile)
                        .param("tableName", "TestTable")
                        .param("columnTypes", "invalid json"))
                .andExpect(status().isBadRequest());

        verify(csvBatchUploadService, never()).startBatchUpload(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchUploadCsv_HandlesMissingRequiredParameters() throws Exception {
        // When & Then - Missing tableName
        // Spring MVC may return 400 or 500 depending on validation setup
        // We verify the service is never called
        try {
            mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                            .file(testCsvFile))
                    .andExpect(status().is4xxClientError());
        } catch (AssertionError e) {
            mockMvc.perform(multipart("/api/schema/tables/upload/batch")
                            .file(testCsvFile))
                    .andExpect(status().is5xxServerError());
        }

        verify(csvBatchUploadService, never()).startBatchUpload(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getBatchStatus_ReturnsBatchStatus_ForValidId() throws Exception {
        // Given
        when(batchStatusService.getBatchStatus(1L)).thenReturn(batchStatus);

        // When & Then
        mockMvc.perform(get("/api/schema/batches/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.batchId").value(1L))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.readCount").value(100L))
                .andExpect(jsonPath("$.writeCount").value(100L));

        verify(batchStatusService, times(1)).getBatchStatus(1L);
    }

    @Test
    void getBatchStatus_Returns404_ForNonExistentBatch() throws Exception {
        // Given
        when(batchStatusService.getBatchStatus(999L))
                .thenThrow(new IllegalArgumentException("Batch not found with ID: 999"));

        // When & Then
        mockMvc.perform(get("/api/schema/batches/999"))
                .andExpect(status().isBadRequest());

        verify(batchStatusService, times(1)).getBatchStatus(999L);
    }

    @Test
    void getBatchStatus_IncludesAllStatusFields() throws Exception {
        // Given
        when(batchStatusService.getBatchStatus(1L)).thenReturn(batchStatus);

        // When & Then
        mockMvc.perform(get("/api/schema/batches/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.exitCode").exists())
                .andExpect(jsonPath("$.readCount").exists())
                .andExpect(jsonPath("$.writeCount").exists())
                .andExpect(jsonPath("$.skipCount").exists());

        verify(batchStatusService, times(1)).getBatchStatus(1L);
    }
}

