package com.datamanager.backend.service.impl;

import com.datamanager.backend.dao.SchemaDao;
import com.datamanager.backend.dto.MigrationResponseDto;
import com.datamanager.backend.entity.BaseColumnMap;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.config.MigrationProperties;
import com.datamanager.backend.service.TableMigrationService;
import com.datamanager.backend.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of TableMigrationService
 * Handles migrating tables between PostgreSQL schemas with shadow copy support
 */
@Service
@Slf4j
public class TableMigrationServiceImpl implements TableMigrationService {

    private final SchemaDao schemaDao;
    private final DSLContext dsl;
    private final MigrationProperties migrationProperties;

    public TableMigrationServiceImpl(
            SchemaDao schemaDao,
            DSLContext dsl,
            MigrationProperties migrationProperties) {
        this.schemaDao = schemaDao;
        this.dsl = dsl;
        this.migrationProperties = migrationProperties;
    }

    @Override
    public List<String> getAvailableSchemas() {
        return new ArrayList<>(migrationProperties.getAvailableSchemas());
    }

    @Override
    @Transactional
    public MigrationResponseDto migrateTable(Long tableId, String sourceSchema, String targetSchema) {
        log.info("Starting migration of table {} from schema {} to schema {}", tableId, sourceSchema, targetSchema);

        if (sourceSchema == null || sourceSchema.isBlank()) {
            sourceSchema = "public";
        }
        if (targetSchema == null || targetSchema.isBlank()) {
            throw new IllegalArgumentException("Target schema cannot be null or blank");
        }

        // Validate target schema is in available schemas
        if (!migrationProperties.getAvailableSchemas().contains(targetSchema)) {
            throw new IllegalArgumentException("Target schema '" + targetSchema + "' is not in available schemas list");
        }

        // 1. Ensure target schema exists
        ensureSchemaExists(targetSchema);

        // 2. Ensure metadata tables exist in target schema (idempotent)
        ensureMetadataTablesExist(targetSchema);

        // 3. Upgrade foreign key constraints to include CASCADE (for existing schemas)
        schemaDao.upgradeMetadataConstraints(targetSchema);

        // 4. Get source table metadata from source schema
        BaseReferenceTable sourceTable = getTableFromSchema(tableId, sourceSchema);
        if (sourceTable == null) {
            throw new IllegalArgumentException("Table with ID " + tableId + " not found in schema " + sourceSchema);
        }

        // 5. Check if physical table exists in source and target schemas
        boolean sourceTableExists = schemaDao.tableExistsInSchema(sourceTable.getTblLink(), sourceSchema);
        boolean targetTableExists = schemaDao.tableExistsInSchema(sourceTable.getTblLink(), targetSchema);

        String shadowTableName;

        if (targetTableExists) {
            // Shadow copy approach: Table exists in target schema
            // Create a NEW shadow table with ULID-generated name
            shadowTableName = IdGenerator.generatePhysicalTableName();
            log.info("Table {} already exists in target schema {}, creating shadow table {} with ULID", 
                    sourceTable.getTblLink(), targetSchema, shadowTableName);

            // Step 1: Create shadow table using DDL from source schema
            createShadowTable(sourceTable.getTblLink(), sourceSchema, shadowTableName, targetSchema);

            // Step 2: Copy data from existing target table to shadow table
            schemaDao.bulkCopyTableData(sourceTable.getTblLink(), targetSchema, shadowTableName, targetSchema);
            log.info("Copied data from existing {}.{} to shadow table {}.{}", 
                    targetSchema, sourceTable.getTblLink(), targetSchema, shadowTableName);

            // Step 3: Create triggers on existing target table to sync changes to shadow table
            String triggerPrefix = "trigger_migrate_" + tableId + "_" + System.currentTimeMillis();
            schemaDao.createTrigger(triggerPrefix + "_insert", sourceTable.getTblLink(), targetSchema,
                    shadowTableName, targetSchema, "INSERT");
            schemaDao.createTrigger(triggerPrefix + "_update", sourceTable.getTblLink(), targetSchema,
                    shadowTableName, targetSchema, "UPDATE");
            schemaDao.createTrigger(triggerPrefix + "_delete", sourceTable.getTblLink(), targetSchema,
                    shadowTableName, targetSchema, "DELETE");
            
            log.info("Shadow copy completed: Created shadow table {} with triggers on existing table {}", 
                    shadowTableName, sourceTable.getTblLink());
        } else {
            // Normal migration: Table doesn't exist in target schema
            shadowTableName = sourceTable.getTblLink(); // Use same name
            log.info("Table {} does not exist in target schema {}, creating new table", 
                    sourceTable.getTblLink(), targetSchema);

            // Create table in target schema
            createShadowTable(sourceTable.getTblLink(), sourceSchema, shadowTableName, targetSchema);

            // If source table has data, copy it
            if (sourceTableExists) {
                schemaDao.bulkCopyTableData(sourceTable.getTblLink(), sourceSchema, shadowTableName, targetSchema);
                log.info("Copied data from {}.{} to {}.{}", 
                        sourceSchema, sourceTable.getTblLink(), targetSchema, shadowTableName);
            }
        }

        // 8. Copy metadata to target schema (using shadow table name)
        copyTableMetadata(sourceTable, sourceSchema, targetSchema, shadowTableName);

        log.info("Successfully migrated table {} from schema {} to schema {}", tableId, sourceSchema, targetSchema);

        return MigrationResponseDto.builder()
                .status("SUCCESS")
                .message("Table migrated successfully")
                .shadowTableName(shadowTableName)
                .targetSchema(targetSchema)
                .tableId(tableId)
                .details("Table " + sourceTable.getTblLabel() + " migrated from " + sourceSchema + " to " + targetSchema)
                .build();
    }

