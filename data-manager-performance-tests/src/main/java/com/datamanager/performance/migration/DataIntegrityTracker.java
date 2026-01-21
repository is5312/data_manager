package com.datamanager.performance.migration;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe tracker for monitoring data integrity during migration tests
 * Tracks all inserted row IDs with timestamps to verify no data loss
 */
@Slf4j
public class DataIntegrityTracker {

    private final Map<Long, InsertRecord> insertedRows = new ConcurrentHashMap<>();
    private final AtomicLong initialRowCount = new AtomicLong(0);
    private volatile Instant migrationStartTime;
    private volatile Instant migrationEndTime;
    private volatile boolean migrationInProgress = false;

    /**
     * Record an inserted row
     */
    public void recordInsert(Long rowId, Instant timestamp) {
        if (rowId != null) {
            insertedRows.put(rowId, new InsertRecord(rowId, timestamp, migrationInProgress));
        }
    }

    /**
     * Set initial row count (before migration starts)
     */
    public void setInitialRowCount(long count) {
        initialRowCount.set(count);
        log.info("Initial row count set to: {}", count);
    }

    /**
     * Mark migration start
     */
    public void markMigrationStart() {
        migrationStartTime = Instant.now();
        migrationInProgress = true;
        log.info("Migration started at: {}", migrationStartTime);
    }

    /**
     * Mark migration end
     */
    public void markMigrationEnd() {
        migrationEndTime = Instant.now();
        migrationInProgress = false;
        log.info("Migration ended at: {} (duration: {}ms)", 
                migrationEndTime, 
                migrationEndTime.toEpochMilli() - migrationStartTime.toEpochMilli());
    }

    /**
     * Get all inserted row IDs
     */
    public Set<Long> getAllInsertedRowIds() {
        return Collections.unmodifiableSet(insertedRows.keySet());
    }

    /**
     * Get expected total row count
     */
    public long getExpectedRowCount() {
        return initialRowCount.get() + insertedRows.size();
    }

    /**
     * Get statistics about inserts
     */
    public InsertStatistics getStatistics() {
        long beforeMigration = insertedRows.values().stream()
                .filter(r -> !r.duringMigration)
                .count();
        long duringMigration = insertedRows.values().stream()
                .filter(r -> r.duringMigration)
                .count();
        long afterMigration = insertedRows.values().stream()
                .filter(r -> r.timestamp.isAfter(migrationEndTime != null ? migrationEndTime : Instant.now()))
                .count();

        return new InsertStatistics(
                initialRowCount.get(),
                insertedRows.size(),
                beforeMigration,
                duringMigration,
                afterMigration,
                migrationStartTime,
                migrationEndTime
        );
    }

    /**
     * Verify data integrity by comparing expected vs actual row counts
     */
    public IntegrityReport verifyIntegrity(long actualRowCount, Set<Long> actualRowIds) {
        long expectedCount = getExpectedRowCount();
        Set<Long> missingIds = new HashSet<>(insertedRows.keySet());
        missingIds.removeAll(actualRowIds);

        boolean dataLoss = !missingIds.isEmpty();
        boolean countMismatch = expectedCount != actualRowCount;

        return new IntegrityReport(
                expectedCount,
                actualRowCount,
                insertedRows.size(),
                missingIds,
                dataLoss,
                countMismatch,
                getStatistics()
        );
    }

    /**
     * Reset tracker for new test run
     */
    public void reset() {
        insertedRows.clear();
        initialRowCount.set(0);
        migrationStartTime = null;
        migrationEndTime = null;
        migrationInProgress = false;
    }

    /**
     * Record of an inserted row
     */
    private static class InsertRecord {
        final Long rowId;
        final Instant timestamp;
        final boolean duringMigration;

        InsertRecord(Long rowId, Instant timestamp, boolean duringMigration) {
            this.rowId = rowId;
            this.timestamp = timestamp;
            this.duringMigration = duringMigration;
        }
    }

    /**
     * Statistics about inserts
     */
    public static class InsertStatistics {
        public final long initialRows;
        public final long totalInserts;
        public final long insertsBeforeMigration;
        public final long insertsDuringMigration;
        public final long insertsAfterMigration;
        public final Instant migrationStartTime;
        public final Instant migrationEndTime;

        public InsertStatistics(long initialRows, long totalInserts, long insertsBeforeMigration,
                               long insertsDuringMigration, long insertsAfterMigration,
                               Instant migrationStartTime, Instant migrationEndTime) {
            this.initialRows = initialRows;
            this.totalInserts = totalInserts;
            this.insertsBeforeMigration = insertsBeforeMigration;
            this.insertsDuringMigration = insertsDuringMigration;
            this.insertsAfterMigration = insertsAfterMigration;
            this.migrationStartTime = migrationStartTime;
            this.migrationEndTime = migrationEndTime;
        }
    }

    /**
     * Integrity verification report
     */
    public static class IntegrityReport {
        public final long expectedRowCount;
        public final long actualRowCount;
        public final long trackedInserts;
        public final Set<Long> missingRowIds;
        public final boolean dataLoss;
        public final boolean countMismatch;
        public final InsertStatistics statistics;

        public IntegrityReport(long expectedRowCount, long actualRowCount, long trackedInserts,
                              Set<Long> missingRowIds, boolean dataLoss, boolean countMismatch,
                              InsertStatistics statistics) {
            this.expectedRowCount = expectedRowCount;
            this.actualRowCount = actualRowCount;
            this.trackedInserts = trackedInserts;
            this.missingRowIds = missingRowIds;
            this.dataLoss = dataLoss;
            this.countMismatch = countMismatch;
            this.statistics = statistics;
        }

        public boolean isIntegrityValid() {
            return !dataLoss && !countMismatch;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Integrity Report:\n");
            sb.append("  Expected Row Count: ").append(expectedRowCount).append("\n");
            sb.append("  Actual Row Count: ").append(actualRowCount).append("\n");
            sb.append("  Tracked Inserts: ").append(trackedInserts).append("\n");
            sb.append("  Data Loss: ").append(dataLoss).append("\n");
            sb.append("  Count Mismatch: ").append(countMismatch).append("\n");
            if (!missingRowIds.isEmpty()) {
                sb.append("  Missing Row IDs: ").append(missingRowIds.size()).append(" rows\n");
                if (missingRowIds.size() <= 10) {
                    sb.append("    ").append(missingRowIds).append("\n");
                }
            }
            return sb.toString();
        }
    }
}
