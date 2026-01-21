import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useTableData } from '../useTableData';
import * as api from '../../services/api';
import * as duckdb from '../../services/duckdb.service';

vi.mock('../../services/api');
vi.mock('../../services/duckdb.service');

const mockFetchTableById = vi.mocked(api.fetchTableById);
const mockFetchTableColumns = vi.mocked(api.fetchTableColumns);
const mockTableExistsInDuckDB = vi.mocked(duckdb.tableExistsInDuckDB);
const mockLoadTableDataIntoDuckDBArrow = vi.mocked(duckdb.loadTableDataIntoDuckDBArrow);
const mockQueryDuckDB = vi.mocked(duckdb.queryDuckDB);
const mockDropTable = vi.mocked(duckdb.dropTable);

describe('useTableData', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchTableById.mockResolvedValue({
      id: 1,
      label: 'Test Table',
      physicalName: 'test_table',
      versionNo: 1,
    });
    mockFetchTableColumns.mockResolvedValue([
      {
        id: 1,
        tableId: 1,
        label: 'Name',
        physicalName: 'name',
        tablePhysicalName: 'test_table',
        type: 'VARCHAR',
      },
    ]);
    mockTableExistsInDuckDB.mockResolvedValue(false);
    mockLoadTableDataIntoDuckDBArrow.mockResolvedValue({ rowCount: 100, columnCount: 2 });
    mockQueryDuckDB.mockResolvedValue([{ count: 100 }]);
  });

  it('fetches table metadata', async () => {
    const { result } = renderHook(() => useTableData('1'));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(mockFetchTableById).toHaveBeenCalledWith(1);
    expect(mockFetchTableColumns).toHaveBeenCalledWith(1);
    expect(result.current.tableInfo).toBeTruthy();
    expect(result.current.columns).toHaveLength(1);
  });

  it('fetches column metadata', async () => {
    const { result } = renderHook(() => useTableData('1'));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.columns).toHaveLength(1);
    expect(result.current.columns[0].label).toBe('Name');
  });

  it('handles loading states', async () => {
    const { result } = renderHook(() => useTableData('1'));

    // Initially loading
    expect(result.current.loading).toBe(true);

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
  });

  it('handles errors', async () => {
    mockFetchTableById.mockRejectedValue(new Error('API Error'));

    const { result } = renderHook(() => useTableData('1'));

    await waitFor(() => {
      expect(result.current.error).toBeTruthy();
    });

    expect(result.current.error).toContain('Failed to load table metadata');
  });

  it('loads data into DuckDB', async () => {
    mockLoadTableDataIntoDuckDBArrow.mockImplementation(async (id, name, callback) => {
      // Simulate progress callbacks as the implementation expects
      if (callback) {
        callback(0, 1000, 'downloading', 0);
        callback(1000, 1000, 'inserting', 50); // First batch triggers dataLoaded
        callback(1000, 1000, 'complete', 100);
      }
      return { rowCount: 100, columnCount: 2 };
    });
    
    const { result } = renderHook(() => useTableData('1'));

    // Wait for metadata to load first
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    // Trigger data load
    await result.current.loadDataIntoDuckDB();

    await waitFor(() => {
      expect(result.current.dataLoaded).toBe(true);
    }, { timeout: 3000 });

    expect(mockLoadTableDataIntoDuckDBArrow).toHaveBeenCalled();
  });

  it('skips reload if already loaded', async () => {
    mockTableExistsInDuckDB.mockResolvedValue(true);
    mockQueryDuckDB.mockResolvedValue([{ count: 50 }]);

    const { result } = renderHook(() => useTableData('1'));

    await waitFor(() => {
      expect(result.current.dataLoaded).toBe(true);
    });

    // Should not call loadTableDataIntoDuckDBArrow if already loaded
    expect(mockLoadTableDataIntoDuckDBArrow).not.toHaveBeenCalled();
    expect(result.current.rowCount).toBe(50);
  });

  it('shows progress during streaming', async () => {
    let progressCallback: any;
    mockLoadTableDataIntoDuckDBArrow.mockImplementation(async (id, name, callback) => {
      progressCallback = callback;
      if (callback) {
        callback(0, 1000, 'downloading', 0);
        callback(500, 1000, 'downloading', 0);
        callback(1000, 1000, 'inserting', 50); // First batch sets dataLoaded
        callback(1000, 1000, 'complete', 100);
      }
      return { rowCount: 100, columnCount: 2 };
    });

    const { result } = renderHook(() => useTableData('1'));

    // Wait for metadata
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    // Trigger data load
    await result.current.loadDataIntoDuckDB();

    await waitFor(() => {
      expect(result.current.dataLoaded).toBe(true);
    });

    // Progress should have been set during streaming
    expect(mockLoadTableDataIntoDuckDBArrow).toHaveBeenCalled();
  });

  it('updates row count', async () => {
    mockLoadTableDataIntoDuckDBArrow.mockImplementation(async (id, name, callback) => {
      if (callback) {
        callback(1000, 1000, 'inserting', 50);
        callback(1000, 1000, 'complete', 100);
      }
      return { rowCount: 100, columnCount: 2 };
    });

    const { result } = renderHook(() => useTableData('1'));

    // Wait for metadata
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    // Trigger data load
    await result.current.loadDataIntoDuckDB();

    await waitFor(() => {
      expect(result.current.dataLoaded).toBe(true);
    });

    expect(result.current.rowCount).toBe(100);
  });

  it('drops existing table on force refresh', async () => {
    const mockDropTable = vi.mocked(duckdb.dropTable);
    mockLoadTableDataIntoDuckDBArrow.mockImplementation(async (id, name, callback) => {
      if (callback) {
        callback(1000, 1000, 'inserting', 50);
        callback(1000, 1000, 'complete', 100);
      }
      return { rowCount: 100, columnCount: 2 };
    });

    const { result } = renderHook(() => useTableData('1'));

    // Wait for metadata
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    // Force refresh should drop table
    await result.current.loadDataIntoDuckDB(true);

    await waitFor(() => {
      expect(result.current.dataLoaded).toBe(true);
    });

    await result.current.loadDataIntoDuckDB(true);

    expect(mockDropTable).toHaveBeenCalledWith('test_table');
    expect(mockLoadTableDataIntoDuckDBArrow).toHaveBeenCalled();
  });
});

