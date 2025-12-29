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
    public List<Map<String, Object>> queryTableData(String tableName) {
        log.info("Querying data from table: {}", tableName);

        Table<?> table = DSL.table(DSL.name(tableName));

        List<Map<String, Object>> result = dsl.selectFrom(table)
                .fetch()
                .intoMaps();

        log.info("Retrieved {} rows from table: {}", result.size(), tableName);
        return result;
    }

    @Override
    public Long insertRow(String tableName, Map<String, Object> rowData) {
        log.info("Inserting row into table: {}", tableName);

        Table<?> table = DSL.table(DSL.name(tableName));

        // Build the insert query dynamically
        var insertStep = dsl.insertInto(table);

        List<Field<Object>> fields = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            fields.add(DSL.field(DSL.name(entry.getKey()), Object.class));
            values.add(entry.getValue());
        }

        var result = insertStep
                .columns(fields)
                .values(values)
                .returningResult(DSL.field(DSL.name("id"), Long.class))
                .fetchOne();

        Long id = result != null ? result.value1() : null;
        log.info("Inserted row with ID: {}", id);
        return id;
    }

    @Override
    public void updateRow(String tableName, Long rowId, Map<String, Object> rowData) {
        log.info("Updating row {} in table: {} with data: {}", rowId, tableName, rowData);

        Table<?> table = DSL.table(DSL.name(tableName));
        var query = dsl.updateQuery(table);
        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            query.addValue(DSL.field(DSL.name(entry.getKey()), Object.class), entry.getValue());
        }
        query.addConditions(DSL.field(DSL.name("id"), Long.class).eq(rowId));

        int execute = query.execute();

        if (execute == 0) {
            log.error("Failed to update row {} in table {}: Row not found or no changes made", rowId, tableName);
            throw new RuntimeException("Failed to update row: Row " + rowId + " not found in table " + tableName);
        }

        log.info("Successfully updated row {} in table: {}", rowId, tableName);
    }

    @Override
    public void deleteRow(String tableName, Long rowId) {
        log.info("Deleting row {} from table: {}", rowId, tableName);

        Table<?> table = DSL.table(DSL.name(tableName));

        dsl.deleteFrom(table)
                .where(DSL.field(DSL.name("id"), Long.class).eq(rowId))
                .execute();

        log.info("Deleted row {} from table: {}", rowId, tableName);
    }
}
