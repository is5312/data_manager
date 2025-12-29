package com.datamanager.backend.mapper;

import com.datamanager.backend.dto.ColumnMetadataDto;
import com.datamanager.backend.dto.TableMetadataDto;
import com.datamanager.backend.entity.BaseColumnMap;
import com.datamanager.backend.entity.BaseReferenceTable;

import java.util.stream.Collectors;

/**
 * Mapper utility for converting between entities and DTOs
 */
public class MetadataMapper {

    private MetadataMapper() {
        // Utility class
    }

    /**
     * Convert BaseReferenceTable entity to TableMetadataDto
     */
    public static TableMetadataDto toDto(BaseReferenceTable entity) {
        if (entity == null) {
            return null;
        }

        return TableMetadataDto.builder()
                .id(entity.getId())
                .label(entity.getTblLabel())
                .physicalName(entity.getTblLink())
                .createdAt(entity.getAddTs())
                .createdBy(entity.getAddUsr())
                .updatedAt(entity.getUpdTs())
                .updatedBy(entity.getUpdUsr())
                .columns(entity.getColumns() != null ? entity.getColumns().stream()
                        .map(MetadataMapper::toDto)
                        .collect(Collectors.toList()) : null)
                .build();
    }

    /**
     * Convert BaseColumnMap entity to ColumnMetadataDto
     */
    public static ColumnMetadataDto toDto(BaseColumnMap entity) {
        if (entity == null) {
            return null;
        }

        return ColumnMetadataDto.builder()
                .id(entity.getId())
                .tableId(entity.getReferenceTable() != null ? entity.getReferenceTable().getId() : null)
                .label(entity.getColLabel())
                .physicalName(entity.getColLink())
                .tablePhysicalName(entity.getTblLink())
                .createdAt(entity.getAddTs())
                .createdBy(entity.getAddUsr())
                .updatedAt(entity.getUpdTs())
                .updatedBy(entity.getUpdUsr())
                .build();
    }
}
