import { useState, useCallback } from 'react';
import { GridApi } from 'ag-grid-community';
import {
    updateTableRow,
    insertTableRow,
    deleteTableRow
} from '../services/api';
import {
    executeUpdate,
    executeInsert,
    executeDelete
} from '../services/duckdb.service';

interface SnackbarState {
    open: boolean;
    message: string;
    severity: 'success' | 'error' | 'info';
}

interface UseTableMutationsResult {
    snackbar: SnackbarState;
    setSnackbar: (state: SnackbarState) => void;
    selectedCount: number;
    setSelectedCount: (count: number) => void;
    addDialogOpen: boolean;
    setAddDialogOpen: (open: boolean) => void;
    deleteDialogOpen: boolean;
    setDeleteDialogOpen: (open: boolean) => void;
    newRowData: Record<string, any>;
    setNewRowData: (data: Record<string, any>) => void;
    savingRow: boolean;
    deletingRows: boolean;
    onCellValueChanged: (params: any, tableId: number, tableName: string, setError: (error: string | null) => void) => Promise<void>;
    handleBulkDelete: (gridApi: GridApi | null) => void;
    confirmDelete: (tableId: number, tableName: string, gridApi: GridApi | null, setRowCount: (fn: (prev: number) => number) => void) => Promise<void>;
    handleSaveNewRow: (tableId: number, tableName: string, gridApi: GridApi | null, setRowCount: (fn: (prev: number) => number) => void) => Promise<void>;
}

/**
 * Custom hook to manage table mutations (insert, update, delete)
 */
export function useTableMutations(): UseTableMutationsResult {
    const [snackbar, setSnackbar] = useState<SnackbarState>({
        open: false,
        message: '',
        severity: 'success'
    });
    const [selectedCount, setSelectedCount] = useState(0);
    const [addDialogOpen, setAddDialogOpen] = useState(false);
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [newRowData, setNewRowData] = useState<Record<string, any>>({});
    const [savingRow, setSavingRow] = useState(false);
    const [deletingRows, setDeletingRows] = useState(false);

    // Cell Editing Handler
    const onCellValueChanged = useCallback(async (
        params: any,
        tableId: number,
        tableName: string,
        setError: (error: string | null) => void
    ) => {
        const { data, colDef, newValue, oldValue } = params;
        if (newValue === oldValue) return;

        try {
            console.log(`Updating row ${data.id}, col ${colDef.field} to ${newValue}`);

            // 1. Update Backend
            await updateTableRow(tableId, data.id, { [colDef.field]: newValue });

            // 2. Update DuckDB (Local)
            await executeUpdate(tableName, data.id, colDef.field, newValue);

            setSnackbar({ open: true, message: 'Saved', severity: 'success' });
        } catch (err) {
            console.error("Update failed", err);
            setError("Failed to save change");
            setSnackbar({ open: true, message: 'Failed to save change', severity: 'error' });
            // Revert the cell to old value
            params.node.setDataValue(colDef.field, oldValue);
        }
    }, []);

    // Bulk Delete Handler - Open Dialog
    const handleBulkDelete = useCallback((gridApi: GridApi | null) => {
        const selectedNodes = gridApi?.getSelectedNodes();
        if (!selectedNodes || selectedNodes.length === 0) return;
        setDeleteDialogOpen(true);
    }, []);

    // Bulk Delete Action - Execute Delete
    const confirmDelete = useCallback(async (
        tableId: number,
        tableName: string,
        gridApi: GridApi | null,
        setRowCount: (fn: (prev: number) => number) => void
    ) => {
        if (!gridApi) return;

        const selectedNodes = gridApi.getSelectedNodes();
        if (selectedNodes.length === 0) {
            setDeleteDialogOpen(false);
            return;
        }

        try {
            setDeletingRows(true);

            // Get the data to delete (capture before operations)
            const rowsToDelete = selectedNodes.map(node => node.data);
            const idsToDelete = rowsToDelete.map(row => row.id);

            // Delete from backend/DuckDB
            await Promise.all(idsToDelete.map(async (rowId) => {
                await deleteTableRow(tableId, rowId);
                await executeDelete(tableName, rowId);
            }));

            // Remove from grid using transaction (no full refresh!)
            gridApi.applyServerSideTransactionAsync({
                remove: rowsToDelete
            }, (res) => {
                console.log('Delete transaction applied:', res);
            });

            gridApi.deselectAll();

            setRowCount(prev => prev - idsToDelete.length);
            setSnackbar({ open: true, message: `Deleted ${idsToDelete.length} rows`, severity: 'success' });
        } catch (err) {
            console.error("Delete failed", err);
            setSnackbar({ open: true, message: 'Failed to delete rows', severity: 'error' });
        } finally {
            setDeletingRows(false);
            setDeleteDialogOpen(false);
        }
    }, []);

    // Add Row Handler
    const handleSaveNewRow = useCallback(async (
        tableId: number,
        tableName: string,
        gridApi: GridApi | null,
        setRowCount: (fn: (prev: number) => number) => void
    ) => {
        try {
            setSavingRow(true);

            // 1. Insert into Backend
            const result = await insertTableRow(tableId, newRowData);

            // 2. Insert into DuckDB (including the new ID)
            const newRow = { ...newRowData, id: result.id };
            await executeInsert(tableName, newRow);

            // 3. Add to Grid using transaction (no full refresh!)
            if (gridApi) {
                gridApi.applyServerSideTransactionAsync({
                    add: [newRow]
                }, (res) => {
                    console.log('Add transaction applied:', res);
                });
            }

            setRowCount(prev => prev + 1);
            setAddDialogOpen(false);
            setNewRowData({});
            setSnackbar({ open: true, message: 'Row added', severity: 'success' });
        } catch (err) {
            console.error("Insert failed", err);
            setSnackbar({ open: true, message: 'Failed to add row', severity: 'error' });
        } finally {
            setSavingRow(false);
        }
    }, [newRowData]);

    return {
        snackbar,
        setSnackbar,
        selectedCount,
        setSelectedCount,
        addDialogOpen,
        setAddDialogOpen,
        deleteDialogOpen,
        setDeleteDialogOpen,
        newRowData,
        setNewRowData,
        savingRow,
        deletingRows,
        onCellValueChanged,
        handleBulkDelete,
        confirmDelete,
        handleSaveNewRow
    };
}
