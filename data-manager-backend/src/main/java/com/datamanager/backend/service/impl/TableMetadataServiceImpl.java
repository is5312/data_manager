package com.datamanager.backend.service.impl;

import com.datamanager.backend.dao.SchemaDao;
import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.entity.BaseColumnMap;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.mapper.MetadataMapper;
import com.datamanager.backend.repository.BaseColumnMapRepository;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.TableMetadataService;
import com.datamanager.backend.util.IdGenerator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of TableMetadataService
 * Uses Spring JPA for metadata operations and JOOQ for dynamic schema
 * operations
 */
@Service
@Slf4j
public class TableMetadataServiceImpl implements TableMetadataService {

    private final BaseReferenceTableRepository tableRepository;
    private final BaseColumnMapRepository columnRepository;
    private final SchemaDao schemaDao;

    public TableMetadataServiceImpl(
            BaseReferenceTableRepository tableRepository,
            BaseColumnMapRepository columnRepository,
            SchemaDao schemaDao) {
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.schemaDao = schemaDao;
    }

    @Override
    @Transactional
    public TableMetadataDto createTable(String tableLabel) {
        log.info("Creating table with label: {}", tableLabel);

        // 1. Generate unique physical table name
        String physicalTableName = generatePhysicalTableName();

        // 2. Create physical table using JOOQ DAO
        schemaDao.createTable(physicalTableName);

        // 3. Create metadata entry using JPA
        BaseReferenceTable entity = new BaseReferenceTable();
        entity.setTblLabel(tableLabel);
        entity.setTblLink(physicalTableName);
        entity.setAddUsr("system");

        BaseReferenceTable saved = tableRepository.save(entity);

        log.info("Successfully created table: {} with physical name: {}", tableLabel, physicalTableName);

        return MetadataMapper.toDto(saved);
    }


    /**
     * Private helper method to add a column using an already-fetched table entity.
     * This avoids redundant database queries when the table entity is already available.
     */
    private ColumnMetadataDto addColumn(BaseReferenceTable table, String columnLabel, String columnType) {
        log.info("Adding column {} to table {}", columnLabel, table.getTblLabel());

        String physicalTableName = table.getTblLink();

        // 1. Generate unique physical column name
        String physicalColumnName = generatePhysicalColumnName();

        // 2. Add column to physical table using JOOQ DAO
        schemaDao.addColumn(physicalTableName, physicalColumnName, columnType);

        // 3. Create column metadata using JPA
        BaseColumnMap columnEntity = new BaseColumnMap();
        columnEntity.setReferenceTable(table);
        columnEntity.setTblLink(physicalTableName);
        columnEntity.setColLabel(columnLabel);
        columnEntity.setColLink(physicalColumnName);
        columnEntity.setAddUsr("system");

        BaseColumnMap saved = columnRepository.save(columnEntity);

        log.info("Successfully added column {} to table {}", columnLabel, table.getTblLabel());

        return MetadataMapper.toDto(saved, columnType);
    }

    @Override
    @Transactional
    public ColumnMetadataDto addColumn(Long tableId, String columnLabel, String columnType) {
        log.info("Adding column {} to table ID {}", columnLabel, tableId);

        BaseReferenceTable table = getTableEntityById(tableId);
        return addColumn(table, columnLabel, columnType);
    }

    @Override
    @Transactional
    public ColumnMetadataDto changeColumnType(Long tableId, Long columnId, String columnType) {
        log.info("Changing column type for column {} in table {} to {}", columnId, tableId, columnType);

        if (columnType == null || columnType.isBlank()) {
            throw new IllegalArgumentException("Column type cannot be null or blank");
        }

        BaseReferenceTable table = getTableEntityById(tableId);

        BaseColumnMap column = columnRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found with ID: " + columnId));

        if (!column.getReferenceTable().getId().equals(tableId)) {
            throw new IllegalArgumentException("Column does not belong to the specified table");
        }

        // ALTER physical column type
        schemaDao.alterColumnType(table.getTblLink(), column.getColLink(), columnType);

        // Return updated metadata with refreshed physical type
        Map<String, String> typesByPhysicalName = schemaDao.getColumnTypes(table.getTblLink());
        String actualType = typesByPhysicalName.get(column.getColLink());
        return MetadataMapper.toDto(column, actualType != null ? actualType : columnType);
    }

    @Override
    @Transactional
    public void removeColumn(Long tableId, Long columnId) {
        log.info("Removing column ID {} from table ID {}", columnId, tableId);

        // 1. Get column metadata
        BaseColumnMap column = columnRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found with ID: " + columnId));

        if (!column.getReferenceTable().getId().equals(tableId)) {
            throw new IllegalArgumentException("Column does not belong to the specified table");
        }

        String physicalTableName = column.getTblLink();
        String physicalColumnName = column.getColLink();

        // 2. Remove column from physical table using JOOQ DAO
        schemaDao.removeColumn(physicalTableName, physicalColumnName);

        // 3. Delete column metadata using JPA
        columnRepository.delete(column);

        log.info("Successfully removed column ID {} from table ID {}", columnId, tableId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableMetadataDto> getAllTables() {
        log.info("Fetching all tables");

        List<BaseReferenceTable> tables = tableRepository.findAll();

        return tables.stream()
                .map(MetadataMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TableMetadataDto getTableById(Long tableId) {
        log.info("Fetching table with ID: {}", tableId);

        BaseReferenceTable table = getTableEntityById(tableId);
        return MetadataMapper.toDto(table);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColumnMetadataDto> getColumnsByTableId(Long tableId) {
        log.info("Fetching columns for table ID: {}", tableId);

        BaseReferenceTable table = getTableEntityById(tableId);
        Map<String, String> typesByPhysicalName = schemaDao.getColumnTypes(table.getTblLink());

        List<BaseColumnMap> columns = columnRepository.findByReferenceTableId(tableId);

        return columns.stream()
                .map(col -> MetadataMapper.toDto(col, typesByPhysicalName.get(col.getColLink())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTable(Long tableId) {
        log.info("Deleting table with ID: {}", tableId);

        BaseReferenceTable table = getTableEntityById(tableId);
        String physicalTableName = table.getTblLink();

        // Drop physical table using JOOQ DAO
        schemaDao.dropTable(physicalTableName);

        // Delete metadata using JPA (cascade will delete columns)
        tableRepository.delete(table);

        log.info("Successfully deleted table ID {} with physical name {}", tableId, physicalTableName);
    }

    @Override
    @Transactional
    public TableMetadataDto renameTable(Long tableId, String newLabel) {
        log.info("Renaming table ID {} to {}", tableId, newLabel);

        BaseReferenceTable table = getTableEntityById(tableId);

        table.setTblLabel(newLabel);
        table.setUpdUsr("system");

        BaseReferenceTable updated = tableRepository.save(table);

        log.info("Successfully renamed table ID {} to {}", tableId, newLabel);

        return MetadataMapper.toDto(updated);
    }

    /**
     * Get table entity by ID, throwing exception if not found
     */
    private BaseReferenceTable getTableEntityById(Long tableId) {
        return tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));
    }

    /**
     * Generate unique physical table name
     */
    private String generatePhysicalTableName() {
        return IdGenerator.generatePhysicalTableName();
    }

    /**
     * Generate unique physical column name
     */
    private String generatePhysicalColumnName() {
        return "col_" + IdGenerator.generateUlid();
    }
}
