# Frontend Architecture

## Overview

The `data-manager-ui` is a microfrontend application built with React, designed to provide a rich, spreadsheet-like interface for managing database schemas and data. It interacts with the backend for persistence and uses DuckDB WASM for client-side data buffering and querying.

## Core Concepts

### 1. State Management (Zustand + Immer)
We use **Zustand** combined with **Immer** for managing the application state, particularly for table editing. This allows for:
- **Immutable Updates**: Immer simplifies working with nested objects and Maps/Sets.
- **Pending Changes**: Edits are tracked in memory before being saved to the backend.
  - `insertedRows`: Map of temporary IDs to new row data.
  - `updatedCells`: Nested Map tracking cell changes (Row ID -> Field -> {old, new}).
  - `deletedRows`: Set of IDs marked for deletion.
- **Optimistic UI**: The grid reflects pending changes immediately (e.g., deleted rows shown with strikethrough).

### 2. Data Grid (AG Grid + DuckDB)
The data grid is the core component (`TableDataView.tsx`), integrated with:
- **Server-Side Row Model**: Use `rowModelType='serverSide'` to handle large datasets.
- **DuckDB WASM**: Acts as a client-side cache and query engine. Data fetched from the backend is loaded into DuckDB, allowing for fast sorting, filtering, and querying within the browser.
- **Hybrid Datasource**: The custom datasource (`createServerSideDatasource`) combines:
  1. **Pending Inserts**: New rows from the Zustand store.
  2. **Database Rows**: Rows fetched from DuckDB (originally from backend).
  3. **Pending Updates**: Overlays local cell edits onto the fetched rows.

### 3. Change Tracking & Limits
To prevent memory issues and ensure data integrity:
- **Change Limit**: A hard limit of **50 pending changes** is enforced.
- **Visual Feedback**:
  - **Inserts**: Highlighted in green.
  - **Updates**: Highlighted in yellow.
  - **Deletes**: Red text with strikethrough.
- **Batch Saving**: All changes are committed in a single transaction (or batched requests) when the user clicks "Save".

## Directory Structure

```
data-manager-ui/
├── src/
│   ├── components/
│   │   ├── TableDataView.tsx    # Main grid component
│   │   └── ...
│   ├── hooks/
│   │   ├── useTableData.ts      # Data fetching & DuckDB loading
│   │   └── useTableMutations.ts # Edit handlers & batch save logic
│   ├── stores/
│   │   └── tableEditStore.ts    # Zustand store for pending changes
│   ├── services/
│   │   ├── api.ts               # Backend REST client
│   │   └── duckdb.ts            # DuckDB singleton & queries
│   └── utils/
│       └── ...
```

## Key Workflows

### Loading Data
1. `useTableData` fetches table metadata and physical name.
2. Fetches all rows from backend (`/api/schema/tables/{id}/data`).
3. Inserts data into a local DuckDB table.
4. Grid requests rows via `serverSideDatasource`.
5. Datasource merges Store changes + DuckDB data → Grid.
6. **Smart Count**: Datasource queries `SELECT COUNT(*)` to ensure accurate pagination.

### Editing Data
1. **User Action**: Single-click cell edit, Add Row, or Delete Row.
2. **Store Update**: Action is dispatched to `tableEditStore`.
3. **Limit Check**: Store checks if `MAX_PENDING_CHANGES (50)` is reached.
   - If yes: Returns false, UI shows warning dialog.
   - If no: Updates state, returns true.
4. **UI Update**: 
   - Grid refreshes specific cells/rows.
   - `getRowClass` applies styling based on store state.
   - "Save Changes" button updates badge count.

### Saving Data
1. User clicks "Save Changes".
2. **Confirmation**: Dialog shows count of inserts/updates/deletes.
3. **Execution** (`useTableMutations`):
   - Backend: Calls optimized `insertRow`/`updateRow` endpoints.
   - Local Sync: Updates DuckDB with returned row data (ID, audit columns).
   - Clear Store: Removes pending changes.
4. **Refresh**: `gridApi.refreshServerSide({ purge: true })` triggers fast reload from local DuckDB.

## Optimizations
- **Live Row Counts**: Grid datasource always queries `COUNT(*)` from DuckDB to handle insertions correctly.
- **Local Refresh**: Saving updates DuckDB locally and purges grid cache, avoiding full backend re-fetch.
- **Audit Columns**: Backend calculates and returns `add_ts`, `upd_ts` etc., which are synced to DuckDB immediately.

## Design System
- **Theme**: Custom Dark Mode with specific semantic colors.
- **Styling**: Vanilla CSS (`TableEditStyles.css`) + MUI System.
- **Glassmorphism**: Used for panels and dialogs.

## Best Practices
- **Always use the Store**: Never mutate grid data directly; go through `tableEditStore`.
- **Sanitize IDs**: Use proper ID types (number for DB, string for temp).
- **Clean Up**: `useEffect` in `TableDataView` clears the store when switching tables.
