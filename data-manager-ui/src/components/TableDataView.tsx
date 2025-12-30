import React, { useEffect, useMemo, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllEnterpriseModule } from 'ag-grid-enterprise';
import {
    ColDef,
    GridApi,
    IServerSideDatasource,
    IServerSideGetRowsParams,
    CellClassParams,
    RowClassParams
} from 'ag-grid-community';
import {
    Container,
    Typography,
    Box,
    CircularProgress,
    Alert,
    Paper,
    Stack,
    Button,
    Chip,
    Snackbar,
    LinearProgress,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Tooltip,
    Badge,
    IconButton
} from '@mui/material';
import {
    ArrowBack as ArrowBackIcon,
    TableChart as TableChartIcon,
    Refresh as RefreshIcon,
    Add as AddIcon,
    Delete as DeleteIcon,
    Save as SaveIcon
} from '@mui/icons-material';
import { queryDuckDB } from '../services/duckdb.service';
import { useTableData } from '../hooks/useTableData';
import { useTableMutations } from '../hooks/useTableMutations';
import { useTableEditStore } from '../stores/tableEditStore';
import './LandingPage.css';
import './TableEditStyles.css';

// Register all AG Grid Enterprise modules
ModuleRegistry.registerModules([AllEnterpriseModule]);

// Stable cell style functions (defined outside component to prevent recreation)
const ID_CELL_STYLE = { color: '#A0A0A0', fontFamily: 'Roboto Mono' };
const DEFAULT_CELL_STYLE = { color: '#000000' };

