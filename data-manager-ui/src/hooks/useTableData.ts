import { useState, useCallback, useRef, useEffect } from 'react';
import {
    fetchTableById,
    fetchTableColumns,
    TableMetadata,
    ColumnMetadata
} from '../services/api';
import {
    loadTableDataIntoDuckDBArrow,
    tableExistsInDuckDB,
    queryDuckDB,
    dropTable
} from '../services/duckdb.service';

interface UseTableDataResult {
    tableInfo: TableMetadata | null;
    columns: ColumnMetadata[];
    loading: boolean;
    loadingData: boolean;
    loadProgress: string;
    loadProgressPercent: number;
    error: string | null;
    rowCount: number;
    dataLoaded: boolean;
    loadDataIntoDuckDB: (force?: boolean) => Promise<void>;
    setError: (error: string | null) => void;
}

/**
 * Custom hook to manage table metadata and data loading
 */
export function useTableData(tableId: string | undefined): UseTableDataResult {
    const [tableInfo, setTableInfo] = useState<TableMetadata | null>(null);
    const [columns, setColumns] = useState<ColumnMetadata[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadingData, setLoadingData] = useState(false);
    const [loadProgress, setLoadProgress] = useState<string>('');
    const [loadProgressPercent, setLoadProgressPercent] = useState<number>(0);
    const [error, setError] = useState<string | null>(null);
    const [rowCount, setRowCount] = useState<number>(0);
    const [dataLoaded, setDataLoaded] = useState(false);

    // Load table metadata
    const loadTableMetadata = useCallback(async () => {
        if (!tableId) return;

        try {
            setLoading(true);
            const [table, cols] = await Promise.all([
                fetchTableById(Number(tableId)),
                fetchTableColumns(Number(tableId))
            ]);

            setTableInfo(table);
            setColumns(cols);
            setLoading(false);
        } catch (err) {
            console.error("Error loading table metadata", err);
            setError("Failed to load table metadata");
            setLoading(false);
        }
    }, [tableId]);

    // Load data into DuckDB
    const loadDataIntoDuckDB = useCallback(async (force: boolean = false) => {
        if (!tableId || !tableInfo) return;

        try {
            setLoadingData(true);
            setLoadProgress('Connecting to server...');
            setLoadProgressPercent(0);

            console.time('⏱️ Total data load');

            if (force) {
                console.log(`♻️ Force refresh requested. Dropping table ${tableInfo.physicalName}...`);
                await dropTable(tableInfo.physicalName);
                setDataLoaded(false);
                setRowCount(0);
            }

            // Check if already loaded (unless forcing)
            if (!force) {
                const exists = await tableExistsInDuckDB(tableInfo.physicalName);
                if (exists) {
                    setLoadProgress('');
                    setDataLoaded(true);
                    setLoadingData(false);
                    setError(null);

                    // Get row count
                    const result = await queryDuckDB(`SELECT COUNT(*) as count FROM "${tableInfo.physicalName}"`);
                    setRowCount(Number(result[0].count));

                    console.log(`✅ Table already loaded: ${tableInfo.physicalName} (${result[0].count} rows)`);
                    return;
                }
            }

            // Load data with progressive streaming
            let isFirstBatch = true;
            const { rowCount: finalCount } = await loadTableDataIntoDuckDBArrow(
                Number(tableId),
                tableInfo.physicalName,
                (bytesLoaded, totalBytes, phase, currentRows) => {
                    // Update progress based on phase
                    if (totalBytes) {
                        const percent = Math.round((bytesLoaded / totalBytes) * 100);
                        setLoadProgressPercent(percent);
                    }

                    if (phase === 'downloading') {
                        if (totalBytes) {
                            setLoadProgress(`Downloading: ${(bytesLoaded / 1024 / 1024).toFixed(1)} MB / ${(totalBytes / 1024 / 1024).toFixed(1)} MB`);
                        } else {
                            setLoadProgress(`Downloading: ${(bytesLoaded / 1024 / 1024).toFixed(1)} MB...`);
                        }
                    } else if (phase === 'inserting') {
                        // Show grid after first batch!
                        if (isFirstBatch && currentRows && currentRows > 0) {
                            console.log(`🎉 First batch loaded! Showing grid with ${currentRows} rows...`);
                            setDataLoaded(true);
                            setRowCount(currentRows);
                            isFirstBatch = false;
                        } else if (currentRows) {
                            // Update row count as more batches arrive
                            setRowCount(currentRows);
                        }
                        setLoadProgress(`Loading data: ${currentRows?.toLocaleString() || 0} rows...`);
                    } else if (phase === 'complete') {
                        setLoadProgress('');
                    }
                }
            );

            // Final row count update
            setRowCount(finalCount);
            setLoadProgress('');
            setLoadProgressPercent(100);
            setLoadingData(false);
            setError(null);

            console.timeEnd('⏱️ Total data load');

        } catch (err) {
            console.error("Error loading data into DuckDB", err);
            setError("Failed to load data");
            setLoadingData(false);
            setLoadProgress('');
            setLoadProgressPercent(0);
        }
    }, [tableId, tableInfo]);

    // Load metadata on mount
    useEffect(() => {
        loadTableMetadata();
    }, [loadTableMetadata]);

    // Use ref to prevent duplicate loads from React StrictMode
    const loadingRef = useRef(false);

    useEffect(() => {
        if (tableInfo && !loadingRef.current) {
            loadingRef.current = true;
            loadDataIntoDuckDB().finally(() => {
                loadingRef.current = false;
            });
        }
    }, [tableInfo, loadDataIntoDuckDB]);

    return {
        tableInfo,
        columns,
        loading,
        loadingData,
        loadProgress,
        loadProgressPercent,
        error,
        rowCount,
        dataLoaded,
        loadDataIntoDuckDB,
        setError
    };
}
