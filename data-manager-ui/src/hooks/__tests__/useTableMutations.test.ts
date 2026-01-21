import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTableMutations } from '../useTableMutations';
import * as api from '../../services/api';
import * as duckdb from '../../services/duckdb.service';
import { useTableEditStore } from '../../stores/tableEditStore';

vi.mock('../../services/api');
vi.mock('../../services/duckdb.service');
vi.mock('../../stores/tableEditStore');

const mockInsertTableRow = vi.mocked(api.insertTableRow);
const mockUpdateTableRow = vi.mocked(api.updateTableRow);
const mockDeleteTableRow = vi.mocked(api.deleteTableRow);
const mockQueryDuckDB = vi.mocked(duckdb.queryDuckDB);
const mockExecuteInsert = vi.mocked(duckdb.executeInsert);
const mockExecuteDelete = vi.mocked(duckdb.executeDelete);

describe('useTableMutations', () => {
  const mockUpdateCell = vi.fn();
  const mockMarkForDelete = vi.fn();
  const mockAddInsert = vi.fn();
  const mockRemoveInsert = vi.fn();
  const mockClearChanges = vi.fn();
  const mockGetAllChanges = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useTableEditStore as any).mockReturnValue({
      updateCell: mockUpdateCell,
      markForDelete: mockMarkForDelete,
      addInsert: mockAddInsert,
      removeInsert: mockRemoveInsert,
      clearChanges: mockClearChanges,
      getAllChanges: mockGetAllChanges,
    });
    mockUpdateCell.mockReturnValue(true);
    mockMarkForDelete.mockReturnValue(true);
    mockAddInsert.mockReturnValue(true);
    mockGetAllChanges.mockReturnValue({
      inserts: [],
      updates: [],
      deletes: [],
    });
    mockQueryDuckDB.mockResolvedValue([{ id: 1, name: 'Test' }]);
  });

  it('tracks cell changes in store', async () => {
    const { result } = renderHook(() => useTableMutations());

    const params = {
      data: { id: 1 },
      colDef: { field: 'name' },
      oldValue: 'Old',
      newValue: 'New',
      node: { setDataValue: vi.fn() },
    };

    await act(async () => {
      await result.current.onCellValueChanged(params, 1, 'test_table', vi.fn());
    });

    expect(mockUpdateCell).toHaveBeenCalledWith(1, 'name', 'Old', 'New');
  });

  it('reverts on limit reached', async () => {
    mockUpdateCell.mockReturnValue(false);
    const setDataValue = vi.fn();

    const { result } = renderHook(() => useTableMutations());

    const params = {
      data: { id: 1 },
      colDef: { field: 'name' },
      oldValue: 'Old',
      newValue: 'New',
      node: { setDataValue },
    };

    await act(async () => {
      await result.current.onCellValueChanged(params, 1, 'test_table', vi.fn());
    });

    expect(setDataValue).toHaveBeenCalledWith('name', 'Old');
    expect(result.current.limitWarningDialogOpen).toBe(true);
  });

  it('marks rows for deletion', async () => {
    const mockGridApi = {
      getSelectedNodes: vi.fn().mockReturnValue([
        { data: { id: 1 } },
        { data: { id: 2 } },
      ]),
      refreshCells: vi.fn(),
      deselectAll: vi.fn(),
    };

    const { result } = renderHook(() => useTableMutations());

    await act(async () => {
      await result.current.handleBulkDelete(1, 'test_table', mockGridApi as any);
    });

    expect(mockMarkForDelete).toHaveBeenCalledTimes(2);
    expect(mockGridApi.refreshCells).toHaveBeenCalled();
  });

  it('adds row to store', async () => {
    const mockGridApi = {
      clearFocusedCell: vi.fn(),
      refreshServerSide: vi.fn(),
      getColumns: vi.fn().mockReturnValue([
        { getColDef: () => ({ editable: true }), getColId: () => 'name' },
      ]),
      startEditingCell: vi.fn(),
    };

    const { result } = renderHook(() => useTableMutations());

    await act(async () => {
      await result.current.handleSaveNewRow(1, 'test_table', mockGridApi as any, vi.fn());
    });

    expect(mockAddInsert).toHaveBeenCalled();
    expect(mockGridApi.refreshServerSide).toHaveBeenCalled();
  });

  it('processes batch save with inserts', async () => {
    mockGetAllChanges.mockReturnValue({
      inserts: [{ tempId: 'temp1', data: { name: 'Test' } }],
      updates: [],
      deletes: [],
    });
    mockInsertTableRow.mockResolvedValue({
      id: 1,
      message: 'Inserted',
      add_usr: 'user',
      add_ts: '2024-01-01T00:00:00Z',
      upd_usr: 'user',
      upd_ts: '2024-01-01T00:00:00Z',
    });
    mockExecuteInsert.mockResolvedValue(undefined);

    const { result } = renderHook(() => useTableMutations());

    await act(async () => {
      await result.current.batchSaveChanges(1, 'test_table', null);
    });

    expect(mockInsertTableRow).toHaveBeenCalledWith(1, { name: 'Test' });
    expect(mockExecuteInsert).toHaveBeenCalled();
    expect(mockClearChanges).toHaveBeenCalled();
  });

  it('processes batch save with updates', async () => {
    mockGetAllChanges.mockReturnValue({
      inserts: [],
      updates: [
        {
          rowId: 1,
          changes: new Map([['name', { field: 'name', oldValue: 'Old', newValue: 'New' }]]),
        },
      ],
      deletes: [],
    });
    mockQueryDuckDB.mockResolvedValue([{ id: 1, name: 'Old' }]);
    mockUpdateTableRow.mockResolvedValue({
      message: 'Updated',
      add_usr: 'user',
      add_ts: '2024-01-01T00:00:00Z',
      upd_usr: 'user',
      upd_ts: '2024-01-01T00:00:00Z',
    });
    mockExecuteDelete.mockResolvedValue(undefined);
    mockExecuteInsert.mockResolvedValue(undefined);

    const { result } = renderHook(() => useTableMutations());

    await act(async () => {
      await result.current.batchSaveChanges(1, 'test_table', null);
    });

    expect(mockUpdateTableRow).toHaveBeenCalledWith(1, 1, { name: 'New' });
    expect(mockExecuteDelete).toHaveBeenCalled();
    expect(mockExecuteInsert).toHaveBeenCalled();
  });

  it('processes batch save with deletes', async () => {
    mockGetAllChanges.mockReturnValue({
      inserts: [],
      updates: [],
      deletes: [1, 2],
    });
    mockDeleteTableRow.mockResolvedValue(undefined);
    mockExecuteDelete.mockResolvedValue(undefined);

    const mockGridApi = {
      applyServerSideTransactionAsync: vi.fn(),
    };

    const { result } = renderHook(() => useTableMutations());

    await act(async () => {
      await result.current.batchSaveChanges(1, 'test_table', mockGridApi as any);
    });

    expect(mockDeleteTableRow).toHaveBeenCalledTimes(2);
    expect(mockExecuteDelete).toHaveBeenCalledTimes(2);
    expect(mockClearChanges).toHaveBeenCalled();
  });
});