export const TableDataView: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [gridApi, setGridApi] = React.useState<GridApi | null>(null);
    // Store gridApi in ref for cleanup access
    const gridApiRef = useRef<GridApi | null>(null);

    // Use custom hooks for data management
    const {
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
    } = useTableData(id);

    const {
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
        savingChanges,
        onCellValueChanged,
        handleBulkDelete,
        handleSaveNewRow,
        batchSaveChanges
    } = useTableMutations();

    // Get Zustand store for styling
    const {
        isRowDeleted,
        isRowInserted,
        isCellUpdated,
        hasChanges,
        getPendingChangesCount,
        clearChanges
    } = useTableEditStore();

    // Clear pending changes when switching to a different table
    useEffect(() => {
        return () => {
            // Cleanup when component unmounts or table changes
            clearChanges();
        };
    }, [id, clearChanges]);

    // Cleanup AG Grid instance on unmount or table change
    useEffect(() => {
        return () => {
            // Destroy grid API to prevent memory leaks
            if (gridApiRef.current) {
                try {
                    gridApiRef.current.destroy();
                    gridApiRef.current = null;
                } catch (error) {
                    console.warn('Error destroying AG Grid instance:', error);
                }
            }
        };
    }, [id]); // Cleanup when id changes or component unmounts

    // Additional cleanup effect that runs on unmount regardless of id change
    // This ensures cleanup happens even if component unmounts quickly
    useEffect(() => {
        return () => {
            // Clear Zustand store state on component unmount
            clearChanges();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []); // Empty deps array means this only runs on mount/unmount

    // Wrapper functions to pass required parameters to hook functions
    const handleCellValueChanged = useCallback((params: any) => {
        if (!id || !tableInfo) return;
        onCellValueChanged(params, Number(id), tableInfo.physicalName, setError);
    }, [id, tableInfo, onCellValueChanged, setError]);

    const handleBulkDeleteClick = useCallback(() => {
        if (!id || !tableInfo) return;
        handleBulkDelete(Number(id), tableInfo.physicalName, gridApi);
    }, [id, tableInfo, gridApi, handleBulkDelete]);

    const handleSaveRow = useCallback(() => {
        if (!id || !tableInfo) return;
        handleSaveNewRow(Number(id), tableInfo.physicalName, gridApi, () => {
            // Row count update handled internally by the hook
        });
    }, [id, tableInfo, gridApi, handleSaveNewRow]);

    const handleBatchSaveClick = useCallback(() => {
        setSaveDialogOpen(true);
    }, [setSaveDialogOpen]);

    const handleConfirmSave = useCallback(async () => {
        if (!id || !tableInfo) return;
        await batchSaveChanges(Number(id), tableInfo.physicalName, gridApi);
        setSaveDialogOpen(false);
    }, [id, tableInfo, gridApi, batchSaveChanges, setSaveDialogOpen]);

    const handleRefresh = useCallback(() => {
        loadDataIntoDuckDB(true);
    }, [loadDataIntoDuckDB]);

    // Use ref to store current row count to avoid datasource recreation
    const rowCountRef = useRef(rowCount);
    useEffect(() => {
        rowCountRef.current = rowCount;
    }, [rowCount]);

    // Create Server-Side Datasource for AG-Grid (stable - only recreates when tableInfo changes)
    const createServerSideDatasource = useCallback((): IServerSideDatasource => {
        return {
            getRows: async (params: IServerSideGetRowsParams) => {
                if (!tableInfo) {
                    params.fail();
                    return;
                }

                try {
                    const { startRow, endRow, sortModel, filterModel } = params.request;

                    // Get pending inserts and updates from store
                    const { inserts, updates } = useTableEditStore.getState().getAllChanges();
                    const pendingRows = inserts.map(({ tempId, data }) => ({
                        id: tempId,
                        ...data
                    }));

                    // Build SQL query
                    let sql = `SELECT * FROM "${tableInfo.physicalName}"`;

                    // Add WHERE clause for filters
                    if (filterModel && Object.keys(filterModel).length > 0) {
                        const whereClauses: string[] = [];
                        for (const [field, filter] of Object.entries(filterModel)) {
                            if (filter.filterType === 'text' && filter.filter) {
                                whereClauses.push(`"${field}" ILIKE '%${filter.filter}%'`);
                            } else if (filter.filterType === 'number' && filter.filter != null) {
                                const op = filter.type === 'greaterThan' ? '>' :
                                    filter.type === 'lessThan' ? '<' :
                                        filter.type === 'equals' ? '=' : '=';
                                whereClauses.push(`"${field}" ${op} ${filter.filter}`);
                            }
                        }
                        if (whereClauses.length > 0) {
                            sql += ' WHERE ' + whereClauses.join(' AND ');
                        }
                    }

                    // Add ORDER BY clause
                    if (sortModel && sortModel.length > 0) {
                        const orderClauses = sortModel.map(s => `"${s.colId}" ${s.sort.toUpperCase()}`);
                        sql += ' ORDER BY ' + orderClauses.join(', ');
                    }

                    // Adjust pagination to account for pending rows
                    const numPendingRows = pendingRows.length;
                    const adjustedStartRow = Math.max(0, (startRow ?? 0) - numPendingRows);
                    const adjustedEndRow = Math.max(0, (endRow ?? 100) - numPendingRows);

                    const limit = adjustedEndRow - adjustedStartRow;
                    const offset = adjustedStartRow;
                    sql += ` LIMIT ${limit} OFFSET ${offset}`;

                    const rows = await queryDuckDB(sql);

                    // Apply pending updates to rows
                    const rowsWithUpdates = rows.map(row => {
                        // Find updates for this row
                        const rowUpdate = updates.find(u => u.rowId === row.id);
                        if (!rowUpdate) return row;

                        // Apply all pending cell updates for this row
                        const updatedRow = { ...row };
                        rowUpdate.changes.forEach((cellUpdate: any, field: string) => {
                            updatedRow[field] = cellUpdate.newValue;
                        });
                        return updatedRow;
                    });

                    // Get total row count directly from DuckDB to ensure we see newly inserted rows
                    // counting is fast in DuckDB
                    let countSql = `SELECT COUNT(*) as count FROM "${tableInfo.physicalName}"`;

                    if (filterModel && Object.keys(filterModel).length > 0) {
                        const whereClauses: string[] = [];
                        for (const [field, filter] of Object.entries(filterModel)) {
                            if (filter.filterType === 'text' && filter.filter) {
                                whereClauses.push(`"${field}" ILIKE '%${filter.filter}%'`);
                            } else if (filter.filterType === 'number' && filter.filter != null) {
                                const op = filter.type === 'greaterThan' ? '>' :
                                    filter.type === 'lessThan' ? '<' :
                                        filter.type === 'equals' ? '=' : '=';
                                whereClauses.push(`"${field}" ${op} ${filter.filter}`);
                            }
                        }
                        if (whereClauses.length > 0) {
                            countSql += ' WHERE ' + whereClauses.join(' AND ');
                        }
                    }

                    const countResult = await queryDuckDB(countSql);
                    let lastRow = Number(countResult[0].count) + numPendingRows;

                    // Combine pending rows with database rows (with updates applied)
                    // Pending rows go first, then database rows
                    const combinedRows = [...pendingRows, ...rowsWithUpdates];

                    // Slice to match the requested range
                    const requestedRows = combinedRows.slice(
                        startRow ?? 0,
                        endRow ?? 100
                    );

                    params.success({
                        rowData: requestedRows,
                        rowCount: lastRow
                    });

                } catch (error) {
                    console.error('❌ SSRM getRows error:', error);
                    params.fail();
                }
            }
        };
    }, [tableInfo]);

    const colDefs = useMemo<ColDef[]>(() => {
        if (columns.length === 0) return [];

        const defs: ColDef[] = [
            {
                field: 'id',
                headerName: 'ID',
                width: 100,
                cellStyle: ID_CELL_STYLE,
                pinned: 'left',
                filter: 'agNumberColumnFilter',
                sortable: true,
                editable: false,
                checkboxSelection: true,
                headerCheckboxSelection: true,
                cellClassRules: {
                    'cell-deleted': (params: CellClassParams) => isRowDeleted(params.data?.id),
                    'cell-inserted': (params: CellClassParams) => isRowInserted(params.data?.id)
                }
            }
        ];

        // Add user-defined columns (exclude system columns from editing)
        const systemColumns = ['add_usr', 'add_ts', 'upd_usr', 'upd_ts'];
        columns
            .filter(col => !systemColumns.includes(col.physicalName.toLowerCase()))
            .forEach(col => {
                defs.push({
                    field: col.physicalName,
                    headerName: col.label.toUpperCase(),
                    minWidth: 150,
                    maxWidth: 400,
                    flex: 1,
                    wrapHeaderText: true,
                    autoHeaderHeight: true,
                    filter: 'agTextColumnFilter',
                    sortable: true,
                    editable: true,
                    cellStyle: DEFAULT_CELL_STYLE,
                    cellClassRules: {
                        'cell-deleted': (params: CellClassParams) => isRowDeleted(params.data?.id),
                        'cell-inserted': (params: CellClassParams) => isRowInserted(params.data?.id),
                        'cell-updated': (params: CellClassParams) =>
                            !isRowDeleted(params.data?.id) &&
                            !isRowInserted(params.data?.id) &&
                            isCellUpdated(params.data?.id, col.physicalName)
                    }
                });
            });

        // Add audit columns as read-only at the end
        const auditColumnStyle = { backgroundColor: '#F5F5F5', color: '#666', fontFamily: 'Roboto Mono', fontSize: '0.75rem' };

        defs.push({
            field: 'add_usr',
            headerName: 'CREATED BY',
            width: 120,
            editable: false,
            sortable: true,
            filter: 'agTextColumnFilter',
            cellStyle: auditColumnStyle
        });

        defs.push({
            field: 'add_ts',
            headerName: 'CREATED AT',
            width: 180,
            editable: false,
            sortable: true,
            filter: 'agDateColumnFilter',
            cellStyle: auditColumnStyle,
            valueFormatter: (params) => {
                if (!params.value) return '';
                return new Date(params.value).toLocaleString();
            }
        });

        defs.push({
            field: 'upd_usr',
            headerName: 'UPDATED BY',
            width: 120,
            editable: false,
            sortable: true,
            filter: 'agTextColumnFilter',
            cellStyle: auditColumnStyle
        });

        defs.push({
            field: 'upd_ts',
            headerName: 'UPDATED AT',
            width: 180,
            editable: false,
            sortable: true,
            filter: 'agDateColumnFilter',
            cellStyle: auditColumnStyle,
            valueFormatter: (params) => {
                if (!params.value) return '';
                return new Date(params.value).toLocaleString();
            }
        });

        return defs;
    }, [columns, isRowDeleted, isRowInserted, isCellUpdated]);

    const onSelectionChanged = useCallback(() => {
        if (gridApi) {
            const selectedRows = gridApi.getSelectedRows();
            setSelectedCount(selectedRows.length);
        }
    }, [gridApi]);

    return (
        <Box sx={{ minHeight: '100vh', background: '#FFFFFF', pt: 1.5 }}>
            <Container maxWidth="xl">
                {/* Header */}
                <Box sx={{
                    borderBottom: '1px solid #E0E0E0',
                    mb: 1.5,
                    pb: 1,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center'
                }}>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <Button
                            startIcon={<ArrowBackIcon />}
                            onClick={() => navigate('/')}
                            sx={{ color: 'text.secondary', minWidth: 'auto', fontWeight: 600 }}
                        >
                            BACK
                        </Button>
                        <Box sx={{ width: '1px', height: '20px', bgcolor: '#E0E0E0' }} />
                        <TableChartIcon sx={{ color: 'primary.main', fontSize: 20 }} />
                        <Tooltip title={tableInfo ? `${tableInfo.label} (${tableInfo.physicalName})` : ''}>
                            <Typography
                                variant="h6"
                                sx={{
                                    color: 'text.primary',
                                    letterSpacing: '0.1em',
                                    fontSize: '0.875rem',
                                    maxWidth: '400px',
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis'
                                }}
                            >
                                {tableInfo?.label || 'LOADING...'} <span style={{ color: '#666', fontSize: '0.75em', marginLeft: '6px' }}>({tableInfo?.physicalName})</span>
                            </Typography>
                        </Tooltip>
                        {dataLoaded && (
                            <Chip
                                label={`${rowCount.toLocaleString()} ROWS`}
                                color="default"
                                size="small"
                                variant="outlined"
                                sx={{ borderRadius: 0 }}
                            />
                        )}
                    </Stack>
                    <Stack direction="row" spacing={1}>
                        <Tooltip title={`Save Changes (${getPendingChangesCount()})`}>
                            <Badge
                                badgeContent={getPendingChangesCount()}
                                color="warning"
                                invisible={!hasChanges()}
                            >
                                <span style={{ display: 'inline-flex' }}> {/* Wrapper for disabled state tooltip */}
                                    <IconButton
                                        color="success"
                                        onClick={handleBatchSaveClick}
                                        disabled={!dataLoaded || !hasChanges() || savingChanges}
                                        size="small"
                                        sx={{
                                            borderRadius: 1,
                                            border: '1px solid',
                                            borderColor: hasChanges() && !savingChanges ? 'success.light' : 'transparent'
                                        }}
                                    >
                                        {savingChanges ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
                                    </IconButton>
                                </span>
                            </Badge>
                        </Tooltip>

                        {selectedCount > 0 && (
                            <Tooltip title={`Delete Selected (${selectedCount})`}>
                                <IconButton
                                    color="error"
                                    onClick={handleBulkDeleteClick}
                                    disabled={!dataLoaded}
                                    size="small"
                                    sx={{
                                        borderRadius: 1,
                                        border: '1px solid',
                                        borderColor: 'error.light'
                                    }}
                                >
                                    <DeleteIcon />
                                </IconButton>
                            </Tooltip>
                        )}

                        <Tooltip title="Add Row">
                            <IconButton
                                color="primary"
                                onClick={handleSaveRow}
                                disabled={!dataLoaded}
                                size="small"
                                sx={{ borderRadius: 1 }}
                            >
                                <AddIcon />
                            </IconButton>
                        </Tooltip>

                        <Tooltip title="Refresh Data">
                            <IconButton
                                onClick={handleRefresh}
                                disabled={!dataLoaded}
                                size="small"
                                sx={{ borderRadius: 1 }}
                            >
                                <RefreshIcon />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                </Box>

                {error && (
                    <Alert severity="error" variant="outlined" sx={{ mb: 1.5, borderRadius: 0 }}>
                        {error}
                    </Alert>
                )}

                {loadingData && loadProgress && (
                    <Box sx={{ mb: 2 }}>
                        <Alert severity="info" variant="outlined" icon={false} sx={{ borderRadius: 0, py: 1 }}>
                            <Stack spacing={1}>
                                <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between">
                                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                        {loadProgress}
                                    </Typography>
                                    {loadProgressPercent > 0 && loadProgressPercent < 100 && (
                                        <Typography variant="body2" sx={{ fontWeight: 600, color: 'primary.main' }}>
                                            {loadProgressPercent}%
                                        </Typography>
                                    )}
                                </Stack>
                                <LinearProgress
                                    variant={loadProgressPercent > 0 ? "determinate" : "indeterminate"}
                                    value={loadProgressPercent}
                                    sx={{
                                        height: 6,
                                        borderRadius: 0,
                                        backgroundColor: 'rgba(0, 102, 204, 0.1)',
                                        '& .MuiLinearProgress-bar': {
                                            backgroundColor: 'primary.main'
                                        }
                                    }}
                                />
                            </Stack>
                        </Alert>
                    </Box>
                )}

                {/* Grid */}
                <Paper
                    className="grid-wrapper ag-theme-quartz-dark"
                    elevation={0}
                    sx={{
                        height: 'calc(100vh - 140px)',
                        borderRadius: 0,
                        border: '1px solid #E0E0E0',
                        position: 'relative'
                    }}
                >
                    {loading ? (
                        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                            <CircularProgress sx={{ color: 'primary.main' }} />
                        </Box>
                    ) : dataLoaded ? (
                        <>
                            <AgGridReact
                                columnDefs={colDefs}
                                defaultColDef={{
                                    wrapHeaderText: true,
                                    autoHeaderHeight: true
                                }}
                                rowModelType={'serverSide'}
                                serverSideDatasource={createServerSideDatasource()}
                                pagination={true}
                                paginationPageSize={100}
                                cacheBlockSize={10000}
                                maxBlocksInCache={5}
                                rowHeight={28}
                                headerHeight={60}
                                onGridReady={(params) => {
                                    setGridApi(params.api);
                                    gridApiRef.current = params.api;
                                    params.api.autoSizeAllColumns(false);
                                }}
                                suppressColumnVirtualisation={false}
                                animateRows={false}
                                loading={false}
                                onCellValueChanged={handleCellValueChanged}
                                rowSelection="multiple"
                                onSelectionChanged={onSelectionChanged}
                                singleClickEdit={true}
                                getRowClass={(params: RowClassParams) => {
                                    if (isRowDeleted(params.data?.id)) return 'row-deleted';
                                    if (isRowInserted(params.data?.id)) return 'row-inserted';
                                    return '';
                                }}
                            />
                        </>
                    ) : null}
                </Paper>
            </Container>

            {/* Save Confirmation Dialog */}
            <Dialog open={saveDialogOpen} onClose={() => setSaveDialogOpen(false)} maxWidth="xs" fullWidth>
                <DialogTitle>Save All Changes?</DialogTitle>
                <DialogContent>
                    <Typography>
                        You have {getPendingChangesCount()} pending change(s).
                        Do you want to save all changes to the database?
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                        This action cannot be undone.
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setSaveDialogOpen(false)} disabled={savingChanges}>
                        Cancel
                    </Button>
                    <Button
                        onClick={handleConfirmSave}
                        color="primary"
                        variant="contained"
                        disabled={savingChanges}
                        startIcon={savingChanges ? <CircularProgress size={20} color="inherit" /> : null}
                    >
                        {savingChanges ? 'Saving...' : 'Save Changes'}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Limit Warning Dialog */}
            <Dialog open={limitWarningDialogOpen} onClose={() => setLimitWarningDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Change Limit Reached</DialogTitle>
                <DialogContent>
                    <Typography>
                        You have reached the maximum limit of 50 pending changes.
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                        Please save your current changes before making additional edits.
                    </Typography>
                    <Typography variant="body2" sx={{ mt: 2, fontWeight: 'bold' }}>
                        Current pending changes: {getPendingChangesCount()}
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setLimitWarningDialogOpen(false)}>
                        OK
                    </Button>
                    <Button
                        onClick={() => {
                            setLimitWarningDialogOpen(false);
                            setSaveDialogOpen(true);
                        }}
                        color="primary"
                        variant="contained"
                    >
                        Save Changes Now
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Row Dialog */}
            <Dialog open={addDialogOpen} onClose={() => setAddDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Add New Row</DialogTitle>
                <DialogContent>
                    <Box sx={{ pt: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
                        {columns.map(col => (
                            <TextField
                                key={col.id}
                                label={col.label}
                                fullWidth
                                variant="outlined"
                                size="small"
                                value={newRowData[col.physicalName] || ''}
                                onChange={(e) => setNewRowData({ ...newRowData, [col.physicalName]: e.target.value })}
                            />
                        ))}
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddDialogOpen(false)} disabled={savingRow}>Cancel</Button>
                    <Button
                        onClick={handleSaveRow}
                        variant="contained"
                        disabled={savingRow}
                        startIcon={savingRow ? <CircularProgress size={20} /> : <SaveIcon />}
                    >
                        Save
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                open={snackbar.open}
                autoHideDuration={6000}
                onClose={() => setSnackbar({ ...snackbar, open: false })}
            >
                <Alert onClose={() => setSnackbar({ ...snackbar, open: false })} severity={snackbar.severity} sx={{ width: '100%' }}>
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </Box>
    );
};
