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
}
