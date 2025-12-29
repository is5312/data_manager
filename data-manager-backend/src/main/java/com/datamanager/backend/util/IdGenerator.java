package com.datamanager.backend.util;

import com.github.f4b6a3.ulid.UlidCreator;

public class IdGenerator {

    /**
     * Generate a new Monotonic ULID.
     * Monotonic ULIDs ensure strict ordering even when generated within the same
     * millisecond.
     * 
     * @return A valid ULID string (e.g., "01ARZ3NDEKTSV4RRFFQ69G5FAV")
     */
    public static String generateUlid() {
        // Use lowercase to avoid Postgres case-folding surprises when building dynamic SQL.
        return UlidCreator.getMonotonicUlid().toString().toLowerCase();
    }

    /**
     * Generate a physical table name using ULID standard.
     * Format: tbl_<ULID>
     * 
     * @return formatted table name string
     */
    public static String generatePhysicalTableName() {
        return "tbl_" + generateUlid();
    }
}
