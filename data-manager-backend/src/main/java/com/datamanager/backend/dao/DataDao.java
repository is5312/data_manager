package com.datamanager.backend.dao;

import java.util.List;
import java.util.Map;

/**
 * DAO interface for data operations on physical tables using JOOQ
 */
public interface DataDao {

    /**
     * Query all rows from a physical table
     * 
     * @param tableName Physical table name
     * @return List of rows, where each row is a map of column name to value
     */
    List<Map<String, Object>> queryTableData(String tableName);

    /**
     * Insert a row into a physical table
     * 
     * @param tableName Physical table name
     * @param rowData   Map of column name to value
     * @return Map containing id and audit column values
     */
    Map<String, Object> insertRow(String tableName, Map<String, Object> rowData);

    /**
     * Update a row in a physical table
     * 
     * @param tableName Physical table name
     * @param rowId     The ID of the row to update
     * @param rowData   Map of column name to value
     * @return Map containing audit column values
     */
    Map<String, Object> updateRow(String tableName, Long rowId, Map<String, Object> rowData);

    /**
     * Delete a row from a physical table
     * 
     * @param tableName Physical table name
     * @param rowId     The ID of the row to delete
     */
    void deleteRow(String tableName, Long rowId);
}
