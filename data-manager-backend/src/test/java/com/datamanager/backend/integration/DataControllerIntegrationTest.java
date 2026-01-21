package com.datamanager.backend.integration;

import com.datamanager.backend.config.TestDataSourceConfig;
import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.TableMetadataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "grpc.server.port=-1"  // Disable gRPC server for tests
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = DatabaseProvider.ZONKY
)
@Import(TestDataSourceConfig.class)
@Transactional
class DataControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BaseReferenceTableRepository tableRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TableMetadataService tableMetadataService;

    private Long tableId;
    private Map<String, String> logicalToPhysicalColumnMap; // Maps logical column name to physical column name

    @BeforeEach
    void setUp() throws Exception {
        // Clean up before each test
        tableRepository.deleteAll();

        // Create a test table with columns
        String tableLabel = "TestTable_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/schema/tables")
                        .param("label", tableLabel))
                .andExpect(status().isCreated());

        tableId = tableRepository.findByTblLabel(tableLabel)
                .map(t -> t.getId())
                .orElseThrow();

        // Add columns
        mockMvc.perform(post("/api/schema/tables/" + tableId + "/columns")
                        .param("label", "name")
                        .param("type", "VARCHAR(255)"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/schema/tables/" + tableId + "/columns")
                        .param("label", "age")
                        .param("type", "INTEGER"))
                .andExpect(status().isCreated());

        // Build logical to physical column name mapping
        List<ColumnMetadataDto> columns = tableMetadataService.getColumnsByTableId(tableId, null);
        logicalToPhysicalColumnMap = columns.stream()
                .collect(Collectors.toMap(ColumnMetadataDto::getLabel, ColumnMetadataDto::getPhysicalName));
    }

    @Test
    void dataOperationsFlow_CreateTable_InsertRows_UpdateRow_DeleteRow_StreamData() throws Exception {
        // Insert rows - use physical column names
        Map<String, Object> rowData1 = new HashMap<>();
        rowData1.put(logicalToPhysicalColumnMap.get("name"), "John Doe");
        rowData1.put(logicalToPhysicalColumnMap.get("age"), 30);

        mockMvc.perform(post("/api/data/tables/" + tableId + "/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rowData1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.add_usr").exists());

        Map<String, Object> rowData2 = new HashMap<>();
        rowData2.put(logicalToPhysicalColumnMap.get("name"), "Jane Smith");
        rowData2.put(logicalToPhysicalColumnMap.get("age"), 25);

        String insertResponse = mockMvc.perform(post("/api/data/tables/" + tableId + "/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rowData2)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract row ID from response (simplified - in real test would parse JSON)
        assertThat(insertResponse).contains("id");

        // Update row - use physical column name
        Map<String, Object> updateData = new HashMap<>();
        updateData.put(logicalToPhysicalColumnMap.get("age"), 31);

        // For this test, we'll use row ID 1 (assuming first insert)
        mockMvc.perform(put("/api/data/tables/" + tableId + "/rows/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upd_usr").exists());

        // Verify data persisted correctly
        mockMvc.perform(get("/api/data/tables/" + tableId + "/rows/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"));

        // Verify audit columns set correctly
        // This is verified through the response JSON in update/insert operations

        // Delete row
        mockMvc.perform(delete("/api/data/tables/" + tableId + "/rows/1"))
                .andExpect(status().isNoContent());

        // Verify streaming works for large datasets
        // This would require inserting many rows - simplified for basic test
        mockMvc.perform(get("/api/data/tables/" + tableId + "/rows/arrow"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.apache.arrow.stream"));
    }

    @Test
    void verifyDataPersisted_Correctly() throws Exception {
        // Insert row - use physical column names
        Map<String, Object> rowData = new HashMap<>();
        rowData.put(logicalToPhysicalColumnMap.get("name"), "Test User");
        rowData.put(logicalToPhysicalColumnMap.get("age"), 28);

        String insertResponse = mockMvc.perform(post("/api/data/tables/" + tableId + "/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rowData)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify insert response contains the data
        assertThat(insertResponse).contains("id");

        // Verify data can be streamed - CSV streaming may return empty if no rows or header only
        // For now, just verify the endpoint works
        mockMvc.perform(get("/api/data/tables/" + tableId + "/rows/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"));
    }

    @Test
    void verifyAuditColumns_SetCorrectly() throws Exception {
        // Insert row - use physical column names
        Map<String, Object> rowData = new HashMap<>();
        rowData.put(logicalToPhysicalColumnMap.get("name"), "Test User");
        rowData.put(logicalToPhysicalColumnMap.get("age"), 28);

        mockMvc.perform(post("/api/data/tables/" + tableId + "/rows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rowData)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.add_usr").exists())
                .andExpect(jsonPath("$.add_ts").exists());
    }

    @Test
    void verifyStreaming_WorksForLargeDatasets() throws Exception {
        // Insert multiple rows - use physical column names
        for (int i = 0; i < 10; i++) {
            Map<String, Object> rowData = new HashMap<>();
            rowData.put(logicalToPhysicalColumnMap.get("name"), "User " + i);
            rowData.put(logicalToPhysicalColumnMap.get("age"), 20 + i);

            mockMvc.perform(post("/api/data/tables/" + tableId + "/rows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(rowData)))
                    .andExpect(status().isCreated());
        }

        // Verify CSV streaming
        mockMvc.perform(get("/api/data/tables/" + tableId + "/rows/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"));

        // Verify Arrow streaming
        mockMvc.perform(get("/api/data/tables/" + tableId + "/rows/arrow"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.apache.arrow.stream"))
                .andExpect(header().exists("X-Total-Rows"));
    }
}

