package com.datamanager.backend.dao.impl;

import com.datamanager.backend.dao.SchemaDao;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JOOQ implementation of SchemaDao for dynamic schema operations
 * Uses JOOQ DSL for type-safe DDL operations
 */
@Repository
@Slf4j
public class SchemaDaoImpl implements SchemaDao {

    private final DSLContext dsl;

    public SchemaDaoImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void createTable(String tableName) {
        log.info("Creating physical table: {}", tableName);

        String sanitizedTableName = sanitizeIdentifier(tableName);

        dsl.createTableIfNotExists(DSL.name(sanitizedTableName))
                .column(DSL.name("id"), SQLDataType.BIGINT.identity(true))
                .column(DSL.name("add_usr"), SQLDataType.VARCHAR(255).defaultValue(DSL.inline("system")))
                .column(DSL.name("add_ts"), SQLDataType.TIMESTAMP.defaultValue(DSL.currentTimestamp()))
                .column(DSL.name("upd_usr"), SQLDataType.VARCHAR(255))
                .column(DSL.name("upd_ts"), SQLDataType.TIMESTAMP)
                .constraint(DSL.primaryKey(DSL.name("id")))
                .execute();

        log.info("Successfully created table: {}", sanitizedTableName);
    }

    @Override
    public void addColumn(String tableName, String columnName, String columnType) {
        log.info("Adding column {} of type {} to table {}", columnName, columnType, tableName);

        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedColumnName = sanitizeIdentifier(columnName);

        // Parse column type - this is a simplified approach
        // In production, you'd want more robust type mapping
        var dataType = parseColumnType(columnType);

        dsl.alterTable(DSL.name(sanitizedTableName))
                .addColumn(DSL.name(sanitizedColumnName), dataType)
                .execute();

        log.info("Successfully added column {} to table {}", sanitizedColumnName, sanitizedTableName);
    }

    @Override
    public void removeColumn(String tableName, String columnName) {
        log.info("Removing column {} from table {}", columnName, tableName);

        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedColumnName = sanitizeIdentifier(columnName);

        dsl.alterTable(DSL.name(sanitizedTableName))
                .dropColumn(DSL.name(sanitizedColumnName))
                .execute();

        log.info("Successfully removed column {} from table {}", sanitizedColumnName, sanitizedTableName);
    }

    @Override
    public void dropTable(String tableName) {
        log.info("Dropping physical table: {}", tableName);

        String sanitizedTableName = sanitizeIdentifier(tableName);

        dsl.dropTableIfExists(DSL.table(DSL.name(sanitizedTableName)))
                .execute();

        log.info("Successfully dropped table: {}", sanitizedTableName);
    }

    @Override
    public boolean tableExists(String tableName) {
        String sanitizedTableName = sanitizeIdentifier(tableName);

        Integer count = dsl.selectCount()
                .from(DSL.table("information_schema.tables"))
                .where(DSL.field("table_schema").eq("public"))
                .and(DSL.field("table_name").eq(sanitizedTableName))
                .fetchOne(0, Integer.class);

        return count != null && count > 0;
    }

    @Override
    public void renameTable(String oldTableName, String newTableName) {
        log.info("Renaming table from {} to {}", oldTableName, newTableName);

        String sanitizedOldName = sanitizeIdentifier(oldTableName);
        String sanitizedNewName = sanitizeIdentifier(newTableName);

        dsl.alterTable(DSL.table(DSL.name(sanitizedOldName)))
                .renameTo(DSL.name(sanitizedNewName))
                .execute();

        log.info("Successfully renamed table from {} to {}", sanitizedOldName, sanitizedNewName);
    }

    @Override
    public void renameColumn(String tableName, String oldColumnName, String newColumnName) {
        log.info("Renaming column in table {} from {} to {}", tableName, oldColumnName, newColumnName);

        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedOldColumn = sanitizeIdentifier(oldColumnName);
        String sanitizedNewColumn = sanitizeIdentifier(newColumnName);

        dsl.alterTable(DSL.table(DSL.name(sanitizedTableName)))
                .renameColumn(DSL.field(DSL.name(sanitizedOldColumn)))
                .to(DSL.name(sanitizedNewColumn))
                .execute();

        log.info("Successfully renamed column in table {} from {} to {}",
                sanitizedTableName, sanitizedOldColumn, sanitizedNewColumn);
    }

    @Override
    public void alterColumnType(String tableName, String columnName, String columnType) {
        log.info("Altering column type in table {} for column {} to {}", tableName, columnName, columnType);

        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedColumnName = sanitizeIdentifier(columnName);
        String normalizedTypeSql = normalizeTypeSql(columnType);

        // Use raw SQL for broad Postgres compatibility; identifiers are sanitized and
        // type is whitelisted.
        String sql = String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s",
                quoteIdentifier(sanitizedTableName), quoteIdentifier(sanitizedColumnName), normalizedTypeSql);
        dsl.execute(sql);

