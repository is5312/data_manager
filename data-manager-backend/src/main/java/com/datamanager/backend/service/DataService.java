package com.datamanager.backend.service;

import java.util.Map;

/**
 * Service interface for data operations on physical tables
 * Handles row-level operations (insert, update, delete) and response building
 */
public interface DataService {

    /**
     * Insert a new row into a table (default public schema)
     * 
     * @param tableId Logical table ID
     * @param rowData Map of column name to value
     * @return Map containing id and audit column values (add_usr, add_ts, upd_usr, upd_ts)
     * @throws IllegalArgumentException if table not found
     */
    Map<String, Object> insertRow(Long tableId, Map<String, Object> rowData);

    /**
     * Insert a new row into a table in a specific schema
     * 
     * @param tableId Logical table ID
     * @param schema Schema name (e.g., "public", "dmgr")
     * @param rowData Map of column name to value
     * @return Map containing id and audit column values (add_usr, add_ts, upd_usr, upd_ts)
     * @throws IllegalArgumentException if table not found
     */
    Map<String, Object> insertRow(Long tableId, String schema, Map<String, Object> rowData);

    /**
     * Update a row in a table (default public schema)
     * 
     * @param tableId Logical table ID
     * @param rowId The ID of the row to update
     * @param rowData Map of column name to value
     * @return Map containing audit column values (add_usr, add_ts, upd_usr, upd_ts)
     * @throws IllegalArgumentException if table not found
     */
    Map<String, Object> updateRow(Long tableId, Long rowId, Map<String, Object> rowData);

    /**
     * Update a row in a table in a specific schema
     * 
     * @param tableId Logical table ID
     * @param schema Schema name (e.g., "public", "dmgr")
     * @param rowId The ID of the row to update
     * @param rowData Map of column name to value
     * @return Map containing audit column values (add_usr, add_ts, upd_usr, upd_ts)
     * @throws IllegalArgumentException if table not found
     */
    Map<String, Object> updateRow(Long tableId, String schema, Long rowId, Map<String, Object> rowData);

    /**
     * Delete a row from a table (default public schema)
     * 
     * @param tableId Logical table ID
     * @param rowId The ID of the row to delete
     * @throws IllegalArgumentException if table not found
     */
    void deleteRow(Long tableId, Long rowId);

    /**
     * Delete a row from a table in a specific schema
     * 
     * @param tableId Logical table ID
     * @param schema Schema name (e.g., "public", "dmgr")
     * @param rowId The ID of the row to delete
     * @throws IllegalArgumentException if table not found
     */
    void deleteRow(Long tableId, String schema, Long rowId);
}

