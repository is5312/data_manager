package com.datamanager.backend.service.impl;

import com.datamanager.backend.dao.SchemaDao;
import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.mapper.MetadataMapper;
import com.datamanager.backend.service.SchemaFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of SchemaFilterService
 * Uses SchemaDao to query schema-specific metadata tables
 */
@Service
@Slf4j
public class SchemaFilterServiceImpl implements SchemaFilterService {

    private final SchemaDao schemaDao;

    public SchemaFilterServiceImpl(SchemaDao schemaDao) {
        this.schemaDao = schemaDao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableMetadataDto> getTablesBySchema(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            schemaName = "public";
        }

        log.info("Fetching tables from schema: {}", schemaName);

        // Query metadata tables from the specified schema
        List<BaseReferenceTable> tables = schemaDao.getTablesFromSchema(schemaName);

        return tables.stream()
                .map(MetadataMapper::toDto)
                .collect(Collectors.toList());
    }
}