        log.info("Successfully altered column type in table {} for column {} to {}",
                sanitizedTableName, sanitizedColumnName, normalizedTypeSql);
    }

    @Override
    public Map<String, String> getColumnTypes(String tableName) {
        String sanitizedTableName = sanitizeIdentifier(tableName);

        Result<Record4<String, String, Integer, Integer>> rows = dsl
                .select(
                        DSL.field(DSL.name("column_name"), String.class),
                        DSL.field(DSL.name("data_type"), String.class),
                        DSL.field(DSL.name("character_maximum_length"), Integer.class),
                        DSL.field(DSL.name("numeric_scale"), Integer.class))
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema")).eq("public"))
                .and(DSL.field(DSL.name("table_name")).eq(sanitizedTableName))
                .orderBy(DSL.field(DSL.name("ordinal_position")).asc())
                .fetch();

        Map<String, String> result = new LinkedHashMap<>();
        for (Record4<String, String, Integer, Integer> r : rows) {
            String columnName = r.value1();
            String dataType = r.value2();
            Integer charLen = r.value3();
            Integer numericScale = r.value4();

            if (columnName == null || dataType == null)
                continue;
            result.put(columnName, normalizeInformationSchemaType(dataType, charLen, numericScale));
        }
        return result;
    }

    private String normalizeInformationSchemaType(String dataType, Integer charLen, Integer numericScale) {
        String dt = dataType.trim().toLowerCase();
        return switch (dt) {
            case "character varying", "varchar" -> charLen != null ? "VARCHAR(" + charLen + ")" : "VARCHAR";
            case "text" -> "TEXT";
            case "integer", "int4" -> "INTEGER";
            case "bigint", "int8" -> "BIGINT";
            case "boolean" -> "BOOLEAN";
            case "date" -> "DATE";
            case "timestamp without time zone" -> "TIMESTAMP";
            case "timestamp with time zone" -> "TIMESTAMPTZ";
            case "numeric", "decimal" -> {
                // Precision is not reliably exposed here; keep it generic.
                yield (numericScale != null) ? "DECIMAL" : "DECIMAL";
            }
            case "double precision" -> "DOUBLE";
            case "real" -> "REAL";
            default -> dataType.toUpperCase();
        };
    }

    private String normalizeTypeSql(String columnType) {
        // Validate against supported types (reuse parseColumnType), then return
        // normalized SQL snippet.
        // This avoids SQL injection through type strings.
        parseColumnType(columnType);

        String upperType = columnType.toUpperCase().trim();
        if (upperType.startsWith("VARCHAR")) {
            if (upperType.contains("(")) {
                String lengthStr = upperType.substring(
                        upperType.indexOf("(") + 1,
                        upperType.indexOf(")"));
                int length = Integer.parseInt(lengthStr);
                return "VARCHAR(" + length + ")";
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
            default -> throw new IllegalArgumentException("Unsupported column type: " + columnType);
        };
    }

    private String quoteIdentifier(String identifier) {
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Sanitize SQL identifier to prevent SQL injection
     */
    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }

        // Allow only alphanumeric and underscore
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }

        return identifier;
    }

    /**
     * Parse column type string to JOOQ DataType
     * This is a simplified implementation - extend as needed
     */
    private org.jooq.DataType<?> parseColumnType(String columnType) {
        if (columnType == null || columnType.isBlank()) {
            throw new IllegalArgumentException("Column type cannot be null or blank");
        }

        String upperType = columnType.toUpperCase().trim();

        // Handle VARCHAR with length
        if (upperType.startsWith("VARCHAR")) {
            if (upperType.contains("(")) {
                String lengthStr = upperType.substring(
                        upperType.indexOf("(") + 1,
                        upperType.indexOf(")"));
                int length = Integer.parseInt(lengthStr);
                return SQLDataType.VARCHAR(length);
            }
            return SQLDataType.VARCHAR(255);
        }

        // Handle common types
        return switch (upperType) {
            case "INTEGER", "INT" -> SQLDataType.INTEGER;
            case "BIGINT", "LONG" -> SQLDataType.BIGINT;
            case "TEXT" -> SQLDataType.CLOB;
            case "BOOLEAN", "BOOL" -> SQLDataType.BOOLEAN;
            case "DATE" -> SQLDataType.DATE;
            case "TIMESTAMP" -> SQLDataType.TIMESTAMP;
            case "DECIMAL", "NUMERIC" -> SQLDataType.DECIMAL;
            case "DOUBLE" -> SQLDataType.DOUBLE;
            case "FLOAT", "REAL" -> SQLDataType.REAL;
            default -> throw new IllegalArgumentException("Unsupported column type: " + columnType);
        };
    }
}