    private void ensureSchemaExists(String schemaName) {
        if (!schemaDao.schemaExists(schemaName)) {
            log.info("Schema {} does not exist, creating it", schemaName);
            schemaDao.createSchema(schemaName);
        }
    }

    private void ensureMetadataTablesExist(String schemaName) {
        // Idempotent in SchemaDaoImpl via CREATE TABLE IF NOT EXISTS; keep unit tests simple.
        schemaDao.createMetadataTablesInSchema(schemaName);
    }

    private BaseReferenceTable getTableFromSchema(Long tableId, String schemaName) {
        List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schemaName);
        return tables.stream()
                .filter(t -> t.getId().equals(tableId))
                .findFirst()
                .orElse(null);
    }

    private void createShadowTable(String sourceTableName, String sourceSchema, String shadowTableName, String targetSchema) {
        log.info("Creating shadow table {} in schema {} based on {}.{}", shadowTableName, targetSchema, sourceSchema, sourceTableName);

        // Get table structure from source
        Map<String, String> structure = schemaDao.getTableStructure(sourceTableName, sourceSchema);

        if (structure.isEmpty()) {
            // If no structure found, create a basic table (for new tables without data)
            schemaDao.createTableInSchema(shadowTableName, targetSchema);
        } else {
            // Create table with same structure
            // Build CREATE TABLE statement
            StringBuilder createSql = new StringBuilder();
            createSql.append("CREATE TABLE ").append(quoteIdentifier(targetSchema)).append(".").append(quoteIdentifier(shadowTableName)).append(" (");

            List<String> columns = new ArrayList<>();
            for (Map.Entry<String, String> entry : structure.entrySet()) {
                columns.add(quoteIdentifier(entry.getKey()) + " " + entry.getValue());
            }

            createSql.append(String.join(", ", columns));
            createSql.append(")");

            dsl.execute(createSql.toString());
            log.info("Created shadow table {} in schema {} with {} columns", shadowTableName, targetSchema, structure.size());
        }
    }

    private void copyTableMetadata(BaseReferenceTable sourceTable, String sourceSchema, String targetSchema, String shadowTableName) {
        log.info("Copying table metadata from {}.base_reference_table to {}.base_reference_table with shadow table {}", 
                sourceSchema, targetSchema, shadowTableName);

        List<BaseColumnMap> sourceColumns = getColumnsFromSchema(sourceTable.getId(), sourceSchema);
        
        // Check if metadata already exists in target schema
        Integer existingTableId = getExistingTableIdInSchema(sourceTable.getTblLabel(), targetSchema);
        
        Integer tableId;
        
        if (existingTableId != null) {
            // UPDATE existing metadata (shadow copy scenario)
            log.info("Metadata already exists in target schema with ID {}, backing up and updating tbl_link to {}", 
                    existingTableId, shadowTableName);
            
            // STEP 1: Backup current metadata before update
            backupTableMetadata(existingTableId, targetSchema);
            
            // STEP 2: Update base_reference_table to point to shadow table
            String updateTableSql = String.format(
                "UPDATE %s.base_reference_table " +
                "SET tbl_link = ?, version_no = version_no + 1, upd_ts = CURRENT_TIMESTAMP, upd_usr = ? " +
                "WHERE id = ?",
                quoteIdentifier(targetSchema));
            
            dsl.execute(updateTableSql, shadowTableName, sourceTable.getUpdUsr(), existingTableId);
            tableId = existingTableId;
            
            log.info("Updated metadata: tbl_link -> {}, version_no incremented", shadowTableName);
            
            // STEP 3: Sync column metadata - add any new columns from source that don't exist in target
            syncColumnMetadata(existingTableId, sourceColumns, targetSchema);
            log.info("Synced column metadata for table {} in schema {}", sourceTable.getTblLabel(), targetSchema);
        } else {
            // INSERT new metadata (normal migration scenario)
            log.info("Metadata does not exist in target schema, inserting new metadata");
            
            String insertTableSql = String.format(
                "INSERT INTO %s.base_reference_table (tbl_label, tbl_link, description, version_no, deployment_type, add_ts, add_usr, upd_ts, upd_usr) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                quoteIdentifier(targetSchema));
            
            tableId = dsl.fetchOne(insertTableSql,
                sourceTable.getTblLabel(),
                shadowTableName,
                sourceTable.getDescription(),
                sourceTable.getVersionNo(),
                sourceTable.getDeploymentType(),
                sourceTable.getAddTs(),
                sourceTable.getAddUsr(),
                sourceTable.getUpdTs(),
                sourceTable.getUpdUsr())
                .get(0, Integer.class);
            
            // Insert columns for new table
            for (BaseColumnMap sourceColumn : sourceColumns) {
                String insertColumnSql = String.format(
                    "INSERT INTO %s.base_column_map (tbl_id, tbl_link, col_label, col_link, description, version_no, add_ts, add_usr, upd_ts, upd_usr) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    quoteIdentifier(targetSchema));
                
                dsl.execute(insertColumnSql,
                    tableId,
                    sourceColumn.getTblLink(),
                    sourceColumn.getColLabel(),
                    sourceColumn.getColLink(),
                    sourceColumn.getDescription(),
                    sourceColumn.getVersionNo(),
                    sourceColumn.getAddTs(),
                    sourceColumn.getAddUsr(),
                    sourceColumn.getUpdTs(),
                    sourceColumn.getUpdUsr());
            }
        }
        
        log.info("Successfully handled metadata for table {} in schema {} with version tracking", 
                sourceTable.getTblLabel(), targetSchema);
    }

    private List<BaseColumnMap> getColumnsFromSchema(Long tableId, String schemaName) {
        // Query base_column_map from source schema
        var rows = dsl.select()
                .from(DSL.table(DSL.name(schemaName, "base_column_map")))
                .where(DSL.field("tbl_id").eq(tableId))
                .fetch();

        List<BaseColumnMap> columns = new ArrayList<>();
        for (org.jooq.Record row : rows) {
            BaseColumnMap column = new BaseColumnMap();
            column.setId(row.get(DSL.field("id"), Long.class));
            column.setTblLink(row.get(DSL.field("tbl_link"), String.class));
            column.setColLabel(row.get(DSL.field("col_label"), String.class));
            column.setColLink(row.get(DSL.field("col_link"), String.class));
            column.setDescription(row.get(DSL.field("description"), String.class));
            column.setVersionNo(row.get(DSL.field("version_no"), Integer.class));
            column.setAddTs(row.get(DSL.field("add_ts"), java.time.LocalDateTime.class));
            column.setAddUsr(row.get(DSL.field("add_usr"), String.class));
            column.setUpdTs(row.get(DSL.field("upd_ts"), java.time.LocalDateTime.class));
            column.setUpdUsr(row.get(DSL.field("upd_usr"), String.class));
            columns.add(column);
        }

        return columns;
    }

    private Integer getExistingTableIdInSchema(String tblLabel, String targetSchema) {
        String querySql = String.format(
            "SELECT id FROM %s.base_reference_table WHERE tbl_label = ? LIMIT 1",
            quoteIdentifier(targetSchema));
        
        var result = dsl.fetchOne(querySql, tblLabel);
        return result != null ? result.get(0, Integer.class) : null;
    }

    private void syncColumnMetadata(Integer tableId, List<BaseColumnMap> sourceColumns, String targetSchema) {
        log.info("Syncing column metadata for table ID {} in schema {} - inserting {} columns", 
                tableId, targetSchema, sourceColumns.size());
        
        // STEP 1: Backup existing column_map entries before deleting
        String backupColumnsSql = String.format(
            "INSERT INTO %s.base_column_map_bak " +
            "(id, tbl_id, tbl_link, col_label, col_link, description, version_no, add_ts, add_usr, upd_ts, upd_usr) " +
            "SELECT id, tbl_id, tbl_link, col_label, col_link, description, version_no, add_ts, add_usr, upd_ts, upd_usr " +
            "FROM %s.base_column_map WHERE tbl_id = ?",
            quoteIdentifier(targetSchema), quoteIdentifier(targetSchema));
        
        dsl.execute(backupColumnsSql, tableId);
        log.info("Backed up existing columns for table {} in schema {}", tableId, targetSchema);
        
        // STEP 2: Delete existing columns from base_column_map to avoid duplicates
        String deleteColumnsSql = String.format(
            "DELETE FROM %s.base_column_map WHERE tbl_id = ?",
            quoteIdentifier(targetSchema));
        
        dsl.execute(deleteColumnsSql, tableId);
        log.info("Deleted existing columns for table {} in schema {}", tableId, targetSchema);
        
        // STEP 3: Insert all columns from source
        for (BaseColumnMap sourceColumn : sourceColumns) {
            String insertColumnSql = String.format(
                "INSERT INTO %s.base_column_map (tbl_id, tbl_link, col_label, col_link, description, version_no, add_ts, add_usr, upd_ts, upd_usr) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                quoteIdentifier(targetSchema));
            
            dsl.execute(insertColumnSql,
                tableId,
                sourceColumn.getTblLink(),
                sourceColumn.getColLabel(),
                sourceColumn.getColLink(),
                sourceColumn.getDescription(),
                sourceColumn.getVersionNo(),
                sourceColumn.getAddTs(),
                sourceColumn.getAddUsr(),
                sourceColumn.getUpdTs(),
                sourceColumn.getUpdUsr());
        }
        
        // STEP 4: Cleanup old backups (keep only last 5 versions)
        cleanupOldColumnBackups(tableId, targetSchema);
        
        log.info("Column sync complete: {} columns inserted to schema {} (with backup)", sourceColumns.size(), targetSchema);
    }

    private void cleanupOldColumnBackups(Integer tableId, String targetSchema) {
        log.debug("Cleaning up old column backups for table ID {} in schema {}, keeping last 5 versions", tableId, targetSchema);
        
        // Delete old column backups (keep last 5)
        String cleanupColumnsSql = String.format(
            "DELETE FROM %s.base_column_map_bak " +
            "WHERE tbl_id = ? AND backup_ts NOT IN (" +
            "  SELECT DISTINCT backup_ts FROM %s.base_column_map_bak " +
            "  WHERE tbl_id = ? ORDER BY backup_ts DESC LIMIT 5" +
            ")",
            quoteIdentifier(targetSchema), quoteIdentifier(targetSchema));
        
        dsl.execute(cleanupColumnsSql, tableId, tableId);
    }

    private void backupTableMetadata(Integer tableId, String targetSchema) {
        log.info("Backing up metadata for table ID {} in schema {} before update", tableId, targetSchema);
        
        // Backup base_reference_table record
        String backupTableSql = String.format(
            "INSERT INTO %s.base_reference_table_bak " +
            "(id, tbl_label, tbl_link, description, version_no, deployment_type, add_ts, add_usr, upd_ts, upd_usr) " +
            "SELECT id, tbl_label, tbl_link, description, version_no, deployment_type, add_ts, add_usr, upd_ts, upd_usr " +
            "FROM %s.base_reference_table WHERE id = ?",
            quoteIdentifier(targetSchema), quoteIdentifier(targetSchema));
        
        dsl.execute(backupTableSql, tableId);
        
        // Backup base_column_map records
        String backupColumnsSql = String.format(
            "INSERT INTO %s.base_column_map_bak " +
            "(id, tbl_id, tbl_link, col_label, col_link, description, version_no, add_ts, add_usr, upd_ts, upd_usr) " +
            "SELECT id, tbl_id, tbl_link, col_label, col_link, description, version_no, add_ts, add_usr, upd_ts, upd_usr " +
            "FROM %s.base_column_map WHERE tbl_id = ?",
            quoteIdentifier(targetSchema), quoteIdentifier(targetSchema));
        
        dsl.execute(backupColumnsSql, tableId);
        
        // Cleanup old backups (keep only last 5 versions)
        cleanupOldBackups(tableId, targetSchema);
        
        log.info("Successfully backed up metadata for table ID {}", tableId);
    }

    private void cleanupOldBackups(Integer tableId, String targetSchema) {
        log.debug("Cleaning up old backups for table ID {} in schema {}, keeping last 5 versions", tableId, targetSchema);
        
        // Delete old table backups (keep last 5)
        String cleanupTableSql = String.format(
            "DELETE FROM %s.base_reference_table_bak " +
            "WHERE id = ? AND backup_ts NOT IN (" +
            "  SELECT backup_ts FROM %s.base_reference_table_bak " +
            "  WHERE id = ? ORDER BY backup_ts DESC LIMIT 5" +
            ")",
            quoteIdentifier(targetSchema), quoteIdentifier(targetSchema));
        
        dsl.execute(cleanupTableSql, tableId, tableId);
        
        // Delete old column backups (keep last 5)
        String cleanupColumnsSql = String.format(
            "DELETE FROM %s.base_column_map_bak " +
            "WHERE tbl_id = ? AND backup_ts NOT IN (" +
            "  SELECT DISTINCT backup_ts FROM %s.base_column_map_bak " +
            "  WHERE tbl_id = ? ORDER BY backup_ts DESC LIMIT 5" +
            ")",
            quoteIdentifier(targetSchema), quoteIdentifier(targetSchema));
        
        dsl.execute(cleanupColumnsSql, tableId, tableId);
    }

    private String quoteIdentifier(String identifier) {
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
