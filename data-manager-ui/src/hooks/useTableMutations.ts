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
import { useTableEditStore } from '../stores/tableEditStore';

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
    saveDialogOpen: boolean;
    setSaveDialogOpen: (open: boolean) => void;
    limitWarningDialogOpen: boolean;
    setLimitWarningDialogOpen: (open: boolean) => void;
    newRowData: Record<string, any>;
    setNewRowData: (data: Record<string, any>) => void;
    savingRow: boolean;
    deletingRows: boolean;
    savingChanges: boolean;
    onCellValueChanged: (params: any, tableId: number, tableName: string, setError: (error: string | null) => void) => Promise<void>;
    handleBulkDelete: (tableId: number, tableName: string, gridApi: GridApi | null) => void;
    handleSaveNewRow: (tableId: number, tableName: string, gridApi: GridApi | null, setRowCount: (fn: (prev: number) => number) => void) => Promise<void>;
    batchSaveChanges: (tableId: number, tableName: string, gridApi: GridApi | null) => Promise<void>;
}

/**
 * Custom hook to manage table mutations (insert, update, delete)
 * Now uses Zustand store to track changes in memory before batch saving
 */
export function useTableMutations(): UseTableMutationsResult {
    const [snackbar, setSnackbar] = useState<SnackbarState>({
        open: false,
        message: '',
        severity: 'success'
    });
    const [selectedCount, setSelectedCount] = useState(0);
    const [addDialogOpen, setAddDialogOpen] = useState(false);
    const [saveDialogOpen, setSaveDialogOpen] = useState(false);
    const [limitWarningDialogOpen, setLimitWarningDialogOpen] = useState(false);
    const [newRowData, setNewRowData] = useState<Record<string, any>>({});
    const [savingRow, setSavingRow] = useState(false);
    const [deletingRows, setDeletingRows] = useState(false);
    const [savingChanges, setSavingChanges] = useState(false);

    // Get Zustand store actions
    const {
        updateCell,
        markForDelete,
        addInsert,
        removeInsert,
        clearChanges,
        getAllChanges,
        getPendingChangesCount
    } = useTableEditStore();

    // Cell Editing Handler - Now tracks changes in store instead of immediate save
    const onCellValueChanged = useCallback(async (
        params: any,
        tableId: number,
        tableName: string,
        setError: (error: string | null) => void
    ) => {
        const { data, colDef, newValue, oldValue } = params;
        if (newValue === oldValue) return;

        try {
            console.log(`Tracking update: row ${data.id}, col ${colDef.field} from ${oldValue} to ${newValue}`);

            // Track the change in Zustand store
            const success = updateCell(data.id, colDef.field, oldValue, newValue);

            if (!success) {
                // Limit reached
                setLimitWarningDialogOpen(true);
                // Revert the cell to old value
                params.node.setDataValue(colDef.field, oldValue);
                return;
            }

            setSnackbar({ open: true, message: 'Change tracked (not saved yet)', severity: 'info' });
        } catch (err) {
            console.error("Failed to track update", err);
            setError("Failed to track change");
            setSnackbar({ open: true, message: 'Failed to track change', severity: 'error' });
            // Revert the cell to old value
            params.node.setDataValue(colDef.field, oldValue);
        }
    }, [updateCell]);

    // Bulk Delete Handler - Mark rows for deletion immediately (no confirmation)
    const handleBulkDelete = useCallback(async (
        tableId: number,
        tableName: string,
        gridApi: GridApi | null
    ) => {
        const selectedNodes = gridApi?.getSelectedNodes();
        if (!selectedNodes || selectedNodes.length === 0) return;

        // Immediately mark for deletion
        try {
            setDeletingRows(true);
            const rowsToDelete = selectedNodes.map(node => node.data);
            let failedCount = 0;

            rowsToDelete.forEach(row => {
                const success = markForDelete(row.id);
                if (!success) failedCount++;
            });

            if (failedCount > 0) {
                setLimitWarningDialogOpen(true);
                gridApi?.refreshCells({ force: true });
                return;
            }

            gridApi?.refreshCells({ force: true });
            gridApi?.deselectAll();
            setSnackbar({
                open: true,
                message: `Marked ${rowsToDelete.length} row(s) for deletion (not saved yet)`,
                severity: 'info'
            });
        } catch (err) {
            console.error("Failed to mark for deletion", err);
            setSnackbar({ open: true, message: 'Failed to mark rows for deletion', severity: 'error' });
        } finally {
            setDeletingRows(false);
        }
    }, [markForDelete]);


    // Add Row Handler - Add empty row directly to grid (no dialog)
    const handleSaveNewRow = useCallback(async (
        tableId: number,
        tableName: string,
        gridApi: GridApi | null,
        setRowCount: (fn: (prev: number) => number) => void
    ) => {
        try {
            setSavingRow(true);

            // Generate temporary ID for new row
            const tempId = `temp_${Date.now()}_${Math.random()}`;

            // Track in store (empty data)
            const success = addInsert(tempId, {});

            if (!success) {
                // Limit reached
                setLimitWarningDialogOpen(true);
                return;
            }

            // Refresh the grid to show the new row from datasource
            if (gridApi) {
                gridApi.refreshServerSide({ purge: false });

                // Start editing the first editable cell of the new row
                setTimeout(() => {
                    const columns = gridApi.getColumns();
                    const firstEditableCol = columns?.find(col =>
                        col.getColDef().editable && col.getColId() !== 'id'
                    );
                    if (firstEditableCol) {
                        gridApi.startEditingCell({
                            rowIndex: 0,
                            colKey: firstEditableCol.getColId()
                        });
                    }
                }, 200);
            }

            setSnackbar({ open: true, message: 'Empty row added - start editing', severity: 'info' });
        } catch (err) {
            console.error("Failed to add row", err);
            setSnackbar({ open: true, message: 'Failed to add row', severity: 'error' });
        } finally {
            setSavingRow(false);
        }
    }, [addInsert]);

    // Batch Save All Changes
    const batchSaveChanges = useCallback(async (
        tableId: number,
        tableName: string,
        gridApi: GridApi | null
    ) => {
        try {
            setSavingChanges(true);

            const { inserts, updates, deletes } = getAllChanges();
            const totalChanges = inserts.length + updates.length + deletes.length;

            if (totalChanges === 0) {
                setSnackbar({ open: true, message: 'No changes to save', severity: 'info' });
                return;
            }

            console.log('Saving changes:', { inserts, updates, deletes });

            // 1. Process Inserts
            const insertPromises = inserts.map(async ({ tempId, data }) => {
                const result = await insertTableRow(tableId, data);
                const newRow = { ...data, id: result.id };
                await executeInsert(tableName, newRow);
                return { tempId, realId: result.id, data: newRow };
            });

            const insertResults = await Promise.all(insertPromises);

            // Update grid with real IDs
            if (gridApi) {
                insertResults.forEach(({ tempId, realId, data }) => {
                    // Remove temp row
                    gridApi.applyServerSideTransactionAsync({
                        remove: [{ id: tempId }]
                    });
                    // Add real row
                    gridApi.applyServerSideTransactionAsync({
                        add: [data]
                    });
                });
            }

            // 2. Process Updates
            const updatePromises = updates.map(async ({ rowId, changes }) => {
                const updateData: Record<string, any> = {};
                changes.forEach((cellUpdate, field) => {
                    updateData[field] = cellUpdate.newValue;
                });

                await updateTableRow(tableId, Number(rowId), updateData);

                // Update DuckDB for each field
                for (const [field, cellUpdate] of changes.entries()) {
                    await executeUpdate(tableName, Number(rowId), field, cellUpdate.newValue);
                }
            });

            await Promise.all(updatePromises);

            // 3. Process Deletes
            const deletePromises = deletes.map(async (rowId) => {
                await deleteTableRow(tableId, Number(rowId));
                await executeDelete(tableName, Number(rowId));
            });

            await Promise.all(deletePromises);

            // Remove deleted rows from grid
            if (gridApi && deletes.length > 0) {
                const rowsToRemove = deletes.map(id => ({ id }));
                gridApi.applyServerSideTransactionAsync({
                    remove: rowsToRemove
                });
            }

            // Clear the store
            clearChanges();

            // Refresh grid to remove styling
            if (gridApi) {
                gridApi.refreshCells({ force: true });
            }

            setSnackbar({
                open: true,
                message: `Saved ${totalChanges} change(s) successfully`,
                severity: 'success'
            });

        } catch (err) {
            console.error("Batch save failed", err);
            setSnackbar({ open: true, message: 'Failed to save changes', severity: 'error' });
        } finally {
            setSavingChanges(false);
        }
    }, [getAllChanges, clearChanges]);

    return {
        snackbar,
        setSnackbar,
        selectedCount,
        setSelectedCount,
        addDialogOpen,
        setAddDialogOpen,
        saveDialogOpen,
        setSaveDialogOpen,
        limitWarningDialogOpen,
        setLimitWarningDialogOpen,
        newRowData,
        setNewRowData,
        savingRow,
        deletingRows,
        savingChanges,
        onCellValueChanged,
        handleBulkDelete,
        handleSaveNewRow,
        batchSaveChanges
    };
}
