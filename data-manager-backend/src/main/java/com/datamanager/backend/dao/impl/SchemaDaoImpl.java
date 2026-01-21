package com.datamanager.backend.dao.impl;

import com.datamanager.backend.dao.SchemaDao;
import com.datamanager.backend.entity.BaseColumnMap;
import com.datamanager.backend.entity.BaseReferenceTable;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        return tableExistsInSchema(tableName, "public");
    }

    @Override
    public boolean tableExistsInSchema(String tableName, String schemaName) {
        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        Integer count = dsl.selectCount()
                .from(DSL.table("information_schema.tables"))
                .where(DSL.field("table_schema").eq(sanitizedSchemaName))
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

    @Override
    public Map<String, String> getColumnTypesInSchema(String tableName, String schemaName) {
        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        Result<Record4<String, String, Integer, Integer>> rows = dsl
                .select(
                        DSL.field(DSL.name("column_name"), String.class),
                        DSL.field(DSL.name("data_type"), String.class),
                        DSL.field(DSL.name("character_maximum_length"), Integer.class),
                        DSL.field(DSL.name("numeric_scale"), Integer.class))
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema")).eq(sanitizedSchemaName))
                .and(DSL.field(DSL.name("table_name")).eq(sanitizedTableName))
                .orderBy(DSL.field(DSL.name("ordinal_position")).asc())
                .fetch();

        Map<String, String> result = new LinkedHashMap<>();
        for (Record4<String, String, Integer, Integer> r : rows) {
            String columnName = r.value1();
            String dataType = r.value2();
            Integer charLen = r.value3();
            Integer numericScale = r.value4();

            if (columnName == null || dataType == null) {
                continue;
            }
            result.put(columnName, normalizeInformationSchemaType(dataType, charLen, numericScale));
        }
        return result;
    }

    @Override
    public List<BaseColumnMap> getColumnsFromSchema(Long tableId, String schemaName) {
        log.info("Getting columns for table id {} from schema {}", tableId, schemaName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        Result<?> rows = dsl.select()
                .from(DSL.table(DSL.name(sanitizedSchemaName, "base_column_map")))
                .where(DSL.field(DSL.name("tbl_id")).eq(tableId))
                .orderBy(DSL.field(DSL.name("id")).asc())
                .fetch();

        List<BaseColumnMap> columns = new ArrayList<>();
        for (org.jooq.Record row : rows) {
            BaseColumnMap column = new BaseColumnMap();
            column.setId(row.get(DSL.field(DSL.name("id")), Long.class));
            column.setTblLink(row.get(DSL.field(DSL.name("tbl_link")), String.class));
            column.setColLabel(row.get(DSL.field(DSL.name("col_label")), String.class));
            column.setColLink(row.get(DSL.field(DSL.name("col_link")), String.class));
            column.setDescription(row.get(DSL.field(DSL.name("description")), String.class));
            column.setVersionNo(row.get(DSL.field(DSL.name("version_no")), Integer.class));
            column.setAddTs(row.get(DSL.field(DSL.name("add_ts")), LocalDateTime.class));
            column.setAddUsr(row.get(DSL.field(DSL.name("add_usr")), String.class));
            column.setUpdTs(row.get(DSL.field(DSL.name("upd_ts")), LocalDateTime.class));
            column.setUpdUsr(row.get(DSL.field(DSL.name("upd_usr")), String.class));
            columns.add(column);
        }
        return columns;
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

    @Override
    public void createSchema(String schemaName) {
        log.info("Creating schema: {}", schemaName);
        sanitizeIdentifier(schemaName);

        dsl.createSchemaIfNotExists(DSL.name(schemaName)).execute();
        log.info("Successfully created schema: {}", schemaName);
    }

    @Override
    public boolean schemaExists(String schemaName) {
        sanitizeIdentifier(schemaName);

        Integer count = dsl.selectCount()
                .from(DSL.table("information_schema.schemata"))
                .where(DSL.field("schema_name").eq(schemaName))
                .fetchOne(0, Integer.class);

        return count != null && count > 0;
    }

    @Override
    public void createTableInSchema(String tableName, String schemaName) {
        log.info("Creating physical table {} in schema {}", tableName, schemaName);

        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        dsl.createTableIfNotExists(DSL.table(DSL.name(sanitizedSchemaName, sanitizedTableName)))
                .column(DSL.name("id"), SQLDataType.BIGINT.identity(true))
                .column(DSL.name("add_usr"), SQLDataType.VARCHAR(255).defaultValue(DSL.inline("system")))
                .column(DSL.name("add_ts"), SQLDataType.TIMESTAMP.defaultValue(DSL.currentTimestamp()))
                .column(DSL.name("upd_usr"), SQLDataType.VARCHAR(255))
                .column(DSL.name("upd_ts"), SQLDataType.TIMESTAMP)
                .constraint(DSL.primaryKey(DSL.name("id")))
                .execute();

        log.info("Successfully created table {} in schema {}", sanitizedTableName, sanitizedSchemaName);
    }

    @Override
    public void createMetadataTablesInSchema(String schemaName) {
        log.info("Creating metadata tables in schema: {}", schemaName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        // Create base_reference_table
        dsl.createTableIfNotExists(DSL.table(DSL.name(sanitizedSchemaName, "base_reference_table")))
                .column(DSL.name("id"), SQLDataType.INTEGER.identity(true))
                .column(DSL.name("tbl_label"), SQLDataType.VARCHAR(255).nullable(false))
                .column(DSL.name("tbl_link"), SQLDataType.VARCHAR(255).nullable(false))
                .column(DSL.name("description"), SQLDataType.CLOB)
                .column(DSL.name("version_no"), SQLDataType.INTEGER.defaultValue(DSL.inline(1)))
                .column(DSL.name("deployment_type"), SQLDataType.VARCHAR(20).defaultValue(DSL.inline("DESIGN_TIME")))
                .column(DSL.name("add_ts"), SQLDataType.TIMESTAMP.defaultValue(DSL.currentTimestamp()))
                .column(DSL.name("add_usr"), SQLDataType.VARCHAR(255))
                .column(DSL.name("upd_ts"), SQLDataType.TIMESTAMP)
                .column(DSL.name("upd_usr"), SQLDataType.VARCHAR(255))
                .constraint(DSL.primaryKey(DSL.name("id")))
                .constraint(DSL.unique(DSL.name("tbl_link")))
                .execute();

        // Create base_column_map
        dsl.createTableIfNotExists(DSL.table(DSL.name(sanitizedSchemaName, "base_column_map")))
                .column(DSL.name("id"), SQLDataType.INTEGER.identity(true))
                .column(DSL.name("tbl_id"), SQLDataType.INTEGER.nullable(false))
                .column(DSL.name("tbl_link"), SQLDataType.VARCHAR(255).nullable(false))
                .column(DSL.name("col_label"), SQLDataType.VARCHAR(255).nullable(false))
                .column(DSL.name("col_link"), SQLDataType.VARCHAR(255).nullable(false))
                .column(DSL.name("description"), SQLDataType.CLOB)
                .column(DSL.name("version_no"), SQLDataType.INTEGER.defaultValue(DSL.inline(1)))
                .column(DSL.name("add_ts"), SQLDataType.TIMESTAMP.defaultValue(DSL.currentTimestamp()))
                .column(DSL.name("add_usr"), SQLDataType.VARCHAR(255))
                .column(DSL.name("upd_ts"), SQLDataType.TIMESTAMP)
                .column(DSL.name("upd_usr"), SQLDataType.VARCHAR(255))
                .constraint(DSL.primaryKey(DSL.name("id")))
                .execute();

        // Add foreign key constraint with ON DELETE CASCADE
        String schemaQuoted = quoteIdentifier(sanitizedSchemaName);
        String columnMapQuoted = quoteIdentifier("base_column_map");
        String refTableQuoted = quoteIdentifier("base_reference_table");
        
        // Check if constraint already exists and whether it has ON DELETE CASCADE
        var constraintInfo = dsl.select(
                DSL.field("pg_constraint.confdeltype", String.class))
                .from(DSL.table("pg_constraint"))
                .join(DSL.table("pg_class")).on(DSL.field("pg_constraint.conrelid").eq(DSL.field("pg_class.oid")))
                .join(DSL.table("pg_namespace")).on(DSL.field("pg_class.relnamespace").eq(DSL.field("pg_namespace.oid")))
                .where(DSL.field("pg_constraint.conname").eq("fk_base_column_map_tbl_id"))
                .and(DSL.field("pg_namespace.nspname").eq(sanitizedSchemaName))
                .and(DSL.field("pg_class.relname").eq("base_column_map"))
                .fetchOne();
        
        boolean needsRecreate = false;
        if (constraintInfo != null) {
            String deleteAction = constraintInfo.value1();
            // 'a' = NO ACTION, 'r' = RESTRICT, 'c' = CASCADE, 'n' = SET NULL, 'd' = SET DEFAULT
            if (!"c".equals(deleteAction)) {
                log.info("Foreign key constraint exists but without CASCADE, will recreate it");
                needsRecreate = true;
                // Drop existing constraint
                String dropFkSql = String.format(
                        "ALTER TABLE %s.%s DROP CONSTRAINT IF EXISTS fk_base_column_map_tbl_id",
                        schemaQuoted,
                        columnMapQuoted);
                dsl.execute(dropFkSql);
            } else {
                log.debug("Foreign key constraint fk_base_column_map_tbl_id already exists with CASCADE in schema {}", sanitizedSchemaName);
            }
        }
        
        if (constraintInfo == null || needsRecreate) {
            // Constraint doesn't exist or needs to be recreated, create it with ON DELETE CASCADE
            String fkSql = String.format(
                    "ALTER TABLE %s.%s ADD CONSTRAINT fk_base_column_map_tbl_id " +
                    "FOREIGN KEY (tbl_id) REFERENCES %s.%s(id) ON DELETE CASCADE",
                    schemaQuoted,
                    columnMapQuoted,
                    schemaQuoted,
                    refTableQuoted);
            try {
                dsl.execute(fkSql);
                log.info("Created foreign key constraint with ON DELETE CASCADE for schema {}", sanitizedSchemaName);
            } catch (Exception e) {
                // Log but don't fail - constraint might have been created by another transaction
                log.debug("Could not create foreign key constraint (may have been created concurrently): {}", e.getMessage());
            }
        }

        // Create base_reference_table_bak for rollback support
        String bakTableSql = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (" +
            "id BIGINT, " +
            "tbl_label VARCHAR(255), " +
            "tbl_link VARCHAR(255), " +
            "description TEXT, " +
            "version_no INTEGER, " +
            "deployment_type VARCHAR(50), " +
            "add_ts TIMESTAMP, " +
            "add_usr VARCHAR(255), " +
            "upd_ts TIMESTAMP, " +
            "upd_usr VARCHAR(255), " +
            "backup_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",
            schemaQuoted,
            quoteIdentifier("base_reference_table_bak"));
        dsl.execute(bakTableSql);

        // Create base_column_map_bak for rollback support  
        String bakColumnMapSql = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (" +
            "id BIGINT, " +
            "tbl_id BIGINT, " +
            "tbl_link VARCHAR(255), " +
            "col_label VARCHAR(255), " +
            "col_link VARCHAR(255), " +
            "description TEXT, " +
            "version_no INTEGER, " +
            "add_ts TIMESTAMP, " +
            "add_usr VARCHAR(255), " +
            "upd_ts TIMESTAMP, " +
            "upd_usr VARCHAR(255), " +
            "backup_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",
            schemaQuoted,
            quoteIdentifier("base_column_map_bak"));
        dsl.execute(bakColumnMapSql);

        log.info("Successfully created metadata tables (including backup tables) in schema: {}", sanitizedSchemaName);
    }

    @Override
    public void createTrigger(String triggerName, String sourceTable, String sourceSchema, String targetTable, String targetSchema, String triggerType) {
        log.info("Creating {} trigger {} on {}.{} to sync to {}.{}", triggerType, triggerName, sourceSchema, sourceTable, targetSchema, targetTable);

        String sanitizedTriggerName = sanitizeIdentifier(triggerName);
        String sanitizedSourceTable = sanitizeIdentifier(sourceTable);
        String sanitizedSourceSchema = sanitizeIdentifier(sourceSchema);
        String sanitizedTargetTable = sanitizeIdentifier(targetTable);
        String sanitizedTargetSchema = sanitizeIdentifier(targetSchema);

        // Create trigger function based on type
        String functionName = "migrate_" + triggerType.toLowerCase() + "_handler_" + sanitizedTriggerName;
        String functionBody = switch (triggerType.toUpperCase()) {
            case "INSERT" -> String.format(
                    "CREATE OR REPLACE FUNCTION %s() RETURNS TRIGGER AS $$ " +
                    "BEGIN " +
                    "  INSERT INTO %s.%s SELECT NEW.*; " +
                    "  RETURN NEW; " +
                    "END; " +
                    "$$ LANGUAGE plpgsql;",
                    quoteIdentifier(functionName),
                    quoteIdentifier(sanitizedTargetSchema),
                    quoteIdentifier(sanitizedTargetTable));
            case "UPDATE" -> {
                // For UPDATE, we need to delete and re-insert to handle all columns dynamically
                // This is simpler than trying to build dynamic UPDATE statements
                yield String.format(
                        "CREATE OR REPLACE FUNCTION %s() RETURNS TRIGGER AS $$ " +
                        "BEGIN " +
                        "  DELETE FROM %s.%s WHERE id = NEW.id; " +
                        "  INSERT INTO %s.%s SELECT NEW.*; " +
                        "  RETURN NEW; " +
                        "END; " +
                        "$$ LANGUAGE plpgsql;",
                        quoteIdentifier(functionName),
                        quoteIdentifier(sanitizedTargetSchema),
                        quoteIdentifier(sanitizedTargetTable),
                        quoteIdentifier(sanitizedTargetSchema),
                        quoteIdentifier(sanitizedTargetTable));
            }
            case "DELETE" -> String.format(
                    "CREATE OR REPLACE FUNCTION %s() RETURNS TRIGGER AS $$ " +
                    "BEGIN " +
                    "  DELETE FROM %s.%s WHERE id = OLD.id; " +
                    "  RETURN OLD; " +
                    "END; " +
                    "$$ LANGUAGE plpgsql;",
                    quoteIdentifier(functionName),
                    quoteIdentifier(sanitizedTargetSchema),
                    quoteIdentifier(sanitizedTargetTable));
            default -> throw new IllegalArgumentException("Unsupported trigger type: " + triggerType);
        };

        // Execute function creation
        dsl.execute(functionBody);

        // Create trigger
        String triggerSql = String.format(
                "CREATE TRIGGER %s " +
                "AFTER %s ON %s.%s " +
                "FOR EACH ROW EXECUTE FUNCTION %s();",
                quoteIdentifier(sanitizedTriggerName),
                triggerType.toUpperCase(),
                quoteIdentifier(sanitizedSourceSchema),
                quoteIdentifier(sanitizedSourceTable),
                quoteIdentifier(functionName));

        dsl.execute(triggerSql);

        log.info("Successfully created {} trigger {} on {}.{}", triggerType, sanitizedTriggerName, sanitizedSourceSchema, sanitizedSourceTable);
    }

    @Override
    public void dropTrigger(String triggerName, String tableName, String schemaName) {
        log.info("Dropping trigger {} from {}.{}", triggerName, schemaName, tableName);

        String sanitizedTriggerName = sanitizeIdentifier(triggerName);
        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        String sql = String.format("DROP TRIGGER IF EXISTS %s ON %s.%s;",
                quoteIdentifier(sanitizedTriggerName),
                quoteIdentifier(sanitizedSchemaName),
                quoteIdentifier(sanitizedTableName));

        dsl.execute(sql);

        log.info("Successfully dropped trigger {} from {}.{}", sanitizedTriggerName, sanitizedSchemaName, sanitizedTableName);
    }

    @Override
    public void bulkCopyTableData(String sourceTable, String sourceSchema, String targetTable, String targetSchema) {
        log.info("Bulk copying data from {}.{} to {}.{}", sourceSchema, sourceTable, targetSchema, targetTable);

        String sanitizedSourceTable = sanitizeIdentifier(sourceTable);
        String sanitizedSourceSchema = sanitizeIdentifier(sourceSchema);
        String sanitizedTargetTable = sanitizeIdentifier(targetTable);
        String sanitizedTargetSchema = sanitizeIdentifier(targetSchema);

        // Get column names from source table
        Result<org.jooq.Record1<String>> columns = dsl
                .select(DSL.field(DSL.name("column_name"), String.class))
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema")).eq(sanitizedSourceSchema))
                .and(DSL.field(DSL.name("table_name")).eq(sanitizedSourceTable))
                .orderBy(DSL.field(DSL.name("ordinal_position")).asc())
                .fetch();

        if (columns.isEmpty()) {
            log.warn("No columns found in source table {}.{}", sanitizedSourceSchema, sanitizedSourceTable);
            return;
        }

        // Build column list
        List<String> columnNames = new ArrayList<>();
        for (org.jooq.Record1<String> col : columns) {
            String colName = col.value1();
            if (colName != null) {
                columnNames.add(quoteIdentifier(colName));
            }
        }

        String columnList = String.join(", ", columnNames);

        // Copy data
        String sql = String.format(
                "INSERT INTO %s.%s (%s) " +
                "SELECT %s FROM %s.%s",
                quoteIdentifier(sanitizedTargetSchema),
                quoteIdentifier(sanitizedTargetTable),
                columnList,
                columnList,
                quoteIdentifier(sanitizedSourceSchema),
                quoteIdentifier(sanitizedSourceTable));

        int rowsCopied = dsl.execute(sql);
        log.info("Successfully copied {} rows from {}.{} to {}.{}", rowsCopied, sanitizedSourceSchema, sanitizedSourceTable, sanitizedTargetSchema, sanitizedTargetTable);
    }

    @Override
    public Map<String, String> getTableStructure(String tableName, String schemaName) {
        log.info("Getting table structure for {}.{}", schemaName, tableName);

        String sanitizedTableName = sanitizeIdentifier(tableName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        Result<Record5<String, String, Integer, Integer, String>> rows = dsl
                .select(
                        DSL.field(DSL.name("column_name"), String.class),
                        DSL.field(DSL.name("data_type"), String.class),
                        DSL.field(DSL.name("character_maximum_length"), Integer.class),
                        DSL.field(DSL.name("numeric_scale"), Integer.class),
                        DSL.field(DSL.name("column_default"), String.class))
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema")).eq(sanitizedSchemaName))
                .and(DSL.field(DSL.name("table_name")).eq(sanitizedTableName))
                .orderBy(DSL.field(DSL.name("ordinal_position")).asc())
                .fetch();

        Map<String, String> structure = new LinkedHashMap<>();
        for (Record5<String, String, Integer, Integer, String> r : rows) {
            String columnName = r.value1();
            String dataType = r.value2();
            Integer charLen = r.value3();
            Integer numericScale = r.value4();
            String defaultValue = r.value5();

            if (columnName == null || dataType == null) continue;

            String typeDef = normalizeInformationSchemaType(dataType, charLen, numericScale);
            if (defaultValue != null && !defaultValue.isEmpty()) {
                typeDef += " DEFAULT " + defaultValue;
            }
            structure.put(columnName, typeDef);
        }

        return structure;
    }

    @Override
    public List<BaseReferenceTable> getTablesFromSchema(String schemaName) {
        log.info("Getting tables from schema: {}", schemaName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);

        // Query base_reference_table from the specified schema
        Result<?> rows = dsl.select()
                .from(DSL.table(DSL.name(sanitizedSchemaName, "base_reference_table")))
                .orderBy(DSL.field(DSL.name("upd_ts")).desc().nullsLast(),
                        DSL.field(DSL.name("add_ts")).desc())
                .fetch();

        List<BaseReferenceTable> tables = new ArrayList<>();
        for (org.jooq.Record row : rows) {
            BaseReferenceTable table = new BaseReferenceTable();
            table.setId(row.get(DSL.field(DSL.name("id")), Integer.class).longValue());
            table.setTblLabel(row.get(DSL.field(DSL.name("tbl_label")), String.class));
            table.setTblLink(row.get(DSL.field(DSL.name("tbl_link")), String.class));
            table.setDescription(row.get(DSL.field(DSL.name("description")), String.class));
            table.setVersionNo(row.get(DSL.field(DSL.name("version_no")), Integer.class));
            table.setDeploymentType(row.get(DSL.field(DSL.name("deployment_type")), String.class));
            table.setAddTs(row.get(DSL.field(DSL.name("add_ts")), LocalDateTime.class));
            table.setAddUsr(row.get(DSL.field(DSL.name("add_usr")), String.class));
            table.setUpdTs(row.get(DSL.field(DSL.name("upd_ts")), LocalDateTime.class));
            table.setUpdUsr(row.get(DSL.field(DSL.name("upd_usr")), String.class));
            tables.add(table);
        }

        log.info("Found {} tables in schema {}", tables.size(), sanitizedSchemaName);
        return tables;
    }

    @Override
    public void upgradeMetadataConstraints(String schemaName) {
        log.info("Upgrading foreign key constraints in schema: {}", schemaName);
        String sanitizedSchemaName = sanitizeIdentifier(schemaName);
        
        String schemaQuoted = quoteIdentifier(sanitizedSchemaName);
        String columnMapQuoted = quoteIdentifier("base_column_map");
        String refTableQuoted = quoteIdentifier("base_reference_table");
        
        // Check if constraint exists and whether it has ON DELETE CASCADE
        // Check for both possible constraint names
        var constraintInfo = dsl.select(
                DSL.field("pg_constraint.conname", String.class),
                DSL.field("pg_constraint.confdeltype", String.class))
                .from(DSL.table("pg_constraint"))
                .join(DSL.table("pg_class")).on(DSL.field("pg_constraint.conrelid").eq(DSL.field("pg_class.oid")))
                .join(DSL.table("pg_namespace")).on(DSL.field("pg_class.relnamespace").eq(DSL.field("pg_namespace.oid")))
                .where(DSL.field("pg_constraint.conname").in("fk_base_column_map_tbl_id", "base_column_map_tbl_id_fkey"))
                .and(DSL.field("pg_namespace.nspname").eq(sanitizedSchemaName))
                .and(DSL.field("pg_class.relname").eq("base_column_map"))
                .fetchOne();
        
        if (constraintInfo != null) {
            String constraintName = constraintInfo.value1();
            String deleteAction = constraintInfo.value2();
            // 'a' = NO ACTION, 'r' = RESTRICT, 'c' = CASCADE, 'n' = SET NULL, 'd' = SET DEFAULT
            if (!"c".equals(deleteAction)) {
                log.info("Foreign key constraint '{}' exists but without CASCADE, upgrading it", constraintName);
                // Drop existing constraint
                String dropFkSql = String.format(
                        "ALTER TABLE %s.%s DROP CONSTRAINT %s",
                        schemaQuoted,
                        columnMapQuoted,
                        quoteIdentifier(constraintName));
                dsl.execute(dropFkSql);
                
                // Recreate with CASCADE using standard name
                String fkSql = String.format(
                        "ALTER TABLE %s.%s ADD CONSTRAINT fk_base_column_map_tbl_id " +
                        "FOREIGN KEY (tbl_id) REFERENCES %s.%s(id) ON DELETE CASCADE",
                        schemaQuoted,
                        columnMapQuoted,
                        schemaQuoted,
                        refTableQuoted);
                dsl.execute(fkSql);
                
                log.info("Successfully upgraded foreign key constraint to include ON DELETE CASCADE for schema {}", sanitizedSchemaName);
            } else {
                log.debug("Foreign key constraint '{}' already has CASCADE in schema {}", constraintName, sanitizedSchemaName);
            }
        } else {
            log.warn("Foreign key constraint not found in schema {}, columns must be deleted manually before table", sanitizedSchemaName);
        }
    }
}
