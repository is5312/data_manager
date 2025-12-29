import React, { useEffect, useMemo, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllEnterpriseModule } from 'ag-grid-enterprise';
import {
    ColDef,
    GridApi,
    IServerSideDatasource,
    IServerSideGetRowsParams
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
    Tooltip
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
import './LandingPage.css';

// Register all AG Grid Enterprise modules
ModuleRegistry.registerModules([AllEnterpriseModule]);

// Stable cell style functions (defined outside component to prevent recreation)
const ID_CELL_STYLE = { color: '#A0A0A0', fontFamily: 'Roboto Mono' };
const DEFAULT_CELL_STYLE = { color: '#000000' };

export const TableDataView: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [gridApi, setGridApi] = React.useState<GridApi | null>(null);

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
    } = useTableMutations();

    // Wrapper functions to pass required parameters to hook functions
    const handleCellValueChanged = useCallback((params: any) => {
        if (!id || !tableInfo) return;
        onCellValueChanged(params, Number(id), tableInfo.physicalName, setError);
    }, [id, tableInfo, onCellValueChanged, setError]);

    const handleBulkDeleteClick = useCallback(() => {
        handleBulkDelete(gridApi);
    }, [gridApi, handleBulkDelete]);

    const handleConfirmDelete = useCallback(() => {
        if (!id || !tableInfo) return;
        confirmDelete(Number(id), tableInfo.physicalName, gridApi, () => {
            // Row count update handled internally by the hook
        });
    }, [id, tableInfo, gridApi, confirmDelete]);

    const handleSaveRow = useCallback(() => {
        if (!id || !tableInfo) return;
        handleSaveNewRow(Number(id), tableInfo.physicalName, gridApi, () => {
            // Row count update handled internally by the hook
        });
    }, [id, tableInfo, gridApi, handleSaveNewRow, newRowData]);

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

                    // Add LIMIT and OFFSET for pagination
                    const limit = (endRow ?? 100) - (startRow ?? 0);
                    const offset = startRow ?? 0;
                    sql += ` LIMIT ${limit} OFFSET ${offset}`;

                    const rows = await queryDuckDB(sql);

                    // Get total row count (use ref to get latest value without recreating datasource)
                    let lastRow = rowCountRef.current;
                    if (filterModel && Object.keys(filterModel).length > 0) {
                        // Filtered - need to count with same WHERE clause
                        let countSql = `SELECT COUNT(*) as count FROM "${tableInfo.physicalName}"`;
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

                        const countResult = await queryDuckDB(countSql);
                        lastRow = Number(countResult[0].count);
                    }

                    params.success({
                        rowData: rows,
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
                headerCheckboxSelection: true
            }
        ];

        // Add columns based on table schema
        columns.forEach(col => {
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
                cellStyle: DEFAULT_CELL_STYLE
            });
        });

        return defs;
    }, [columns]);

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
                        {dataLoaded && (
                            <Chip
                                label="🦆 DUCKDB"
                                color="primary"
                                size="small"
                                variant="outlined"
                                sx={{ borderRadius: 0, fontWeight: 600 }}
                            />
                        )}
                    </Stack>
                    <Stack direction="row" spacing={1}>
                        <Button
                            variant="contained"
                            color="error"
                            startIcon={<DeleteIcon />}
                            onClick={handleBulkDeleteClick}
                            size="small"
                            disabled={!dataLoaded}
                            style={{ display: selectedCount > 0 ? 'inline-flex' : 'none' }}
                            sx={{ borderRadius: 0 }}
                        >
                            DELETE ({selectedCount})
                        </Button>
                        <Button
                            variant="contained"
                            color="primary"
                            startIcon={<AddIcon />}
                            onClick={() => setAddDialogOpen(true)}
                            size="small"
                            disabled={!dataLoaded}
                            sx={{ borderRadius: 0 }}
                        >
                            ADD ROW
                        </Button>
                        <Button
                            variant="outlined"
                            onClick={handleRefresh}
                            size="small"
                            disabled={!dataLoaded}
                            startIcon={<RefreshIcon />}
                            sx={{ minWidth: 'auto', px: 1.5 }}
                        >
                            REFRESH
                        </Button>
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
                                    params.api.autoSizeAllColumns(false);
                                }}
                                suppressColumnVirtualisation={false}
                                animateRows={false}
                                loading={loadingData}
                                onCellValueChanged={handleCellValueChanged}
                                rowSelection="multiple"
                                onSelectionChanged={onSelectionChanged}
                            />
                        </>
                    ) : null}
                </Paper>
            </Container>

            {/* Delete Confirmation Dialog */}
            <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} maxWidth="xs" fullWidth>
                <DialogTitle>Delete Rows?</DialogTitle>
                <DialogContent>
                    <Typography>
                        Are you sure you want to delete {selectedCount} selected row(s)?
                        This action cannot be undone.
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDeleteDialogOpen(false)} disabled={deletingRows}>
                        Cancel
                    </Button>
                    <Button
                        onClick={handleConfirmDelete}
                        color="error"
                        variant="contained"
                        disabled={deletingRows}
                        startIcon={deletingRows ? <CircularProgress size={20} color="inherit" /> : <DeleteIcon />}
                    >
                        {deletingRows ? 'Deleting...' : 'Delete'}
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
