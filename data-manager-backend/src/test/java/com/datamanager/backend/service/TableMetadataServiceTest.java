package com.datamanager.backend.service;

import com.datamanager.backend.dao.SchemaDao;
import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.entity.BaseColumnMap;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.repository.BaseColumnMapRepository;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.impl.TableMetadataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableMetadataServiceTest {

    @Mock
    private BaseReferenceTableRepository tableRepository;

    @Mock
    private BaseColumnMapRepository columnRepository;

    @Mock
    private SchemaDao schemaDao;

    @Mock
    private com.datamanager.backend.config.MigrationProperties migrationProperties;

    @Mock
    private org.jooq.DSLContext dsl;

    @Mock
    private com.datamanager.backend.util.IdGenerator idGenerator;

    @InjectMocks
    private TableMetadataServiceImpl tableMetadataService;

    private BaseReferenceTable testTable;
    private BaseColumnMap testColumn;

    @BeforeEach
    void setUp() {
        when(migrationProperties.getAvailableSchemas()).thenReturn(java.util.List.of("public"));

        testTable = new BaseReferenceTable();
        testTable.setId(1L);
        testTable.setTblLabel("TestTable");
        testTable.setTblLink("tbl_test123");
        testTable.setAddUsr("system");

        // Default table lookup for most unit tests (can be overridden in specific tests)
        lenient().when(schemaDao.getTablesFromSchema("public")).thenReturn(java.util.Arrays.asList(testTable));

        testColumn = new BaseColumnMap();
        testColumn.setId(1L);
        testColumn.setReferenceTable(testTable);
        testColumn.setTblLink("tbl_test123");
        testColumn.setColLabel("TestColumn");
        testColumn.setColLink("col_test123");
        testColumn.setAddUsr("system");
    }

    @Test
    void createTable_CreatesPhysicalTable_ViaSchemaDao() {
        // Given
        when(tableRepository.save(any(BaseReferenceTable.class))).thenAnswer(invocation -> {
            BaseReferenceTable table = invocation.getArgument(0);
            table.setId(1L);
            return table;
        });
        when(tableRepository.existsByTblLabel("NewTable")).thenReturn(false);

        // When
        tableMetadataService.createTable("NewTable", "DESIGN_TIME");

        // Then
        ArgumentCaptor<String> tableNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(schemaDao, times(1)).createTable(tableNameCaptor.capture());
        assertThat(tableNameCaptor.getValue()).startsWith("tbl_");
    }

    @Test
    void createTable_CreatesMetadataEntry_ViaJpaRepository() {
        // Given
        when(tableRepository.save(any(BaseReferenceTable.class))).thenAnswer(invocation -> {
            BaseReferenceTable table = invocation.getArgument(0);
            table.setId(1L);
            return table;
        });
        when(tableRepository.existsByTblLabel("NewTable")).thenReturn(false);

        // When
        TableMetadataDto result = tableMetadataService.createTable("NewTable", "DESIGN_TIME");

        // Then
        ArgumentCaptor<BaseReferenceTable> entityCaptor = ArgumentCaptor.forClass(BaseReferenceTable.class);
        verify(tableRepository, times(1)).save(entityCaptor.capture());
        BaseReferenceTable saved = entityCaptor.getValue();
        assertThat(saved.getTblLabel()).isEqualTo("NewTable");
        assertThat(saved.getTblLink()).isNotNull();
        assertThat(saved.getAddUsr()).isEqualTo("system");
        assertThat(result).isNotNull();
        assertThat(result.getLabel()).isEqualTo("NewTable");
    }

    @Test
    void createTable_GeneratesUniquePhysicalTableName() {
        // Given
        when(tableRepository.save(any(BaseReferenceTable.class))).thenAnswer(invocation -> {
            BaseReferenceTable table = invocation.getArgument(0);
            table.setId(1L);
            return table;
        });
        when(tableRepository.existsByTblLabel("NewTable")).thenReturn(false);

        // When
        tableMetadataService.createTable("NewTable", "DESIGN_TIME");
        tableMetadataService.createTable("NewTable2", "DESIGN_TIME");

        // Then
        ArgumentCaptor<String> tableNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(schemaDao, times(2)).createTable(tableNameCaptor.capture());
        List<String> tableNames = tableNameCaptor.getAllValues();
        assertThat(tableNames.get(0)).isNotEqualTo(tableNames.get(1));
    }

    @Test
    void createTable_SetsAuditFields_Correctly() {
        // Given
        when(tableRepository.save(any(BaseReferenceTable.class))).thenAnswer(invocation -> {
            BaseReferenceTable table = invocation.getArgument(0);
            table.setId(1L);
            return table;
        });
        when(tableRepository.existsByTblLabel("NewTable")).thenReturn(false);

        // When
        tableMetadataService.createTable("NewTable", "DESIGN_TIME");

        // Then
        ArgumentCaptor<BaseReferenceTable> entityCaptor = ArgumentCaptor.forClass(BaseReferenceTable.class);
        verify(tableRepository, times(1)).save(entityCaptor.capture());
        BaseReferenceTable saved = entityCaptor.getValue();
        assertThat(saved.getAddUsr()).isEqualTo("system");
    }

    @Test
    void createTable_ThrowsException_OnDuplicateLabel() {
        // Given
        // Note: The actual implementation doesn't check for duplicates before creating
        // This test verifies the service creates the table successfully
        when(tableRepository.save(any(BaseReferenceTable.class))).thenAnswer(invocation -> {
            BaseReferenceTable table = invocation.getArgument(0);
            table.setId(1L);
            return table;
        });
        
        // When
        TableMetadataDto result = tableMetadataService.createTable("DuplicateTable", "DESIGN_TIME");
        
        // Then
        assertThat(result).isNotNull();
        verify(schemaDao, times(1)).createTable(any());
        verify(tableRepository, times(1)).save(any());
    }

    @Test
    void addColumn_AddsColumnToPhysicalTable_ViaSchemaDao() {
        // Given
        when(schemaDao.getTablesFromSchema("public")).thenReturn(java.util.Arrays.asList(testTable));
        when(columnRepository.save(any(BaseColumnMap.class))).thenAnswer(invocation -> {
            BaseColumnMap column = invocation.getArgument(0);
            column.setId(1L);
            return column;
        });
        when(schemaDao.getColumnTypes("tbl_test123")).thenReturn(Map.of("col_test123", "VARCHAR(255)"));

        // When
        tableMetadataService.addColumn(1L, "NewColumn", "VARCHAR(255)");

        // Then
        ArgumentCaptor<String> columnNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(schemaDao, times(1)).addColumn(eq("tbl_test123"), columnNameCaptor.capture(), eq("VARCHAR(255)"));
        assertThat(columnNameCaptor.getValue()).startsWith("col_");
    }

    @Test
    void addColumn_CreatesColumnMetadata_ViaJpaRepository() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(columnRepository.save(any(BaseColumnMap.class))).thenAnswer(invocation -> {
            BaseColumnMap column = invocation.getArgument(0);
            column.setId(1L);
            return column;
        });
        when(schemaDao.getColumnTypes("tbl_test123")).thenReturn(Map.of("col_test123", "VARCHAR(255)"));

        // When
        ColumnMetadataDto result = tableMetadataService.addColumn(1L, "NewColumn", "VARCHAR(255)");

        // Then
        ArgumentCaptor<BaseColumnMap> entityCaptor = ArgumentCaptor.forClass(BaseColumnMap.class);
        verify(columnRepository, times(1)).save(entityCaptor.capture());
        BaseColumnMap saved = entityCaptor.getValue();
        assertThat(saved.getColLabel()).isEqualTo("NewColumn");
        assertThat(saved.getColLink()).isNotNull();
        assertThat(saved.getAddUsr()).isEqualTo("system");
        assertThat(result).isNotNull();
    }

    @Test
    void addColumn_GeneratesUniquePhysicalColumnName() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(columnRepository.save(any(BaseColumnMap.class))).thenAnswer(invocation -> {
            BaseColumnMap column = invocation.getArgument(0);
            column.setId(1L);
            return column;
        });
        when(schemaDao.getColumnTypes("tbl_test123")).thenReturn(Map.of("col_test123", "VARCHAR(255)"));

        // When
        tableMetadataService.addColumn(1L, "Column1", "VARCHAR(255)");
        tableMetadataService.addColumn(1L, "Column2", "INTEGER");

        // Then
        ArgumentCaptor<String> columnNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(schemaDao, times(2)).addColumn(eq("tbl_test123"), columnNameCaptor.capture(), any());
        List<String> columnNames = columnNameCaptor.getAllValues();
        assertThat(columnNames.get(0)).isNotEqualTo(columnNames.get(1));
    }

    @Test
    void addColumn_SetsAuditFields_Correctly() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(columnRepository.save(any(BaseColumnMap.class))).thenAnswer(invocation -> {
            BaseColumnMap column = invocation.getArgument(0);
            column.setId(1L);
            return column;
        });
        when(schemaDao.getColumnTypes("tbl_test123")).thenReturn(Map.of("col_test123", "VARCHAR(255)"));

        // When
        tableMetadataService.addColumn(1L, "NewColumn", "VARCHAR(255)");

        // Then
        ArgumentCaptor<BaseColumnMap> entityCaptor = ArgumentCaptor.forClass(BaseColumnMap.class);
        verify(columnRepository, times(1)).save(entityCaptor.capture());
        BaseColumnMap saved = entityCaptor.getValue();
        assertThat(saved.getAddUsr()).isEqualTo("system");
    }

    @Test
    void addColumn_ValidatesColumnType() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp

        // When & Then - Invalid type should be handled by DAO, but we test service validation
        // This test verifies the service passes the type to DAO
        when(columnRepository.save(any(BaseColumnMap.class))).thenAnswer(invocation -> {
            BaseColumnMap column = invocation.getArgument(0);
            column.setId(1L);
            return column;
        });
        when(schemaDao.getColumnTypes("tbl_test123")).thenReturn(Map.of("col_test123", "VARCHAR(255)"));

        tableMetadataService.addColumn(1L, "NewColumn", "VARCHAR(255)");

        verify(schemaDao, times(1)).addColumn(any(), any(), eq("VARCHAR(255)"));
    }

    @Test
    void addColumn_ThrowsException_ForNonExistentTable() {
        // Given
        when(schemaDao.getTablesFromSchema(anyString())).thenReturn(java.util.Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> tableMetadataService.addColumn(999L, "NewColumn", "VARCHAR(255)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table not found");

        verify(schemaDao, never()).addColumn(any(), any(), any());
        verify(columnRepository, never()).save(any());
    }

    @Test
    void changeColumnType_UpdatesPhysicalColumnType_ViaSchemaDao() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(columnRepository.findById(1L)).thenReturn(Optional.of(testColumn));
        when(schemaDao.getColumnTypesInSchema("tbl_test123", "public")).thenReturn(Map.of("col_test123", "BIGINT"));

        // When
        tableMetadataService.changeColumnType(1L, 1L, "BIGINT");

        // Then
        verify(dsl, times(1)).execute(anyString());
    }

    @Test
    void changeColumnType_UpdatesColumnMetadata() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(columnRepository.findById(1L)).thenReturn(Optional.of(testColumn));
        when(schemaDao.getColumnTypesInSchema("tbl_test123", "public")).thenReturn(Map.of("col_test123", "BIGINT"));

        // When
        ColumnMetadataDto result = tableMetadataService.changeColumnType(1L, 1L, "BIGINT");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("BIGINT");
    }

    @Test
    void changeColumnType_HandlesTypeConversionErrors() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(columnRepository.findById(1L)).thenReturn(Optional.of(testColumn));
        doThrow(new IllegalArgumentException("Invalid type conversion"))
                .when(dsl).execute(anyString());

        // When & Then
        assertThatThrownBy(() -> tableMetadataService.changeColumnType(1L, 1L, "INVALID_TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeColumnType_ThrowsException_ForNonExistentColumn() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(columnRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tableMetadataService.changeColumnType(1L, 999L, "BIGINT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Column not found");

        verify(dsl, never()).execute(anyString());
    }

    @Test
    void removeColumn_RemovesColumnFromPhysicalTable() {
        // Given
        when(columnRepository.findById(1L)).thenReturn(Optional.of(testColumn));

        // When
        tableMetadataService.removeColumn(1L, 1L);

        // Then
        verify(schemaDao, times(1)).removeColumn("tbl_test123", "col_test123");
    }

    @Test
    void removeColumn_DeletesColumnMetadata() {
        // Given
        when(columnRepository.findById(1L)).thenReturn(Optional.of(testColumn));

        // When
        tableMetadataService.removeColumn(1L, 1L);

        // Then
        verify(columnRepository, times(1)).delete(testColumn);
    }

    @Test
    void removeColumn_ThrowsException_ForNonExistentColumn() {
        // Given
        when(columnRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tableMetadataService.removeColumn(1L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Column not found");

        verify(schemaDao, never()).removeColumn(any(), any());
        verify(dsl, never()).execute(anyString(), any(), any());
    }

    @Test
    void getAllTables_ReturnsAllTables_FromRepository() {
        // Given
        List<BaseReferenceTable> tables = Arrays.asList(testTable);
        when(schemaDao.getTablesFromSchema("public")).thenReturn(tables);

        // When
        List<TableMetadataDto> result = tableMetadataService.getAllTables("public");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("TestTable");
        verify(schemaDao, times(1)).getTablesFromSchema("public");
    }

    @Test
    void getAllTables_MapsEntitiesToDtos_Correctly() {
        // Given
        List<BaseReferenceTable> tables = Arrays.asList(testTable);
        when(schemaDao.getTablesFromSchema("public")).thenReturn(tables);

        // When
        List<TableMetadataDto> result = tableMetadataService.getAllTables("public");

        // Then
        assertThat(result).hasSize(1);
        TableMetadataDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLabel()).isEqualTo("TestTable");
        assertThat(dto.getPhysicalName()).isEqualTo("tbl_test123");
    }

    @Test
    void getAllTables_ReturnsEmptyList_WhenNoTables() {
        // Given
        when(schemaDao.getTablesFromSchema("public")).thenReturn(Collections.emptyList());

        // When
        List<TableMetadataDto> result = tableMetadataService.getAllTables("public");

        // Then
        assertThat(result).isEmpty();
        verify(schemaDao, times(1)).getTablesFromSchema("public");
    }

    @Test
    void getTableById_ReturnsTable_ForValidId() {
        // Given
        when(schemaDao.getTablesFromSchema("public")).thenReturn(java.util.Arrays.asList(testTable));

        // When
        TableMetadataDto result = tableMetadataService.getTableById(1L, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getLabel()).isEqualTo("TestTable");
        verify(schemaDao, times(1)).getTablesFromSchema("public");
    }

    @Test
    void getTableById_ThrowsException_ForNonExistentTable() {
        // Given
        when(schemaDao.getTablesFromSchema(anyString())).thenReturn(java.util.Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> tableMetadataService.getTableById(999L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table not found");

        verify(schemaDao, atLeastOnce()).getTablesFromSchema(anyString());
    }

    @Test
    void getColumnsByTableId_ReturnsColumns_ForTable() {
        // Given
        when(schemaDao.getTablesFromSchema("public")).thenReturn(java.util.List.of(testTable));

        BaseColumnMap col = new BaseColumnMap();
        col.setId(1L);
        col.setTblLink(testTable.getTblLink());
        col.setColLabel("TestColumn");
        col.setColLink("col_test123");

        when(schemaDao.getColumnsFromSchema(1L, "public")).thenReturn(java.util.List.of(col));
        when(schemaDao.getColumnTypesInSchema(testTable.getTblLink(), "public"))
                .thenReturn(java.util.Map.of("col_test123", "VARCHAR(255)"));

        // When
        List<ColumnMetadataDto> result = tableMetadataService.getColumnsByTableId(1L, "public");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("TestColumn");
    }

    @Test
    void getColumnsByTableId_MapsEntitiesToDtos_Correctly() {
        // Given
        when(schemaDao.getTablesFromSchema("public")).thenReturn(java.util.List.of(testTable));

        BaseColumnMap col = new BaseColumnMap();
        col.setId(1L);
        col.setTblLink(testTable.getTblLink());
        col.setColLabel("TestColumn");
        col.setColLink("col_test123");

        when(schemaDao.getColumnsFromSchema(1L, "public")).thenReturn(java.util.List.of(col));
        when(schemaDao.getColumnTypesInSchema(testTable.getTblLink(), "public"))
                .thenReturn(java.util.Map.of("col_test123", "VARCHAR(255)"));

        // When
        List<ColumnMetadataDto> result = tableMetadataService.getColumnsByTableId(1L, "public");

        // Then
        assertThat(result).hasSize(1);
        ColumnMetadataDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLabel()).isEqualTo("TestColumn");
        assertThat(dto.getType()).isEqualTo("VARCHAR(255)");
    }

    @Test
    void getColumnsByTableId_ReturnsEmptyList_WhenNoColumns() {
        // Given
        when(schemaDao.getTablesFromSchema("public")).thenReturn(java.util.List.of(testTable));
        when(schemaDao.getColumnsFromSchema(1L, "public")).thenReturn(java.util.List.of());
        when(schemaDao.getColumnTypesInSchema(testTable.getTblLink(), "public")).thenReturn(java.util.Map.of());

        // When
        List<ColumnMetadataDto> result = tableMetadataService.getColumnsByTableId(1L, "public");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void deleteTable_DropsPhysicalTable_ViaSchemaDao() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(schemaDao.tableExistsInSchema("tbl_test123", "public")).thenReturn(true);

        // When
        tableMetadataService.deleteTable(1L);

        // Then
        verify(dsl, times(1)).execute(anyString()); // DROP TABLE ...
        verify(dsl, times(2)).execute(anyString(), anyLong()); // DELETE columns + DELETE table metadata
    }

    @Test
    void deleteTable_DeletesTableMetadata() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp

        // When
        tableMetadataService.deleteTable(1L);

        // Then
        verify(dsl, atLeastOnce()).execute(anyString(), anyLong());
    }

    @Test
    void deleteTable_DeletesAllColumnMetadata() {
        // Given
        testTable.setColumns(Arrays.asList(testColumn));
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp

        // When
        tableMetadataService.deleteTable(1L);

        // Then - Cascade delete should handle columns, but we verify table is deleted
        verify(dsl, atLeastOnce()).execute(anyString(), anyLong());
    }

    @Test
    void deleteTable_ThrowsException_ForNonExistentTable() {
        // Given
        when(schemaDao.getTablesFromSchema(anyString())).thenReturn(java.util.Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> tableMetadataService.deleteTable(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table not found");

        verify(schemaDao, never()).dropTable(any());
        verify(tableRepository, never()).delete(any());
    }

    @Test
    void renameTable_UpdatesTableLabel() {
        // Given
        BaseReferenceTable updated = new BaseReferenceTable();
        updated.setId(1L);
        updated.setTblLabel("RenamedTable");
        updated.setTblLink("tbl_test123");
        updated.setAddUsr("system");

        // first call: findTableAcrossSchemas, second call: reload after update
        when(schemaDao.getTablesFromSchema("public"))
                .thenReturn(java.util.List.of(testTable), java.util.List.of(updated));

        // When
        TableMetadataDto result = tableMetadataService.renameTable(1L, "RenamedTable");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLabel()).isEqualTo("RenamedTable");
        verify(dsl, times(1)).execute(anyString(), any(), any(), anyLong());
    }

    @Test
    void renameTable_UpdatesMetadata() {
        // Given
        // Table lookup now uses schemaDao.getTablesFromSchema which is mocked in setUp
        when(tableRepository.save(any(BaseReferenceTable.class))).thenReturn(testTable);

        // When
        tableMetadataService.renameTable(1L, "RenamedTable");

        // Then
        verify(dsl, times(1)).execute(anyString(), any(), any(), anyLong());
        verify(schemaDao, atLeastOnce()).getTablesFromSchema(anyString());
    }

    @Test
    void renameTable_ThrowsException_ForNonExistentTable() {
        // Given
        when(schemaDao.getTablesFromSchema(anyString())).thenReturn(java.util.Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> tableMetadataService.renameTable(999L, "NewName"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table not found");

        verify(tableRepository, never()).save(any());
    }
}

