package com.datamanager.backend.service;

import com.datamanager.backend.dto.TableMetadataDto;

import java.util.List;

/**
 * Service interface for schema filtering operations
 * Handles querying tables from specific schemas
 */
public interface SchemaFilterService {

    /**
     * Get all tables from a specific schema
     *
     * @param schemaName Schema name to query (defaults to "public" if null)
     * @return List of table metadata from the specified schema
     */
    List<TableMetadataDto> getTablesBySchema(String schemaName);
}
