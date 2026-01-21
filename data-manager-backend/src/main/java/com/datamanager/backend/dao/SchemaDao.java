package com.datamanager.backend.dao;

/**
 * DAO interface for dynamic schema operations using JOOQ
 * This handles operations on physical tables (not metadata)
 */
public interface SchemaDao {

    /**
     * Create a physical table with the given name
     * 
     * @param tableName Physical table name
     */
    void createTable(String tableName);

    /**
     * Add a column to a physical table
     * 
     * @param tableName  Physical table name
     * @param columnName Physical column name
     * @param columnType SQL column type (e.g., VARCHAR(255), INTEGER)
     */
    void addColumn(String tableName, String columnName, String columnType);

    /**
     * Remove a column from a physical table
     * 
     * @param tableName  Physical table name
     * @param columnName Physical column name
     */
    void removeColumn(String tableName, String columnName);

    /**
     * Drop a physical table
     * 
     * @param tableName Physical table name
     */
    void dropTable(String tableName);

    /**
     * Check if a physical table exists
     * 
     * @param tableName Physical table name
     * @return true if table exists, false otherwise
     */
    boolean tableExists(String tableName);

    /**
     * Check if a physical table exists in a specific schema
     *
     * @param tableName Physical table name
     * @param schemaName Schema name
     * @return true if table exists, false otherwise
     */
    boolean tableExistsInSchema(String tableName, String schemaName);

    /**
     * Rename a physical table
     * 
     * @param oldTableName Current table name
     * @param newTableName New table name
     */
    void renameTable(String oldTableName, String newTableName);

    /**
     * Rename a column in a physical table
     * 
     * @param tableName     Physical table name
     * @param oldColumnName Current column name
     * @param newColumnName New column name
     */
    void renameColumn(String tableName, String oldColumnName, String newColumnName);

    /**
     * Alter a physical column's type (e.g., VARCHAR -> BIGINT).
     *
     * @param tableName  Physical table name
     * @param columnName Physical column name
     * @param columnType New SQL type (whitelisted by implementation)
     */
    void alterColumnType(String tableName, String columnName, String columnType);

    /**
     * Get physical column types for a table (keyed by physical column name)
     *
     * @param tableName Physical table name
     * @return Map of column_name -> type string (e.g. VARCHAR(255), BIGINT, TIMESTAMP)
     */
    java.util.Map<String, String> getColumnTypes(String tableName);

    /**
     * Get physical column types for a table in a specific schema (keyed by physical column name)
     *
     * @param tableName   Physical table name
     * @param schemaName  Schema name
     * @return Map of column_name -> type string (e.g. VARCHAR(255), BIGINT, TIMESTAMP)
     */
    java.util.Map<String, String> getColumnTypesInSchema(String tableName, String schemaName);

    /**
     * Query base_column_map from a specific schema for a table ID.
     * This is metadata access and intentionally returns entity-like objects to keep
     * service-level logic testable without mocking JOOQ fluent APIs.
     *
     * @param tableId     Table id (tbl_id)
     * @param schemaName  Schema name containing base_column_map
     * @return List of BaseColumnMap rows for the table
     */
    java.util.List<com.datamanager.backend.entity.BaseColumnMap> getColumnsFromSchema(Long tableId, String schemaName);

    /**
     * Create a PostgreSQL schema if it doesn't exist
     *
     * @param schemaName Schema name to create
     */
    void createSchema(String schemaName);

    /**
     * Check if a schema exists
     *
     * @param schemaName Schema name to check
     * @return true if schema exists, false otherwise
     */
    boolean schemaExists(String schemaName);

    /**
     * Create a physical table in the specified schema
     *
     * @param tableName Physical table name
     * @param schemaName Target schema name
     */
    void createTableInSchema(String tableName, String schemaName);

    /**
     * Create metadata tables (base_reference_table and base_column_map) in the specified schema
     *
     * @param schemaName Target schema name
     */
    void createMetadataTablesInSchema(String schemaName);

    /**
     * Create a trigger on source table to sync changes to target table
     *
     * @param triggerName Name of the trigger
     * @param sourceTable Source table name
     * @param sourceSchema Source schema name
     * @param targetTable Target table name
     * @param targetSchema Target schema name
     * @param triggerType Type of trigger: INSERT, UPDATE, or DELETE
     */
    void createTrigger(String triggerName, String sourceTable, String sourceSchema, String targetTable, String targetSchema, String triggerType);

    /**
     * Drop a trigger by name
     *
     * @param triggerName Name of the trigger to drop
     * @param tableName Table name where trigger exists
     * @param schemaName Schema name where trigger exists
     */
    void dropTrigger(String triggerName, String tableName, String schemaName);

    /**
     * Bulk copy data from source table to target table across schemas
     *
     * @param sourceTable Source table name
     * @param sourceSchema Source schema name
     * @param targetTable Target table name
     * @param targetSchema Target schema name
     */
    void bulkCopyTableData(String sourceTable, String sourceSchema, String targetTable, String targetSchema);

    /**
     * Get table structure (column definitions) from a table in a specific schema
     *
     * @param tableName Table name
     * @param schemaName Schema name
     * @return Map of column_name -> column_definition (for CREATE TABLE)
     */
    java.util.Map<String, String> getTableStructure(String tableName, String schemaName);

    /**
     * Query metadata tables from a specific schema and return BaseReferenceTable entities
     *
     * @param schemaName Schema name to query
     * @return List of BaseReferenceTable entities from the schema's metadata tables
     */
    java.util.List<com.datamanager.backend.entity.BaseReferenceTable> getTablesFromSchema(String schemaName);

    /**
     * Upgrade foreign key constraints in metadata tables to include ON DELETE CASCADE
     * This is useful for migrating existing schemas that were created before CASCADE was added
     *
     * @param schemaName Schema name to upgrade
     */
    void upgradeMetadataConstraints(String schemaName);
}
