package com.datamanager.backend.service;

import com.datamanager.backend.dao.SchemaDao;
import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.service.impl.SchemaFilterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchemaFilterServiceTest {

    @Mock
    private SchemaDao schemaDao;

    @InjectMocks
    private SchemaFilterServiceImpl schemaFilterService;

    private BaseReferenceTable testTable1;
    private BaseReferenceTable testTable2;

    @BeforeEach
    void setUp() {
        testTable1 = new BaseReferenceTable();
        testTable1.setId(1L);
        testTable1.setTblLabel("Table1");
        testTable1.setTblLink("tbl_test1");
        testTable1.setDescription("Description 1");
        testTable1.setVersionNo(1);
        testTable1.setDeploymentType("DESIGN_TIME");
        testTable1.setAddTs(LocalDateTime.now());
        testTable1.setAddUsr("system");

        testTable2 = new BaseReferenceTable();
        testTable2.setId(2L);
        testTable2.setTblLabel("Table2");
        testTable2.setTblLink("tbl_test2");
        testTable2.setDescription("Description 2");
        testTable2.setVersionNo(1);
        testTable2.setDeploymentType("RUN_TIME");
        testTable2.setAddTs(LocalDateTime.now());
        testTable2.setAddUsr("system");
    }

    @Test
    void getTablesBySchema_ReturnsTablesFromSpecifiedSchema() {
        // Given
        List<BaseReferenceTable> tables = Arrays.asList(testTable1, testTable2);
        when(schemaDao.getTablesFromSchema("public")).thenReturn(tables);

        // When
        List<TableMetadataDto> result = schemaFilterService.getTablesBySchema("public");

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getLabel()).isEqualTo("Table1");
        assertThat(result.get(1).getLabel()).isEqualTo("Table2");
        verify(schemaDao, times(1)).getTablesFromSchema("public");
    }

    @Test
    void getTablesBySchema_ReturnsEmptyList_WhenNoTablesInSchema() {
        // Given
        when(schemaDao.getTablesFromSchema("dmgr")).thenReturn(Collections.emptyList());

        // When
        List<TableMetadataDto> result = schemaFilterService.getTablesBySchema("dmgr");

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(schemaDao, times(1)).getTablesFromSchema("dmgr");
    }

    @Test
    void getTablesBySchema_DefaultsToPublic_WhenSchemaNameIsNull() {
        // Given
        List<BaseReferenceTable> tables = Collections.singletonList(testTable1);
        when(schemaDao.getTablesFromSchema("public")).thenReturn(tables);

        // When
        List<TableMetadataDto> result = schemaFilterService.getTablesBySchema(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        verify(schemaDao, times(1)).getTablesFromSchema("public");
    }

    @Test
    void getTablesBySchema_DefaultsToPublic_WhenSchemaNameIsBlank() {
        // Given
        List<BaseReferenceTable> tables = Collections.singletonList(testTable1);
        when(schemaDao.getTablesFromSchema("public")).thenReturn(tables);

        // When
        List<TableMetadataDto> result = schemaFilterService.getTablesBySchema("   ");

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        verify(schemaDao, times(1)).getTablesFromSchema("public");
    }

    @Test
    void getTablesBySchema_MapsEntitiesToDtos_Correctly() {
        // Given
        List<BaseReferenceTable> tables = Collections.singletonList(testTable1);
        when(schemaDao.getTablesFromSchema("public")).thenReturn(tables);

        // When
        List<TableMetadataDto> result = schemaFilterService.getTablesBySchema("public");

        // Then
        assertThat(result).hasSize(1);
        TableMetadataDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLabel()).isEqualTo("Table1");
        assertThat(dto.getPhysicalName()).isEqualTo("tbl_test1");
        assertThat(dto.getDescription()).isEqualTo("Description 1");
        assertThat(dto.getDeploymentType()).isEqualTo("DESIGN_TIME");
    }
}
