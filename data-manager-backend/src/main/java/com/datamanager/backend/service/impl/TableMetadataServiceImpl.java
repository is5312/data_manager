package com.datamanager.backend.service.impl;

import com.datamanager.backend.dao.DataDao;
import com.datamanager.backend.dao.SchemaDao;
import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.entity.BaseColumnMap;
import com.datamanager.backend.entity.BaseReferenceTable;
import com.datamanager.backend.repository.BaseColumnMapRepository;
import com.datamanager.backend.repository.BaseReferenceTableRepository;
import com.datamanager.backend.service.TableMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of TableMetadataService
 * Uses Spring JPA for metadata operations and JOOQ for dynamic schema
 * operations
 */
@Service
@Slf4j
public class TableMetadataServiceImpl implements TableMetadataService {

    private final BaseReferenceTableRepository tableRepository;
    private final BaseColumnMapRepository columnRepository;
    private final SchemaDao schemaDao;
    private final DataDao dataDao;

    public TableMetadataServiceImpl(
            BaseReferenceTableRepository tableRepository,
            BaseColumnMapRepository columnRepository,
            SchemaDao schemaDao,
            DataDao dataDao) {
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.schemaDao = schemaDao;
        this.dataDao = dataDao;
    }

    @Override
    @Transactional
    public TableMetadataDto createTable(String tableLabel) {
        log.info("Creating table with label: {}", tableLabel);

        // 1. Generate unique physical table name
        String physicalTableName = generatePhysicalTableName();

        // 2. Create physical table using JOOQ DAO
        schemaDao.createTable(physicalTableName);

        // 3. Create metadata entry using JPA
        BaseReferenceTable entity = new BaseReferenceTable();
        entity.setTblLabel(tableLabel);
        entity.setTblLink(physicalTableName);
        entity.setAddUsr("system");

        BaseReferenceTable saved = tableRepository.save(entity);

        log.info("Successfully created table: {} with physical name: {}", tableLabel, physicalTableName);

        return convertToDto(saved);
    }

    @Override
    @Transactional
    public TableMetadataDto createTableFromCsv(MultipartFile file, String tableName) {
        return createTableFromCsv(file, tableName, null);
    }

