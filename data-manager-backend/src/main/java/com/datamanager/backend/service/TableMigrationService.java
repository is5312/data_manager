package com.datamanager.backend.service;

import com.datamanager.backend.dto.MigrationResponseDto;

import java.util.List;

/**
 * Service interface for table migration operations
 * Handles migrating tables between PostgreSQL schemas
 */
public interface TableMigrationService {

    /**
     * Get list of available schemas from configuration
     *
     * @return List of available schema names
     */
    List<String> getAvailableSchemas();

    /**
     * Migrate a table from source schema to target schema
     *
     * @param tableId Table ID in the source schema
     * @param sourceSchema Source schema name
     * @param targetSchema Target schema name
     * @return Migration response with status and details
     */
    MigrationResponseDto migrateTable(Long tableId, String sourceSchema, String targetSchema);
}
