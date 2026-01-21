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
    executeDelete,
    queryDuckDB
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

            // Refresh grid view (without purging cache if possible to keep DuckDB data)
            if (gridApi) {
                // Clear focused cell if method exists (AG Grid API may vary by version)
                if (gridApi.clearFocusedCell) {
                    gridApi.clearFocusedCell();
                }
                // Important: purge: false tells AG Grid to keep existing data and just refresh rows
                // Since we already updated DuckDB, it should pull from there
                gridApi.refreshServerSide({ purge: false });
            }     // Start editing the first editable cell of the new row
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

            let insertResults: Array<{ tempId: string | number; realId: number; data: any }> = [];

            // Helper to convert ISO timestamp strings to Date objects for DuckDB TIMESTAMP_MS
            const toTimestamp = (isoString: string | null | undefined): Date | null => {
                if (!isoString) return null;
                return new Date(isoString);
            };

            // 1. Process Inserts (only if there are any)
            if (inserts.length > 0) {
                const insertPromises = inserts.map(async ({ tempId, data }) => {
                    const result = await insertTableRow(tableId, data);


                    // Build complete row with user data + audit columns from backend
                    // Convert ISO timestamp strings to Date objects for DuckDB TIMESTAMP_MS
                    const completeRow = {
                        ...data,
                        id: result.id,
                        add_usr: (result as any).add_usr,
                        add_ts: toTimestamp((result as any).add_ts),
                        upd_usr: (result as any).upd_usr,
                        upd_ts: toTimestamp((result as any).upd_ts)
                    };

                    // Insert complete row into DuckDB
                    await executeInsert(tableName, completeRow);

                    return { tempId, realId: result.id, data: completeRow };
                });

                insertResults = await Promise.all(insertPromises);
            }

            // 2. Process Updates (only if there are any)
            if (updates.length > 0) {
                const updatePromises = updates.map(async ({ rowId, changes }) => {
                    const updateData: Record<string, any> = {};
                    changes.forEach((cellUpdate, field) => {
                        updateData[field] = cellUpdate.newValue;
                    });

                    // Fetch original row from DuckDB
                    const originalRows = await queryDuckDB(`SELECT * FROM "${tableName}" WHERE id = ${rowId}`);
                    const originalRow = originalRows[0] || {};

                    const updatedRow = await updateTableRow(tableId, Number(rowId), updateData);

                    // Build complete row with updated data + audit columns from backend
                    // Convert ISO timestamp strings to Date objects for DuckDB TIMESTAMP_MS
                    const completeRow = {
                        ...originalRow,
                        ...updateData,
                        add_usr: (updatedRow as any).add_usr,
                        add_ts: toTimestamp((updatedRow as any).add_ts),
                        upd_usr: (updatedRow as any).upd_usr,
                        upd_ts: toTimestamp((updatedRow as any).upd_ts)
                    };

                    // Update DuckDB with complete row data (includes audit columns)
                    await executeDelete(tableName, Number(rowId));
                    await executeInsert(tableName, completeRow);
                });

                await Promise.all(updatePromises);
            }

            // 3. Process Deletes (only if there are any)
            if (deletes.length > 0) {
                const deletePromises = deletes.map(async (rowId) => {
                    await deleteTableRow(tableId, Number(rowId));
                    await executeDelete(tableName, Number(rowId));
                });

                await Promise.all(deletePromises);

                // Remove deleted rows from grid
                if (gridApi) {
                    const rowsToRemove = deletes.map(id => ({ id }));
                    gridApi.applyServerSideTransactionAsync({
                        remove: rowsToRemove
                    });
                }
            }
            // Clear the store
            // Clear the store
            clearChanges();

            // Refresh grid to show changes (force purge to get fresh data from DuckDB)
            if (gridApi) {
                // Clear focused cell if method exists (AG Grid API may vary by version)
                if (gridApi.clearFocusedCell) {
                    gridApi.clearFocusedCell();
                }
                gridApi.refreshServerSide({ purge: true });
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
