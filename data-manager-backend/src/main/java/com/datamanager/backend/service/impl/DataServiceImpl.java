package com.datamanager.backend.service.impl;

import com.datamanager.backend.dao.DataDao;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.DataService;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Service implementation for data operations on physical tables
 */
@Service
@Slf4j
public class DataServiceImpl implements DataService {

    private final DataDao dataDao;
    private final BaseReferenceTableRepository tableRepository;
    private final DSLContext dsl;

    public DataServiceImpl(DataDao dataDao, BaseReferenceTableRepository tableRepository, DSLContext dsl) {
        this.dataDao = dataDao;
        this.tableRepository = tableRepository;
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public Map<String, Object> insertRow(Long tableId, Map<String, Object> rowData) {
        log.info("Inserting row into table ID: {}", tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        Map<String, Object> auditData = dataDao.insertRow(table.getTblLink(), rowData);

        // Build response with audit columns
        Map<String, Object> response = new HashMap<>();
        response.put("id", auditData.get("id"));
        response.put("message", "Row inserted successfully");
        response.put("add_usr", auditData.get("add_usr"));
        response.put("add_ts", auditData.get("add_ts"));
        response.put("upd_usr", auditData.get("upd_usr"));
        response.put("upd_ts", auditData.get("upd_ts"));

        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> updateRow(Long tableId, Long rowId, Map<String, Object> rowData) {
        log.info("Updating row {} in table ID: {}", rowId, tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        Map<String, Object> auditData = dataDao.updateRow(table.getTblLink(), rowId, rowData);

        // Build response with audit columns
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Row updated successfully");
        response.put("add_usr", auditData.get("add_usr"));
        response.put("add_ts", auditData.get("add_ts"));
        response.put("upd_usr", auditData.get("upd_usr"));
        response.put("upd_ts", auditData.get("upd_ts"));

        return response;
    }

    @Override
    @Transactional
    public void deleteRow(Long tableId, Long rowId) {
        log.info("Deleting row {} from table ID: {}", rowId, tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        dataDao.deleteRow(table.getTblLink(), rowId);
    }

    // Schema-aware methods

    @Override
    @Transactional
    public Map<String, Object> insertRow(Long tableId, String schema, Map<String, Object> rowData) {
        log.info("Inserting row into table ID: {} in schema: {}", tableId, schema);

        String physicalTableName = getPhysicalTableName(tableId, schema);
        // Pass unquoted schema.table - JOOQ's DSL.name() will handle quoting
        String qualifiedTableName = schema + "." + physicalTableName;
        Map<String, Object> auditData = dataDao.insertRow(qualifiedTableName, rowData);

        // Build response with audit columns
        Map<String, Object> response = new HashMap<>();
        response.put("id", auditData.get("id"));
        response.put("message", "Row inserted successfully");
        response.put("add_usr", auditData.get("add_usr"));
        response.put("add_ts", auditData.get("add_ts"));
        response.put("upd_usr", auditData.get("upd_usr"));
        response.put("upd_ts", auditData.get("upd_ts"));

        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> updateRow(Long tableId, String schema, Long rowId, Map<String, Object> rowData) {
        log.info("Updating row {} in table ID: {} in schema: {}", rowId, tableId, schema);

        String physicalTableName = getPhysicalTableName(tableId, schema);
        // Pass unquoted schema.table - JOOQ's DSL.name() will handle quoting
        String qualifiedTableName = schema + "." + physicalTableName;
        Map<String, Object> auditData = dataDao.updateRow(qualifiedTableName, rowId, rowData);

        // Build response with audit columns
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Row updated successfully");
        response.put("add_usr", auditData.get("add_usr"));
        response.put("add_ts", auditData.get("add_ts"));
        response.put("upd_usr", auditData.get("upd_usr"));
        response.put("upd_ts", auditData.get("upd_ts"));

        return response;
    }

    @Override
    @Transactional
    public void deleteRow(Long tableId, String schema, Long rowId) {
        log.info("Deleting row {} from table ID: {} in schema: {}", rowId, tableId, schema);

        String physicalTableName = getPhysicalTableName(tableId, schema);
        // Pass unquoted schema.table - JOOQ's DSL.name() will handle quoting
        String qualifiedTableName = schema + "." + physicalTableName;
        dataDao.deleteRow(qualifiedTableName, rowId);
    }

    /**
     * Get physical table name from schema-specific metadata table
     */
    private String getPhysicalTableName(Long tableId, String schema) {
        String sql = String.format("SELECT tbl_link FROM \"%s\".base_reference_table WHERE id = ?", schema);
        String tableName = dsl.fetchOne(sql, tableId).get(0, String.class);
        
        if (tableName == null) {
            throw new IllegalArgumentException("Table not found: " + tableId);
        }
        
        return tableName;
    }
}

