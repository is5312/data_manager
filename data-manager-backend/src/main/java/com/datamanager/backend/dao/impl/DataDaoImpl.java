package com.datamanager.backend.dao.impl;

import com.datamanager.backend.dao.DataDao;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JOOQ implementation of DataDao for dynamic data operations
 */
@Repository
@Transactional
@Slf4j
public class DataDaoImpl implements DataDao {

    private final DSLContext dsl;

    public DataDaoImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Map<String, Object> insertRow(String tableName, Map<String, Object> rowData) {
        log.info("Inserting row into table: {}", tableName);

        // Define audit values
        String currentUser = "system";
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // Build the insert query dynamically
        // Handle schema-qualified table names (e.g., "dmgr.tbl_test")
        org.jooq.Table<?> table;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            String schema = parts[0];
            String tableNameOnly = parts[1];
            table = DSL.table(DSL.name(schema, tableNameOnly));
        } else {
            table = DSL.table(DSL.name(tableName));
        }
        var insertStep = dsl.insertInto(table);

        List<Field<Object>> fields = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // explicit set audit columns to ensure we know the values to return
        fields.add(DSL.field(DSL.name("add_usr"), Object.class));
        values.add(currentUser);
        fields.add(DSL.field(DSL.name("add_ts"), Object.class));
        values.add(now);
        fields.add(DSL.field(DSL.name("upd_usr"), Object.class));
        values.add(currentUser);
        fields.add(DSL.field(DSL.name("upd_ts"), Object.class));
        values.add(now);

        // Filter out any audit columns that came from the input to avoid
        // duplication/override
        Map<String, Object> filteredData = rowData.entrySet().stream()
                .filter(e -> !isAuditColumn(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (Map.Entry<String, Object> entry : filteredData.entrySet()) {
            fields.add(DSL.field(DSL.name(entry.getKey()), Object.class));
            // Convert String values to appropriate types for common column names
            Object value = convertValueForColumn(entry.getKey(), entry.getValue());
            values.add(value);
        }

        // Execute and get the generated ID
        var result = insertStep
                .columns(fields)
                .values(values)
                .returningResult(DSL.field(DSL.name("id"), Long.class))
                .fetchOne();

        Long id = result != null ? result.value1() : null;

        Map<String, Object> resultMap = new java.util.HashMap<>();
        resultMap.put("id", id);
        resultMap.put("add_usr", currentUser);
        resultMap.put("add_ts", now.toString());
        resultMap.put("upd_usr", currentUser);
        resultMap.put("upd_ts", now.toString());

        log.info("Inserted row with ID: {} (audit columns computed)", id);
        return resultMap;
    }

    @Override
    public Map<String, Object> updateRow(String tableName, Long rowId, Map<String, Object> rowData) {
        log.info("Updating row {} in table: {} with data: {}", rowId, tableName, rowData);

        // Handle schema-qualified table names (e.g., "dmgr.tbl_test")
        Table<?> table;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            String schema = parts[0];
            String tableNameOnly = parts[1];
            table = DSL.table(DSL.name(schema, tableNameOnly));
        } else {
            table = DSL.table(DSL.name(tableName));
        }
        var query = dsl.updateQuery(table);

        // Filter out audit columns from user input (prevent override)
        Map<String, Object> filteredData = rowData.entrySet().stream()
                .filter(e -> !isAuditColumn(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (Map.Entry<String, Object> entry : filteredData.entrySet()) {
            query.addValue(DSL.field(DSL.name(entry.getKey()), Object.class), entry.getValue());
        }

        String updatedBy = "system";
        java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();

        // Automatically set upd_usr and upd_ts
        query.addValue(DSL.field(DSL.name("upd_usr"), String.class), updatedBy);
        query.addValue(DSL.field(DSL.name("upd_ts"), java.time.LocalDateTime.class), timestamp);

        query.addConditions(DSL.field(DSL.name("id"), Long.class).eq(rowId));

        int execute = query.execute();

        if (execute == 0) {
            log.error("Failed to update row {} in table {}: Row not found or no changes made", rowId, tableName);
            throw new RuntimeException("Failed to update row: Row " + rowId + " not found in table " + tableName);
        }

        // Return the updated row data + audit columns
        // No need to query again since we know what we just updated
        rowData.put("upd_usr", updatedBy);
        rowData.put("upd_ts", timestamp.toString()); // Convert to String for frontend consistency

        log.info("Successfully updated row {} in table: {} (audit columns updated in returned data)", rowId, tableName);
        return rowData;
    }

    @Override
    public void deleteRow(String tableName, Long rowId) {
        log.info("Deleting row {} from table: {}", rowId, tableName);

        // Handle schema-qualified table names (e.g., "dmgr.tbl_test")
        Table<?> table;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            String schema = parts[0];
            String tableNameOnly = parts[1];
            table = DSL.table(DSL.name(schema, tableNameOnly));
        } else {
            table = DSL.table(DSL.name(tableName));
        }

        dsl.deleteFrom(table)
                .where(DSL.field(DSL.name("id"), Long.class).eq(rowId))
                .execute();

        log.info("Deleted row {} from table: {}", rowId, tableName);
    }

    /**
     * Check if a column name is an audit column that should not be user-editable
     */
    private boolean isAuditColumn(String columnName) {
        return columnName != null && (columnName.equals("add_usr") ||
                columnName.equals("add_ts") ||
                columnName.equals("upd_usr") ||
                columnName.equals("upd_ts"));
    }

    /**
     * Convert String values to appropriate types based on column name patterns
     * This handles cases where gRPC sends all values as Strings
     */
    private Object convertValueForColumn(String columnName, Object value) {
        if (value == null) {
            return null;
        }
        
        // If already not a String, return as-is
        if (!(value instanceof String)) {
            return value;
        }
        
        String strValue = (String) value;
        
        // Convert based on common column name patterns
        String lowerName = columnName.toLowerCase();
        
        // Integer columns
        if (lowerName.equals("age") || lowerName.endsWith("_id") || 
            lowerName.contains("count") || lowerName.contains("quantity")) {
            try {
                return Integer.parseInt(strValue);
            } catch (NumberFormatException e) {
                log.warn("Could not convert '{}' to Integer for column '{}', keeping as String", strValue, columnName);
                return value;
            }
        }
        
        // Long/BIGINT columns
        if (lowerName.equals("id") && !lowerName.equals("guid")) {
            try {
                return Long.parseLong(strValue);
            } catch (NumberFormatException e) {
                log.warn("Could not convert '{}' to Long for column '{}', keeping as String", strValue, columnName);
                return value;
            }
        }
        
        // Boolean columns
        if (lowerName.startsWith("is_") || lowerName.startsWith("has_") || 
            lowerName.equals("active") || lowerName.equals("enabled")) {
            return Boolean.parseBoolean(strValue);
        }
        
        // Double/Float columns
        if (lowerName.contains("price") || lowerName.contains("amount") || 
            lowerName.contains("rate") || lowerName.contains("percent")) {
            try {
                return Double.parseDouble(strValue);
            } catch (NumberFormatException e) {
                log.warn("Could not convert '{}' to Double for column '{}', keeping as String", strValue, columnName);
                return value;
            }
        }
        
        // Default: return as String
        return value;
    }
}
