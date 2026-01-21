package com.datamanager.backend.repository;

import com.datamanager.backend.entity.BaseReferenceTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for BaseReferenceTable entity
 * Provides CRUD operations and custom queries for metadata tables
 */
@Repository
public interface BaseReferenceTableRepository extends JpaRepository<BaseReferenceTable, Long> {

    /**
     * Find a table by its physical name (tbl_link)
     */
    Optional<BaseReferenceTable> findByTblLink(String tblLink);

    /**
     * Find a table by its logical label
     */
    Optional<BaseReferenceTable> findByTblLabel(String tblLabel);

    /**
     * Check if a table exists by physical name
     */
    boolean existsByTblLink(String tblLink);

    /**
     * Check if a table exists by logical label
     */
    boolean existsByTblLabel(String tblLabel);

    /**
     * Find all tables with their columns (fetch join to avoid N+1 queries)
     */
    @Query("SELECT DISTINCT t FROM BaseReferenceTable t LEFT JOIN FETCH t.columns")
    java.util.List<BaseReferenceTable> findAllWithColumns();

    /**
     * Find all tables ordered by most recently updated or created
     * Orders by updatedAt DESC (nulls last), then by createdAt DESC
     * Uses CASE to handle null updTs values
     */
    @Query("SELECT t FROM BaseReferenceTable t ORDER BY " +
           "CASE WHEN t.updTs IS NOT NULL THEN t.updTs ELSE t.addTs END DESC, " +
           "t.addTs DESC")
    java.util.List<BaseReferenceTable> findAllOrderByMostRecent();
}
