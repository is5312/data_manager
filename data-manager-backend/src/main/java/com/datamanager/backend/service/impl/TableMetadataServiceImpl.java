package com.datamanager.backend.service.impl;

import com.datamanager.backend.config.MigrationProperties;
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
import org.jooq.DSLContext;
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
    private final MigrationProperties migrationProperties;
    private final DSLContext dsl;

    public TableMetadataServiceImpl(
            BaseReferenceTableRepository tableRepository,
            BaseColumnMapRepository columnRepository,
            SchemaDao schemaDao,
            MigrationProperties migrationProperties,
            DSLContext dsl) {
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.schemaDao = schemaDao;
        this.migrationProperties = migrationProperties;
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public TableMetadataDto createTable(String tableLabel, String deploymentType) {
        log.info("Creating table with label: {} and deployment type: {}", tableLabel, deploymentType);

        // Validate deployment type
        if (deploymentType == null || deploymentType.isBlank()) {
            deploymentType = "DESIGN_TIME";
        }
        if (!deploymentType.equals("RUN_TIME") && !deploymentType.equals("DESIGN_TIME")) {
            throw new IllegalArgumentException("Deployment type must be either 'RUN_TIME' or 'DESIGN_TIME'");
        }

        // 1. Generate unique physical table name
        String physicalTableName = generatePhysicalTableName();

        // 2. Create physical table using JOOQ DAO
        schemaDao.createTable(physicalTableName);

        // 3. Create metadata entry using JPA
        BaseReferenceTable entity = new BaseReferenceTable();
        entity.setTblLabel(tableLabel);
        entity.setTblLink(physicalTableName);
        entity.setDeploymentType(deploymentType);
        entity.setAddUsr("system");

        BaseReferenceTable saved = tableRepository.save(entity);

        log.info("Successfully created table: {} with physical name: {} and deployment type: {}", 
                tableLabel, physicalTableName, deploymentType);

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

        BaseReferenceTable table = findTableAcrossSchemas(tableId);
        return addColumn(table, columnLabel, columnType);
    }

    @Override
    @Transactional
    public ColumnMetadataDto changeColumnType(Long tableId, Long columnId, String columnType) {
        log.info("Changing column type for column {} in table {} to {}", columnId, tableId, columnType);

        if (columnType == null || columnType.isBlank()) {
            throw new IllegalArgumentException("Column type cannot be null or blank");
        }

        BaseReferenceTable table = findTableAcrossSchemas(tableId);

        BaseColumnMap column = columnRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found with ID: " + columnId));

        if (!column.getReferenceTable().getId().equals(tableId)) {
            throw new IllegalArgumentException("Column does not belong to the specified table");
        }

        // Get schema name for the table
        String schemaName = findSchemaForTable(tableId);
        
        // ALTER physical column type in the schema where table exists
        String alterSql = String.format("ALTER TABLE %s.%s ALTER COLUMN %s TYPE %s",
                quoteIdentifier(schemaName), quoteIdentifier(table.getTblLink()), quoteIdentifier(column.getColLink()),
                normalizeTypeForAlter(columnType));
        dsl.execute(alterSql);

        // Return updated metadata with refreshed physical type
        Map<String, String> typesByPhysicalName = schemaDao.getColumnTypesInSchema(table.getTblLink(), schemaName);
        String actualType = typesByPhysicalName.get(column.getColLink());
        return MetadataMapper.toDto(column, actualType != null ? actualType : columnType);
    }

    private String normalizeTypeForAlter(String columnType) {
        // Normalize type for ALTER TABLE statement
        String upperType = columnType.toUpperCase().trim();
        if (upperType.startsWith("VARCHAR")) {
            if (upperType.contains("(")) {
                return columnType; // Keep as-is
            }
            return "VARCHAR(255)";
        }
        return switch (upperType) {
            case "INTEGER", "INT" -> "INTEGER";
            case "BIGINT", "LONG" -> "BIGINT";
            case "TEXT" -> "TEXT";
            case "BOOLEAN", "BOOL" -> "BOOLEAN";
            case "DATE" -> "DATE";
            case "TIMESTAMP" -> "TIMESTAMP";
            case "DECIMAL", "NUMERIC" -> "DECIMAL";
            case "DOUBLE" -> "DOUBLE PRECISION";
            case "FLOAT", "REAL" -> "REAL";
            default -> columnType;
        };
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
    public List<TableMetadataDto> getAllTables(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            schemaName = "public";
        }

        log.info("Fetching all tables from schema: {} ordered by most recently updated or created", schemaName);

        // Use SchemaDao to query schema-specific metadata tables
        List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schemaName);

        return tables.stream()
                .map(MetadataMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TableMetadataDto getTableById(Long tableId, String schemaName) {
        log.info("Fetching table with ID: {} from schema: {}", tableId, schemaName);

        BaseReferenceTable table;
        if (schemaName != null && !schemaName.isBlank()) {
            // Query from specific schema
            List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schemaName);
            table = tables.stream()
                    .filter(t -> t.getId().equals(tableId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId + " in schema: " + schemaName));
        } else {
            // Search across all available schemas
            table = findTableAcrossSchemas(tableId);
        }
        return MetadataMapper.toDto(table);
    }

    private BaseReferenceTable findTableAcrossSchemas(Long tableId) {
        // First try the default schema (public)
        try {
            List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema("public");
            BaseReferenceTable table = tables.stream()
                    .filter(t -> t.getId().equals(tableId))
                    .findFirst()
                    .orElse(null);
            if (table != null) {
                return table;
            }
        } catch (Exception e) {
            log.debug("Table not found in public schema, searching other schemas", e);
        }

        // Search in other schemas (dmgr, etc.)
        List<String> schemas = migrationProperties.getAvailableSchemas();
        for (String schema : schemas) {
            if ("public".equals(schema)) {
                continue; // Already checked
            }
            try {
                List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schema);
                BaseReferenceTable table = tables.stream()
                        .filter(t -> t.getId().equals(tableId))
                        .findFirst()
                        .orElse(null);
                if (table != null) {
                    return table;
                }
            } catch (Exception e) {
                log.debug("Table not found in schema: {}", schema, e);
            }
        }

        throw new IllegalArgumentException("Table not found with ID: " + tableId);
    }

    private String findSchemaForTable(Long tableId) {
        // Search across all available schemas to find which one contains this table
        List<String> schemas = migrationProperties.getAvailableSchemas();
        for (String schema : schemas) {
            try {
                List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schema);
                boolean found = tables.stream().anyMatch(t -> t.getId().equals(tableId));
                if (found) {
                    return schema;
                }
            } catch (Exception e) {
                log.debug("Error searching in schema: {}", schema, e);
            }
        }
        return "public"; // Default fallback
    }

    // NOTE: JOOQ queries for schema-aware metadata access live in SchemaDao.

    @Override
    @Transactional(readOnly = true)
    public List<ColumnMetadataDto> getColumnsByTableId(Long tableId, String schemaName) {
        final String finalSchemaName = (schemaName == null || schemaName.isBlank()) ? "public" : schemaName;
        log.info("Fetching columns for table ID: {} from schema: {}", tableId, finalSchemaName);

        // Get table from the specified schema
        List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(finalSchemaName);
        BaseReferenceTable table = tables.stream()
                .filter(t -> t.getId().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Table not found with ID: " + tableId + " in schema: " + finalSchemaName));
        
        // Get column types + columns from SchemaDao (keeps unit tests simple; SQL covered by integration tests)
        Map<String, String> typesByPhysicalName = schemaDao.getColumnTypesInSchema(table.getTblLink(), finalSchemaName);
        List<BaseColumnMap> columns = schemaDao.getColumnsFromSchema(tableId, finalSchemaName);

        return columns.stream()
                .map(col -> MetadataMapper.toDto(col, typesByPhysicalName.get(col.getColLink())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTable(Long tableId) {
        log.info("Deleting table with ID: {}", tableId);

        BaseReferenceTable table = findTableAcrossSchemas(tableId);
        String physicalTableName = table.getTblLink();
        String schemaName = findSchemaForTable(tableId);

        // Ensure foreign key constraints have CASCADE (for existing schemas)
        schemaDao.upgradeMetadataConstraints(schemaName);

        // Drop physical table using JOOQ DAO (from the schema where it exists)
        if (schemaDao.tableExistsInSchema(physicalTableName, schemaName)) {
            // Use raw SQL to drop table in specific schema
            String dropSql = String.format("DROP TABLE IF EXISTS %s.%s CASCADE",
                    quoteIdentifier(schemaName), quoteIdentifier(physicalTableName));
            dsl.execute(dropSql);
        }

        // Delete column metadata first (as a fallback in case CASCADE constraint doesn't exist)
        String deleteColumnsSql = String.format("DELETE FROM %s.base_column_map WHERE tbl_id = ?",
                quoteIdentifier(schemaName));
        int deletedColumns = dsl.execute(deleteColumnsSql, tableId);
        log.debug("Deleted {} column metadata entries for table ID {}", deletedColumns, tableId);

        // Delete metadata from the schema's base_reference_table
        String deleteSql = String.format("DELETE FROM %s.base_reference_table WHERE id = ?",
                quoteIdentifier(schemaName));
        dsl.execute(deleteSql, tableId);

        log.info("Successfully deleted table ID {} with physical name {} from schema {}", tableId, physicalTableName, schemaName);
    }

    @Override
    @Transactional
    public TableMetadataDto renameTable(Long tableId, String newLabel) {
        log.info("Renaming table ID {} to {}", tableId, newLabel);

        String schemaName = findSchemaForTable(tableId);

        // Update metadata in the schema's base_reference_table using JOOQ
        String updateSql = String.format(
                "UPDATE %s.base_reference_table SET tbl_label = ?, upd_usr = ?, upd_ts = CURRENT_TIMESTAMP WHERE id = ?",
                quoteIdentifier(schemaName));
        dsl.execute(updateSql, newLabel, "system", tableId);

        // Reload the updated table
        List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schemaName);
        BaseReferenceTable updated = tables.stream()
                .filter(t -> t.getId().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));

        log.info("Successfully renamed table ID {} to {} in schema {}", tableId, newLabel, schemaName);

        return MetadataMapper.toDto(updated);
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

    /**
     * Quote a SQL identifier to handle reserved keywords and special characters
     */
    private String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return "\"\"";
        }
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
