import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import { enableMapSet } from 'immer';

// Enable Immer's MapSet plugin to support Map and Set
enableMapSet();

// Maximum number of pending changes allowed in memory
export const MAX_PENDING_CHANGES = 50;

export type ChangeType = 'insert' | 'update' | 'delete';

export interface CellUpdate {
    field: string;
    oldValue: any;
    newValue: any;
}

export interface PendingChange {
    type: ChangeType;
    rowId: string | number;
    data?: Record<string, any>;
    updates?: Map<string, CellUpdate>;
}

interface TableEditState {
    // Track inserted rows (temporary IDs -> row data)
    insertedRows: Map<string, Record<string, any>>;

    // Track updated cells (rowId -> field -> {oldValue, newValue})
    updatedCells: Map<string | number, Map<string, CellUpdate>>;

    // Track deleted rows (set of row IDs)
    deletedRows: Set<string | number>;

    // Actions
    addInsert: (tempId: string, rowData: Record<string, any>) => boolean;
    updateCell: (rowId: string | number, field: string, oldValue: any, newValue: any) => boolean;
    markForDelete: (rowId: string | number) => boolean;
    unmarkForDelete: (rowId: string | number) => void;
    removeInsert: (tempId: string) => void;
    clearChanges: () => void;
    hasChanges: () => boolean;
    getPendingChangesCount: () => number;
    isAtLimit: () => boolean;
    isRowDeleted: (rowId: string | number) => boolean;
    isRowInserted: (rowId: string | number) => boolean;
    isCellUpdated: (rowId: string | number, field: string) => boolean;
    getCellUpdate: (rowId: string | number, field: string) => CellUpdate | undefined;
    getAllChanges: () => {
        inserts: Array<{ tempId: string; data: Record<string, any> }>;
        updates: Array<{ rowId: string | number; changes: Map<string, CellUpdate> }>;
        deletes: Array<string | number>;
    };
}

export const useTableEditStore = create<TableEditState>()(
    immer((set, get) => ({
        insertedRows: new Map(),
        updatedCells: new Map(),
        deletedRows: new Set(),

        addInsert: (tempId: string, rowData: Record<string, any>) => {
            const state = get();
            const currentCount = state.insertedRows.size + state.updatedCells.size + state.deletedRows.size;

            if (currentCount >= MAX_PENDING_CHANGES) {
                return false; // Limit reached
            }

            set((state) => {
                state.insertedRows.set(tempId, rowData);
            });
            return true;
        },

        updateCell: (rowId: string | number, field: string, oldValue: any, newValue: any) => {
            // Don't track if value hasn't changed
            if (oldValue === newValue) return true;

            const state = get();

            // Don't track updates for deleted rows
            if (state.deletedRows.has(rowId)) return true;

            // Check if this is an inserted row (has temp ID)
            const rowIdStr = String(rowId);
            if (state.insertedRows.has(rowIdStr)) {
                // For inserted rows, update the data directly (doesn't count toward limit)
                set((state) => {
                    const rowData = state.insertedRows.get(rowIdStr)!;
                    rowData[field] = newValue;
                });
                return true;
            }

            // For existing rows, check if we're at the limit
            const isNewUpdate = !state.updatedCells.has(rowId) || !state.updatedCells.get(rowId)?.has(field);
            if (isNewUpdate) {
                const currentCount = state.insertedRows.size + state.updatedCells.size + state.deletedRows.size;
                if (currentCount >= MAX_PENDING_CHANGES) {
                    return false; // Limit reached
                }
            }

            set((state) => {
                // Get or create the row's update map
                if (!state.updatedCells.has(rowId)) {
                    state.updatedCells.set(rowId, new Map());
                }

                const rowUpdates = state.updatedCells.get(rowId)!;

                // If this field was already updated, keep the original oldValue
                const existingUpdate = rowUpdates.get(field);
                if (existingUpdate) {
                    // If reverting to original value, remove the update
                    if (existingUpdate.oldValue === newValue) {
                        rowUpdates.delete(field);
                        // If no more updates for this row, remove the row entry
                        if (rowUpdates.size === 0) {
                            state.updatedCells.delete(rowId);
                        }
                    } else {
                        // Update the newValue but keep original oldValue
                        existingUpdate.newValue = newValue;
                    }
                } else {
                    // New update for this field
                    rowUpdates.set(field, { field, oldValue, newValue });
                }
            });
            return true;
        },

        markForDelete: (rowId: string | number) => {
            const state = get();

            // If already marked for deletion, return true
            if (state.deletedRows.has(rowId)) return true;

            const currentCount = state.insertedRows.size + state.updatedCells.size + state.deletedRows.size;
            if (currentCount >= MAX_PENDING_CHANGES) {
                return false; // Limit reached
            }

            set((state) => {
                state.deletedRows.add(rowId);
                // Remove any pending updates for this row
                state.updatedCells.delete(rowId);
            });
            return true;
        },

        unmarkForDelete: (rowId: string | number) => {
            set((state) => {
                state.deletedRows.delete(rowId);
            });
        },

        removeInsert: (tempId: string) => {
            set((state) => {
                state.insertedRows.delete(tempId);
            });
        },

        clearChanges: () => {
            set((state) => {
                state.insertedRows.clear();
                state.updatedCells.clear();
                state.deletedRows.clear();
            });
        },

        hasChanges: () => {
            const state = get();
            return (
                state.insertedRows.size > 0 ||
                state.updatedCells.size > 0 ||
                state.deletedRows.size > 0
            );
        },

        getPendingChangesCount: () => {
            const state = get();
            return (
                state.insertedRows.size +
                state.updatedCells.size +
                state.deletedRows.size
            );
        },

        isAtLimit: () => {
            return get().getPendingChangesCount() >= MAX_PENDING_CHANGES;
        },

        isRowDeleted: (rowId: string | number) => {
            return get().deletedRows.has(rowId);
        },

        isRowInserted: (rowId: string | number) => {
            return get().insertedRows.has(String(rowId));
        },

        isCellUpdated: (rowId: string | number, field: string) => {
            const rowUpdates = get().updatedCells.get(rowId);
            return rowUpdates ? rowUpdates.has(field) : false;
        },

        getCellUpdate: (rowId: string | number, field: string) => {
            const rowUpdates = get().updatedCells.get(rowId);
            return rowUpdates?.get(field);
        },

        getAllChanges: () => {
            const state = get();
            return {
                inserts: Array.from(state.insertedRows.entries()).map(([tempId, data]) => ({
                    tempId,
                    data
                })),
                updates: Array.from(state.updatedCells.entries()).map(([rowId, changes]) => ({
                    rowId,
                    changes
                })),
                deletes: Array.from(state.deletedRows)
            };
        }
    }))
);
