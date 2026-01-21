package com.datamanager.backend.service;

import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.dto.ColumnMetadataDto;

import java.util.List;

/**
 * Service interface for table metadata operations
 * Orchestrates JPA repositories for metadata and JOOQ DAO for dynamic queries
 */
public interface TableMetadataService {

    /**
     * Create a new logical table with physical table
     * 
     * @param tableLabel Logical table name/label
     * @param deploymentType Deployment type (RUN_TIME or DESIGN_TIME)
     * @return Created table metadata
     */
    TableMetadataDto createTable(String tableLabel, String deploymentType);

    /**
     * Add a column to a logical table
     * 
     * @param tableId     Logical table ID
     * @param columnLabel Logical column name/label
     * @param columnType  SQL column type
     * @return Created column metadata
     */
    ColumnMetadataDto addColumn(Long tableId, String columnLabel, String columnType);

    /**
     * Change the SQL type of an existing column (physical table ALTER COLUMN TYPE).
     *
     * @param tableId    Logical table id
     * @param columnId   Column metadata id
     * @param columnType New SQL type
     * @return Updated column metadata
     */
    ColumnMetadataDto changeColumnType(Long tableId, Long columnId, String columnType);

    /**
     * Remove a column from a logical table
     * 
     * @param tableId  Logical table ID
     * @param columnId Column ID
     */
    void removeColumn(Long tableId, Long columnId);

    /**
     * Get all logical tables
     * 
     * @param schemaName Optional schema name to filter by (defaults to "public" if null)
     * @return List of table metadata
     */
    List<TableMetadataDto> getAllTables(String schemaName);

    /**
     * Get table by ID
     * 
     * @param tableId Table ID
     * @param schemaName Optional schema name to search in (searches all schemas if null)
     * @return Table metadata
     */
    TableMetadataDto getTableById(Long tableId, String schemaName);

    /**
     * Get all columns for a table
     * 
     * @param tableId Table ID
     * @param schemaName Optional schema name to search in (searches all schemas if null)
     * @return List of column metadata
     */
    List<ColumnMetadataDto> getColumnsByTableId(Long tableId, String schemaName);

    /**
     * Delete a logical table (and its physical table)
     * 
     * @param tableId Table ID
     */
    void deleteTable(Long tableId);

    /**
     * Rename a logical table
     * 
     * @param tableId  Table ID
     * @param newLabel New logical label
     * @return Updated table metadata
     */
    TableMetadataDto renameTable(Long tableId, String newLabel);
}
