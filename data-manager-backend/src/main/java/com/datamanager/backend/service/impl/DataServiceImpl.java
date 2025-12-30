package com.datamanager.backend.service.impl;

import com.datamanager.backend.dao.DataDao;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.DataService;
import lombok.extern.slf4j.Slf4j;
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

    public DataServiceImpl(DataDao dataDao, BaseReferenceTableRepository tableRepository) {
        this.dataDao = dataDao;
        this.tableRepository = tableRepository;
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
}

