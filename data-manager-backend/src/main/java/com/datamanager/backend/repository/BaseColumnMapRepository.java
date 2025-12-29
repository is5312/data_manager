package com.datamanager.backend.repository;

import com.datamanager.backend.entity.BaseColumnMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for BaseColumnMap entity
 * Provides CRUD operations and custom queries for column metadata
 */
@Repository
public interface BaseColumnMapRepository extends JpaRepository<BaseColumnMap, Long> {

    /**
     * Find all columns for a specific table
     */
    List<BaseColumnMap> findByReferenceTableId(Long tableId);

    /**
     * Find a column by its physical name and table
     */
    Optional<BaseColumnMap> findByColLinkAndReferenceTableId(String colLink, Long tableId);

    /**
     * Find a column by its logical label and table
     */
    Optional<BaseColumnMap> findByColLabelAndReferenceTableId(String colLabel, Long tableId);

    /**
     * Delete all columns for a specific table
     */
    void deleteByReferenceTableId(Long tableId);

    /**
     * Check if a column exists by physical name and table
     */
    boolean existsByColLinkAndReferenceTableId(String colLink, Long tableId);
}
