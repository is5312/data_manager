package com.datamanager.backend.service;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Service interface for streaming data operations
 * Handles CSV and Arrow format streaming for large datasets
 */
public interface StreamingDataService {

    /**
     * Stream all rows from a table as CSV using PostgreSQL COPY command
     * 
     * @param tableId Logical table ID
     * @return StreamingResponseBody that streams CSV data
     * @throws IllegalArgumentException if table not found
     */
    StreamingResponseBody streamTableDataAsCsv(Long tableId);

    /**
     * Stream all rows from a table as Apache Arrow IPC format
     * 
     * @param tableId Logical table ID
     * @return StreamingResponseBody that streams Arrow binary data
     * @throws IllegalArgumentException if table not found
     */
    StreamingResponseBody streamTableDataAsArrow(Long tableId);

    /**
     * Get total row count for a table (for progress tracking)
     * 
     * @param tableId Logical table ID
     * @return Total number of rows in the table
     * @throws IllegalArgumentException if table not found
     */
    long getTableRowCount(Long tableId);
}

