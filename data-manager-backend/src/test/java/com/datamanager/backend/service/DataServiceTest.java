package com.datamanager.backend.service;

import com.datamanager.backend.dao.DataDao;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.impl.DataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataServiceTest {

    @Mock
    private DataDao dataDao;

    @Mock
    private BaseReferenceTableRepository tableRepository;

    @InjectMocks
    private DataServiceImpl dataService;

    private BaseReferenceTable testTable;
    private Map<String, Object> testRowData;
    private Map<String, Object> auditData;

    @BeforeEach
    void setUp() {
        testTable = new BaseReferenceTable();
        testTable.setId(1L);
        testTable.setTblLabel("TestTable");
        testTable.setTblLink("tbl_test123");

        testRowData = new HashMap<>();
        testRowData.put("name", "Test Name");
        testRowData.put("age", 30);

        auditData = new HashMap<>();
        auditData.put("id", 1L);
        auditData.put("add_usr", "system");
        auditData.put("add_ts", LocalDateTime.now());
        auditData.put("upd_usr", null);
        auditData.put("upd_ts", null);
    }

    @Test
    void insertRow_InsertsRowIntoPhysicalTable() {
        // Given
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.insertRow("tbl_test123", testRowData)).thenReturn(auditData);

        // When
        Map<String, Object> result = dataService.insertRow(1L, testRowData);

        // Then
        verify(dataDao, times(1)).insertRow("tbl_test123", testRowData);
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(1L);
    }

    @Test
    void insertRow_SetsAuditColumns_AddUsrAddTs() {
        // Given
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.insertRow("tbl_test123", testRowData)).thenReturn(auditData);

        // When
        Map<String, Object> result = dataService.insertRow(1L, testRowData);

        // Then
        assertThat(result.get("add_usr")).isEqualTo("system");
        assertThat(result.get("add_ts")).isNotNull();
        verify(dataDao, times(1)).insertRow(eq("tbl_test123"), any());
    }

    @Test
    void insertRow_ReturnsCompleteRow_WithId() {
        // Given
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.insertRow("tbl_test123", testRowData)).thenReturn(auditData);

        // When
        Map<String, Object> result = dataService.insertRow(1L, testRowData);

        // Then
        assertThat(result).containsKey("id");
        assertThat(result).containsKey("message");
        assertThat(result).containsKey("add_usr");
        assertThat(result).containsKey("add_ts");
    }

    @Test
    void insertRow_ValidatesColumnNamesExist() {
        // Given
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.insertRow("tbl_test123", testRowData))
                .thenThrow(new IllegalArgumentException("Column 'invalid_column' does not exist"));

        // When & Then
        assertThatThrownBy(() -> dataService.insertRow(1L, testRowData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Column");

        verify(dataDao, times(1)).insertRow(eq("tbl_test123"), any());
    }

    @Test
    void insertRow_HandlesDataTypeConversions() {
        // Given
        Map<String, Object> rowDataWithString = new HashMap<>();
        rowDataWithString.put("age", "30"); // String instead of Integer
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.insertRow("tbl_test123", rowDataWithString)).thenReturn(auditData);

        // When
        Map<String, Object> result = dataService.insertRow(1L, rowDataWithString);

        // Then
        assertThat(result).isNotNull();
        verify(dataDao, times(1)).insertRow(eq("tbl_test123"), eq(rowDataWithString));
    }

    @Test
    void insertRow_ThrowsException_ForNonExistentTable() {
        // Given
        when(tableRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> dataService.insertRow(999L, testRowData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table not found");

        verify(dataDao, never()).insertRow(any(), any());
    }

    @Test
    void updateRow_UpdatesRowInPhysicalTable() {
        // Given
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "Updated Name");
        Map<String, Object> updatedAuditData = new HashMap<>();
        updatedAuditData.put("add_usr", "system");
        updatedAuditData.put("add_ts", LocalDateTime.now().minusDays(1));
        updatedAuditData.put("upd_usr", "system");
        updatedAuditData.put("upd_ts", LocalDateTime.now());

        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.updateRow("tbl_test123", 1L, updateData)).thenReturn(updatedAuditData);

        // When
        Map<String, Object> result = dataService.updateRow(1L, 1L, updateData);

        // Then
        verify(dataDao, times(1)).updateRow("tbl_test123", 1L, updateData);
        assertThat(result).isNotNull();
    }

    @Test
    void updateRow_UpdatesAuditColumns_UpdUsrUpdTs() {
        // Given
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "Updated Name");
        Map<String, Object> updatedAuditData = new HashMap<>();
        updatedAuditData.put("add_usr", "system");
        updatedAuditData.put("add_ts", LocalDateTime.now().minusDays(1));
        updatedAuditData.put("upd_usr", "system");
        updatedAuditData.put("upd_ts", LocalDateTime.now());

        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.updateRow("tbl_test123", 1L, updateData)).thenReturn(updatedAuditData);

        // When
        Map<String, Object> result = dataService.updateRow(1L, 1L, updateData);

        // Then
        assertThat(result.get("upd_usr")).isEqualTo("system");
        assertThat(result.get("upd_ts")).isNotNull();
        verify(dataDao, times(1)).updateRow(eq("tbl_test123"), eq(1L), any());
    }

    @Test
    void updateRow_PreservesOriginal_AddUsrAddTs() {
        // Given
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "Updated Name");
        LocalDateTime originalAddTs = LocalDateTime.now().minusDays(1);
        Map<String, Object> updatedAuditData = new HashMap<>();
        updatedAuditData.put("add_usr", "original_user");
        updatedAuditData.put("add_ts", originalAddTs);
        updatedAuditData.put("upd_usr", "system");
        updatedAuditData.put("upd_ts", LocalDateTime.now());

        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.updateRow("tbl_test123", 1L, updateData)).thenReturn(updatedAuditData);

        // When
        Map<String, Object> result = dataService.updateRow(1L, 1L, updateData);

        // Then
        assertThat(result.get("add_usr")).isEqualTo("original_user");
        assertThat(result.get("add_ts")).isEqualTo(originalAddTs);
    }

    @Test
    void updateRow_ReturnsCompleteRow() {
        // Given
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "Updated Name");
        Map<String, Object> updatedAuditData = new HashMap<>();
        updatedAuditData.put("add_usr", "system");
        updatedAuditData.put("add_ts", LocalDateTime.now());
        updatedAuditData.put("upd_usr", "system");
        updatedAuditData.put("upd_ts", LocalDateTime.now());

        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.updateRow("tbl_test123", 1L, updateData)).thenReturn(updatedAuditData);

        // When
        Map<String, Object> result = dataService.updateRow(1L, 1L, updateData);

        // Then
        assertThat(result).containsKey("message");
        assertThat(result).containsKey("add_usr");
        assertThat(result).containsKey("add_ts");
        assertThat(result).containsKey("upd_usr");
        assertThat(result).containsKey("upd_ts");
    }

    @Test
    void updateRow_ValidatesColumnNamesExist() {
        // Given
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("invalid_column", "value");
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.updateRow("tbl_test123", 1L, updateData))
                .thenThrow(new IllegalArgumentException("Column 'invalid_column' does not exist"));

        // When & Then
        assertThatThrownBy(() -> dataService.updateRow(1L, 1L, updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Column");

        verify(dataDao, times(1)).updateRow(eq("tbl_test123"), eq(1L), any());
    }

    @Test
    void updateRow_ThrowsException_ForNonExistentRow() {
        // Given
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "Updated Name");
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(dataDao.updateRow("tbl_test123", 999L, updateData))
                .thenThrow(new IllegalArgumentException("Row not found with ID: 999"));

        // When & Then
        assertThatThrownBy(() -> dataService.updateRow(1L, 999L, updateData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row not found");

        verify(dataDao, times(1)).updateRow(eq("tbl_test123"), eq(999L), any());
    }

    @Test
    void deleteRow_DeletesRowFromPhysicalTable() {
        // Given
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        doNothing().when(dataDao).deleteRow("tbl_test123", 1L);

        // When
        dataService.deleteRow(1L, 1L);

        // Then
        verify(dataDao, times(1)).deleteRow("tbl_test123", 1L);
    }

    @Test
    void deleteRow_ThrowsException_ForNonExistentRow() {
        // Given
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        doThrow(new IllegalArgumentException("Row not found with ID: 999"))
                .when(dataDao).deleteRow("tbl_test123", 999L);

        // When & Then
        assertThatThrownBy(() -> dataService.deleteRow(1L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Row not found");

        verify(dataDao, times(1)).deleteRow("tbl_test123", 999L);
    }

    @Test
    void deleteRow_ThrowsException_ForNonExistentTable() {
        // Given
        when(tableRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> dataService.deleteRow(999L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table not found");

        verify(dataDao, never()).deleteRow(any(), any());
    }
}