    @Override
    @Transactional
    public TableMetadataDto createTableFromCsv(MultipartFile file, String tableName, List<String> columnTypes) {
        log.info("Creating table from CSV: {}", tableName);

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .build();

            try (CSVParser parser = new CSVParser(reader, format)) {
                List<String> rawHeaders = parser.getHeaderNames();
                if (rawHeaders == null || rawHeaders.isEmpty()) {
                    throw new IllegalArgumentException("CSV file is empty or has no header");
                }

                List<String> headerLabels = normalizeAndUniquifyHeaders(rawHeaders);

                // Read all records (used for type inference + inserts)
                List<CSVRecord> records = parser.getRecords();

                Map<String, String> inferredTypesByLabel = inferColumnTypes(headerLabels, records);

                // Create table (logical label), physical table is auto-generated
                TableMetadataDto table = createTable(tableName.trim());

                // Create columns in the same order as CSV headers
                for (int i = 0; i < headerLabels.size(); i++) {
                    String columnLabel = headerLabels.get(i);
                    String columnType = resolveOverrideTypeByIndex(columnTypes, i)
                            .orElse(inferredTypesByLabel.getOrDefault(columnLabel, "VARCHAR"));
                    addColumn(table.getId(), columnLabel, columnType);
                }

                if (!records.isEmpty()) {
                    BaseReferenceTable tableEntity = tableRepository.findById(table.getId())
                            .orElseThrow(() -> new IllegalArgumentException("Table not found"));

                    // Map column label -> physical name for inserts (do NOT rely on repository order)
                    List<ColumnMetadataDto> createdColumns = getColumnsByTableId(table.getId());
                    Map<String, String> physicalByLabel = createdColumns.stream()
                            .collect(Collectors.toMap(
                                    ColumnMetadataDto::getLabel,
                                    ColumnMetadataDto::getPhysicalName,
                                    (a, b) -> a,
                                    LinkedHashMap::new));

                    for (CSVRecord record : records) {
                        Map<String, Object> rowData = new HashMap<>();

                        for (int i = 0; i < headerLabels.size(); i++) {
                            String label = headerLabels.get(i);
                            String physical = physicalByLabel.get(label);
                            if (physical == null) {
                                continue;
                            }

                            String rawValue = i < record.size() ? record.get(i) : null;
                            String effectiveType = resolveOverrideTypeByIndex(columnTypes, i).orElse(inferredTypesByLabel.get(label));
                            Object typedValue = parseTypedValue(rawValue, effectiveType);
                            if (typedValue != null) {
                                rowData.put(physical, typedValue);
                            }
                        }

                        if (!rowData.isEmpty()) {
                            dataDao.insertRow(tableEntity.getTblLink(), rowData);
                        }
                    }
                }

                log.info("Successfully created table from CSV: {} ({} columns, {} rows)",
                        tableName, headerLabels.size(), records.size());
                return table;
            }
        } catch (Exception e) {
            log.error("Error creating table from CSV", e);
            throw new RuntimeException("Failed to create table from CSV: " + e.getMessage(), e);
        }
    }

    private Optional<String> resolveOverrideTypeByIndex(List<String> columnTypes, int index) {
        if (columnTypes == null) return Optional.empty();
        if (index < 0 || index >= columnTypes.size()) return Optional.empty();
        String t = columnTypes.get(index);
        if (t == null || t.isBlank()) return Optional.empty();
        return Optional.of(t.trim());
    }

    private List<String> normalizeAndUniquifyHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();

        for (int i = 0; i < rawHeaders.size(); i++) {
            String h = Optional.ofNullable(rawHeaders.get(i)).orElse("").trim();
            if (h.isBlank()) {
                h = "column_" + (i + 1);
            }
            h = h.replaceAll("\\s+", "_");

            String candidate = h;
            int suffix = 2;
            while (used.contains(candidate)) {
                candidate = h + "_" + suffix;
                suffix++;
            }
            used.add(candidate);
            headers.add(candidate);
        }
        return headers;
    }

    private Map<String, String> inferColumnTypes(List<String> columnLabels, List<CSVRecord> records) {
        Map<String, String> types = new HashMap<>();

        for (int colIndex = 0; colIndex < columnLabels.size(); colIndex++) {
            String label = columnLabels.get(colIndex);

            boolean allBooleans = true;
            boolean allIntegers = true;
            boolean allDecimals = true;
            boolean allDates = true;
            boolean allTimestamps = true;
            boolean sawAnyValue = false;

            for (CSVRecord record : records) {
                if (colIndex >= record.size()) continue;
                String v = normalizeCell(record.get(colIndex));
                if (v == null) continue;
                sawAnyValue = true;

                if (!isBoolean(v)) allBooleans = false;
                if (!isInteger(v)) allIntegers = false;
                if (!isDecimal(v)) allDecimals = false;
                if (!isDate(v)) allDates = false;
                if (!isTimestamp(v)) allTimestamps = false;
            }

            // If the column is entirely empty, default to VARCHAR
            if (!sawAnyValue) {
                types.put(label, "VARCHAR");
                continue;
            }

            if (allBooleans) {
                types.put(label, "BOOLEAN");
            } else if (allIntegers) {
                types.put(label, "BIGINT");
            } else if (allDecimals) {
                // Use DECIMAL to preserve precision; SchemaDao maps DECIMAL/NUMERIC
                types.put(label, "DECIMAL");
            } else if (allDates) {
                types.put(label, "DATE");
            } else if (allTimestamps) {
                types.put(label, "TIMESTAMP");
            } else {
                types.put(label, "VARCHAR");
            }
        }

        return types;
    }

    private Object parseTypedValue(String rawValue, String inferredType) {
        String v = normalizeCell(rawValue);
        if (v == null) return null;

        String type = inferredType == null ? "VARCHAR" : inferredType.toUpperCase().trim();
        try {
            return switch (type) {
                case "BOOLEAN", "BOOL" -> parseBoolean(v);
                case "INTEGER", "INT", "BIGINT", "LONG" -> Long.parseLong(v);
                case "DECIMAL", "NUMERIC", "DOUBLE", "FLOAT", "REAL" -> new BigDecimal(v);
                case "DATE" -> LocalDate.parse(v);
                case "TIMESTAMP" -> parseTimestamp(v);
                default -> v;
            };
        } catch (Exception e) {
            // If a specific cell fails to parse as inferred type, fall back to string.
            return v;
        }
    }

    private String normalizeCell(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.equalsIgnoreCase("null")) return null;
        return v;
    }

    private boolean isBoolean(String v) {
        String s = v.trim().toLowerCase();
        return s.equals("true") || s.equals("false") || s.equals("t") || s.equals("f") || s.equals("yes") || s.equals("no") || s.equals("y") || s.equals("n") || s.equals("1") || s.equals("0");
    }

    private Boolean parseBoolean(String v) {
        String s = v.trim().toLowerCase();
        return switch (s) {
            case "true", "t", "yes", "y", "1" -> true;
            case "false", "f", "no", "n", "0" -> false;
            default -> null;
        };
    }

    private boolean isInteger(String v) {
        try {
            Long.parseLong(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDecimal(String v) {
        try {
            new BigDecimal(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDate(String v) {
        try {
            LocalDate.parse(v);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isTimestamp(String v) {
        try {
            parseTimestamp(v);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private Object parseTimestamp(String v) {
        // Accept ISO_LOCAL_DATE_TIME or ISO_OFFSET_DATE_TIME
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        OffsetDateTime odt = OffsetDateTime.parse(v);
        return odt.toLocalDateTime();
    }

    @Override
    @Transactional
    public ColumnMetadataDto addColumn(Long tableId, String columnLabel, String columnType) {
        log.info("Adding column {} to table ID {}", columnLabel, tableId);

        // 1. Get table metadata using JPA repository
        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));

        String physicalTableName = table.getTblLink();

        // 2. Generate unique physical column name
        String physicalColumnName = generatePhysicalColumnName();

        // 3. Add column to physical table using JOOQ DAO
        schemaDao.addColumn(physicalTableName, physicalColumnName, columnType);

        // 4. Create column metadata using JPA
        BaseColumnMap columnEntity = new BaseColumnMap();
        columnEntity.setReferenceTable(table);
        columnEntity.setTblLink(physicalTableName);
        columnEntity.setColLabel(columnLabel);
        columnEntity.setColLink(physicalColumnName);
        columnEntity.setAddUsr("system");

        BaseColumnMap saved = columnRepository.save(columnEntity);

        log.info("Successfully added column {} to table {}", columnLabel, tableId);

        return convertToDto(saved, columnType);
    }

    @Override
    @Transactional
    public ColumnMetadataDto changeColumnType(Long tableId, Long columnId, String columnType) {
        log.info("Changing column type for column {} in table {} to {}", columnId, tableId, columnType);

        if (columnType == null || columnType.isBlank()) {
            throw new IllegalArgumentException("Column type cannot be null or blank");
        }

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));

        BaseColumnMap column = columnRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found with ID: " + columnId));

        if (!column.getReferenceTable().getId().equals(tableId)) {
            throw new IllegalArgumentException("Column does not belong to the specified table");
        }

        // ALTER physical column type
        schemaDao.alterColumnType(table.getTblLink(), column.getColLink(), columnType);

        // Return updated metadata with refreshed physical type
        Map<String, String> typesByPhysicalName = schemaDao.getColumnTypes(table.getTblLink());
        String actualType = typesByPhysicalName.get(column.getColLink());
        return convertToDto(column, actualType != null ? actualType : columnType);
    }

    @Override
    @Transactional
    public void removeColumn(Long tableId, Long columnId) {
        log.info("Removing column ID {} from table ID {}", columnId, tableId);

        // 1. Get column metadata
        BaseColumnMap column = columnRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found with ID: " + columnId));

        if (!column.getReferenceTable().getId().equals(tableId)) {
            throw new IllegalArgumentException("Column does not belong to the specified table");
        }

        String physicalTableName = column.getTblLink();
        String physicalColumnName = column.getColLink();

        // 2. Remove column from physical table using JOOQ DAO
        schemaDao.removeColumn(physicalTableName, physicalColumnName);

        // 3. Delete column metadata using JPA
        columnRepository.delete(column);

        log.info("Successfully removed column ID {} from table ID {}", columnId, tableId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableMetadataDto> getAllTables() {
        log.info("Fetching all tables");

        List<BaseReferenceTable> tables = tableRepository.findAll();

        return tables.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TableMetadataDto getTableById(Long tableId) {
        log.info("Fetching table with ID: {}", tableId);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));

        return convertToDto(table);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColumnMetadataDto> getColumnsByTableId(Long tableId) {
        log.info("Fetching columns for table ID: {}", tableId);

        // Verify table exists
        if (!tableRepository.existsById(tableId)) {
            throw new IllegalArgumentException("Table not found with ID: " + tableId);
        }

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));
        Map<String, String> typesByPhysicalName = schemaDao.getColumnTypes(table.getTblLink());

        List<BaseColumnMap> columns = columnRepository.findByReferenceTableId(tableId);

        return columns.stream()
                .map(col -> convertToDto(col, typesByPhysicalName.get(col.getColLink())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTable(Long tableId) {
        log.info("Deleting table with ID: {}", tableId);

        // 1. Get table metadata
        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));

        String physicalTableName = table.getTblLink();

        // 2. Drop physical table using JOOQ DAO
        schemaDao.dropTable(physicalTableName);

        // 3. Delete metadata using JPA (cascade will delete columns)
        tableRepository.delete(table);

        log.info("Successfully deleted table ID {} with physical name {}", tableId, physicalTableName);
    }

    @Override
    @Transactional
    public TableMetadataDto renameTable(Long tableId, String newLabel) {
        log.info("Renaming table ID {} to {}", tableId, newLabel);

        BaseReferenceTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with ID: " + tableId));

        table.setTblLabel(newLabel);
        table.setUpdUsr("system");

        BaseReferenceTable updated = tableRepository.save(table);

        log.info("Successfully renamed table ID {} to {}", tableId, newLabel);

        return convertToDto(updated);
    }

    /**
     * Generate unique physical table name
     */
    private String generatePhysicalTableName() {
        return com.datamanager.backend.util.IdGenerator.generatePhysicalTableName();
    }

    /**
     * Generate unique physical column name
     */
    private String generatePhysicalColumnName() {
        return "col_" + com.datamanager.backend.util.IdGenerator.generateUlid();
    }

    /**
     * Convert BaseReferenceTable entity to DTO
     */
    private TableMetadataDto convertToDto(BaseReferenceTable entity) {
        return TableMetadataDto.builder()
                .id(entity.getId())
                .label(entity.getTblLabel())
                .physicalName(entity.getTblLink())
                .createdAt(entity.getAddTs())
                .createdBy(entity.getAddUsr())
                .updatedAt(entity.getUpdTs())
                .updatedBy(entity.getUpdUsr())
                .build();
    }

    /**
     * Convert BaseColumnMap entity to DTO
     */
    private ColumnMetadataDto convertToDto(BaseColumnMap entity, String type) {
        return ColumnMetadataDto.builder()
                .id(entity.getId())
                .tableId(entity.getReferenceTable().getId())
                .label(entity.getColLabel())
                .physicalName(entity.getColLink())
                .tablePhysicalName(entity.getTblLink())
                .type(type)
                .createdAt(entity.getAddTs())
                .createdBy(entity.getAddUsr())
                .updatedAt(entity.getUpdTs())
                .updatedBy(entity.getUpdUsr())
                .build();
    }
}
