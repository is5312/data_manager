import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllEnterpriseModule } from 'ag-grid-enterprise';
import {
    ColDef,
    ICellRendererParams
} from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import {
    Container,
    Typography,
    Box,
    CircularProgress,
    Alert,
    Paper,
    Stack,
    Button,
    IconButton,
    Tooltip,
    Snackbar,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Chip
} from '@mui/material';
import {
    Add as AddIcon,
    Visibility as VisibilityIcon,
    Refresh as RefreshIcon,
    Edit as EditIcon,
    CloudUpload as UploadIcon,
    Delete as DeleteIcon,
    Info as InfoIcon,
    SwapHoriz as MigrateIcon,
    QueryBuilder as QueryBuilderIcon
} from '@mui/icons-material';
import { fetchTables, createTable, addColumn, deleteTable, TableMetadata, startBatchUpload, fetchAvailableSchemas } from '../services/api';
import { initDuckDB } from '../utils/duckdb';
import { CreateTableDialog } from './CreateTableDialog';
import { CsvUploadDialog } from './CsvUploadDialog';
import { BatchUploadProgressDialog } from './BatchUploadProgressDialog';
import { SchemaSelector } from './SchemaSelector';
import { TableMigrationDialog } from './TableMigrationDialog';
import { MigrationStatusDialog } from './MigrationStatusDialog';
import { NavigationBar } from './NavigationBar';
import './LandingPage.css';

// Register all AG Grid Enterprise modules
ModuleRegistry.registerModules([AllEnterpriseModule]);

export const LandingPage: React.FC = () => {
    const navigate = useNavigate();
    const [rowData, setRowData] = useState<TableMetadata[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [duckDBReady, setDuckDBReady] = useState(false);
    const [createDialogOpen, setCreateDialogOpen] = useState(false);
    const [csvUploadDialogOpen, setCsvUploadDialogOpen] = useState(false);
    // Map of tableId -> batchId to track which tables have active batch jobs
    const [tableBatchMap, setTableBatchMap] = useState<Map<number, number>>(new Map());
    const [activeBatchId, setActiveBatchId] = useState<number | null>(null);
    const [progressOpen, setProgressOpen] = useState(false);
    const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
    const [tableToDelete, setTableToDelete] = useState<TableMetadata | null>(null);
    const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' | 'info' }>({
        open: false,
        message: '',
        severity: 'success'
    });
    const [currentSchema, setCurrentSchema] = useState<string>('public');
    const [availableSchemas, setAvailableSchemas] = useState<string[]>(['public']);
    const [migrationDialogOpen, setMigrationDialogOpen] = useState(false);
    const [tableToMigrate, setTableToMigrate] = useState<TableMetadata | null>(null);
    // Map of tableId -> jobId to track migration jobs
    const [tableMigrationJobs, setTableMigrationJobs] = useState<Map<number, string>>(new Map());
    const [migrationStatusDialogOpen, setMigrationStatusDialogOpen] = useState(false);
    const [selectedJobId, setSelectedJobId] = useState<string>('');
    const [selectedTableLabel, setSelectedTableLabel] = useState<string>('');

    const loadData = useCallback(() => {
        setLoading(true);
        fetchTables(currentSchema).then(data => {
            // Data is already sorted by the backend (most recently updated/created first)
            setRowData(data);
            setLoading(false);
        }).catch(err => {
            console.error("Error fetching tables", err);
            setError("Failed to fetch tables from the server");
            setLoading(false);
        });
    }, [currentSchema]);

    const loadAvailableSchemas = useCallback(() => {
        fetchAvailableSchemas().then(schemas => {
            setAvailableSchemas(schemas);
            if (!schemas.includes(currentSchema)) {
                setCurrentSchema('public');
            }
        }).catch(err => {
            console.error("Error fetching available schemas", err);
        });
    }, [currentSchema]);

    useEffect(() => {
        initDuckDB().then(() => {
            console.log("DuckDB WASM initialized");
            setDuckDBReady(true);
        }).catch(err => {
            console.error("Failed to initialize DuckDB WASM", err);
            setError("Failed to initialize DuckDB WASM");
        });

        loadAvailableSchemas();
        loadData();
    }, [loadData, loadAvailableSchemas]);


    const handleCreateTable = async (tableName: string, columns: Array<{ id: string; label: string; type: string }>, deploymentType: string) => {
        try {
            // First create the table
            const newTable = await createTable(tableName, deploymentType);

            // Then add each column
            for (const column of columns) {
                await addColumn(newTable.id, column.label, column.type);
            }

            setCreateDialogOpen(false);
            loadData(); // Refresh list
        } catch (err) {
            console.error("Failed to create table", err);
            alert("Failed to create table. Check console.");
        }
    };

    const handleCsvUpload = async (file: File, tableName: string, deploymentType: string, columnTypes?: string[], selectedColumnIndices?: number[], csvOptions?: { delimiter?: string; quoteChar?: string; escapeChar?: string }) => {
        try {
            // Always use batch upload (CSV or GZIP). Backend will stream-read and insert asynchronously.
            const resp = await startBatchUpload(file, tableName, deploymentType, columnTypes, selectedColumnIndices, csvOptions);

            // Store the mapping of tableId -> batchId
            const tableId = resp.table.id;
            setTableBatchMap(prev => new Map(prev).set(tableId, resp.batchId));

            // Don't auto-open progress dialog - user can click the Info icon to check status
            setSnackbar({
                open: true,
                severity: 'info',
                message: `Batch upload started (ID: ${resp.batchId}). Click ⓘ icon to view progress.`
            });
            loadData();
        } catch (e: any) {
            setSnackbar({ open: true, severity: 'error', message: e?.message || 'Upload failed' });
            throw e;
        }
    };

    const handleViewTable = useCallback((table: TableMetadata) => {
        navigate(`/tables/${table.id}?schema=${encodeURIComponent(currentSchema)}`);
    }, [navigate, currentSchema]);

    const handleRequestDelete = (table: TableMetadata) => {
        setTableToDelete(table);
        setDeleteConfirmOpen(true);
    };

    const handleConfirmDelete = async () => {
        if (!tableToDelete) return;
        try {
            await deleteTable(tableToDelete.id);
            setSnackbar({ open: true, severity: 'success', message: `Deleted table "${tableToDelete.label}"` });
            setDeleteConfirmOpen(false);
            setTableToDelete(null);
            loadData();
        } catch (e: any) {
            setSnackbar({ open: true, severity: 'error', message: e?.message || 'Failed to delete table' });
        }
    };

    const ViewActionRenderer = useCallback((params: ICellRendererParams<TableMetadata>) => {
        if (!params.data) return null;

        const tableId = params.data.id;
        const batchIdForTable = tableBatchMap.get(tableId);
        const hasBatch = batchIdForTable !== undefined;
        const migrationJobId = tableMigrationJobs.get(tableId);
        const hasMigrationJob = migrationJobId !== undefined;

        const handleEdit = (e: React.MouseEvent) => {
            e.stopPropagation();
            if (params.data) {
                navigate(`/tables/${params.data.id}/edit?schema=${encodeURIComponent(currentSchema)}`);
            }
        };

        const handleProgress = (e: React.MouseEvent) => {
            e.stopPropagation();
            if (batchIdForTable) {
                setActiveBatchId(batchIdForTable);
                setProgressOpen(true);
            }
        };

        const handleViewMigrationStatus = (e: React.MouseEvent) => {
            e.stopPropagation();
            if (migrationJobId && params.data) {
                setSelectedJobId(migrationJobId);
                setSelectedTableLabel(params.data.label);
                setMigrationStatusDialogOpen(true);
            }
        };

        return (
            <Stack direction="row" spacing={0.5} alignItems="center">
                <Tooltip title="View Data" placement="top">
                    <IconButton
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            if (params.data) navigate(`/tables/${params.data.id}?schema=${encodeURIComponent(currentSchema)}`);
                        }}
                        sx={{
                            color: 'primary.main',
                            '&:hover': { backgroundColor: 'rgba(0, 102, 204, 0.1)' }
                        }}
                    >
                        <VisibilityIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Edit Schema" placement="top">
                    <IconButton
                        size="small"
                        onClick={handleEdit}
                        sx={{
                            color: 'secondary.main',
                            '&:hover': { backgroundColor: 'rgba(0, 102, 204, 0.1)' }
                        }}
                    >
                        <EditIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
                <Tooltip title={hasBatch ? "Batch Progress" : "No active batch"} placement="top">
                    <IconButton
                        size="small"
                        onClick={handleProgress}
                        sx={{
                            color: hasBatch ? 'info.main' : 'action.disabled',
                            '&:hover': hasBatch ? { backgroundColor: 'rgba(33, 150, 243, 0.1)' } : {}
                        }}
                        disabled={!hasBatch}
                    >
                        <InfoIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Migrate Table" placement="top">
                    <IconButton
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            if (params.data) {
                                setTableToMigrate(params.data);
                                setMigrationDialogOpen(true);
                            }
                        }}
                        sx={{
                            color: 'warning.main',
                            '&:hover': { backgroundColor: 'rgba(237, 108, 2, 0.1)' }
                        }}
                    >
                        <MigrateIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
                <Tooltip title={hasMigrationJob ? "View Migration Status" : "No recent migration"} placement="top">
                    <IconButton
                        size="small"
                        onClick={handleViewMigrationStatus}
                        sx={{
                            color: hasMigrationJob ? 'success.main' : 'action.disabled',
                            '&:hover': hasMigrationJob ? { backgroundColor: 'rgba(46, 125, 50, 0.1)' } : {}
                        }}
                        disabled={!hasMigrationJob}
                    >
                        <QueryBuilderIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Delete Table" placement="top">
                    <IconButton
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            if (params.data) handleRequestDelete(params.data);
                        }}
                        sx={{
                            color: 'error.main',
                            '&:hover': { backgroundColor: 'rgba(211, 47, 47, 0.08)' }
                        }}
                    >
                        <DeleteIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
            </Stack>
        );
    }, [handleViewTable, navigate, currentSchema, tableBatchMap, tableMigrationJobs, setProgressOpen, setActiveBatchId]);

    const colDefs = useMemo<ColDef<TableMetadata>[]>(() => [
        {
            headerName: 'ACTION',
            width: 200,
            cellRenderer: ViewActionRenderer,
            cellStyle: { display: 'flex', justifyContent: 'center', alignItems: 'center' },
            sortable: false,
            filter: false,
            pinned: 'left'
        },
        {
            field: 'id',
            headerName: 'ID',
            width: 70,
            sortable: true,
            filter: true,
            cellStyle: () => ({ color: '#A0A0A0', fontFamily: 'Roboto Mono' })
        },
        {
            field: 'label',
            headerName: 'TABLE',
            flex: 1,
            sortable: true,
            filter: true,
            cellStyle: () => ({ fontWeight: 700, color: '#000000' })
        },
        {
            field: 'physicalName',
            headerName: 'REF ID',
            width: 180,
            sortable: true,
            filter: true,
            cellStyle: () => ({ color: '#000000', fontFamily: 'Roboto Mono', fontSize: '0.75rem' })
        },
        {
            field: 'description',
            headerName: 'DESCRIPTION',
            flex: 1,
            sortable: true,
            filter: true,
            cellStyle: () => ({ color: '#666666', fontSize: '0.8rem' })
        },
        {
            field: 'deploymentType',
            headerName: 'DEPLOYMENT TYPE',
            width: 160,
            sortable: true,
            filter: true,
            cellRenderer: (params: ICellRendererParams<TableMetadata>) => {
                if (!params.value) return '';
                const isRunTime = params.value === 'RUN_TIME';
                return (
                    <Chip
                        label={params.value}
                        size="small"
                        sx={{
                            height: 20,
                            fontSize: '0.7rem',
                            fontWeight: 600,
                            backgroundColor: isRunTime ? '#E3F2FD' : '#F3E5F5',
                            color: isRunTime ? '#1976D2' : '#7B1FA2',
                            border: `1px solid ${isRunTime ? '#90CAF9' : '#CE93D8'}`,
                            '& .MuiChip-label': {
                                padding: '0 8px'
                            }
                        }}
                    />
                );
            },
            cellStyle: () => ({ display: 'flex', alignItems: 'center', justifyContent: 'center' })
        },
        {
            field: 'versionNo',
            headerName: 'VER',
            width: 90,
            sortable: true,
            filter: true,
            cellStyle: () => ({ color: '#0066CC', fontWeight: 600, textAlign: 'center' })
        },
        {
            field: 'createdBy',
            headerName: 'CREATED BY',
            width: 120,
            sortable: true,
            filter: true,
            cellStyle: () => ({ color: '#666666', fontSize: '0.75rem' })
        },
        {
            field: 'createdAt',
            headerName: 'CREATED AT',
            width: 160,
            sortable: true,
            filter: true,
            valueFormatter: (params) => {
                if (!params.value) return '';
                return new Date(params.value).toLocaleString();
            },
            cellStyle: () => ({ color: '#666666', fontSize: '0.75rem', fontFamily: 'Roboto Mono' })
        },
        {
            field: 'updatedBy',
            headerName: 'UPDATED BY',
            width: 120,
            sortable: true,
            filter: true,
            cellStyle: () => ({ color: '#666666', fontSize: '0.75rem' })
        },
        {
            field: 'updatedAt',
            headerName: 'UPDATED AT',
            width: 160,
            sortable: true,
            filter: true,
            valueFormatter: (params) => {
                if (!params.value) return '';
                return new Date(params.value).toLocaleString();
            },
            cellStyle: () => ({ color: '#666666', fontSize: '0.75rem', fontFamily: 'Roboto Mono' })
        }
    ], [ViewActionRenderer]);

    return (
        <Box sx={{ minHeight: '100vh', background: '#FFFFFF' }}>
            <NavigationBar isDuckDBReady={duckDBReady} />
            <Container maxWidth="xl" sx={{ pt: 1.5 }}>
                {/* Header */}
                <Box sx={{
                    borderBottom: '1px solid #E0E0E0',
                    mb: 1.5,
                    pb: 1,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center'
                }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                        <Box />
                    </Stack>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <SchemaSelector
                            selectedSchema={currentSchema}
                            availableSchemas={availableSchemas}
                            onSchemaChange={(schema) => {
                                setCurrentSchema(schema);
                            }}
                        />
                        <Tooltip title="Migrate Table">
                            <IconButton
                                onClick={() => {
                                    setTableToMigrate(null);
                                    setMigrationDialogOpen(true);
                                }}
                                size="small"
                                color="warning"
                                sx={{ borderRadius: 1, border: '1px solid', borderColor: 'warning.light' }}
                            >
                                <MigrateIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Create New Table">
                            <IconButton
                                onClick={() => setCreateDialogOpen(true)}
                                size="small"
                                color="primary"
                                sx={{ borderRadius: 1, border: '1px solid', borderColor: 'primary.light' }}
                            >
                                <AddIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Batch Upload (CSV/GZIP)">
                            <IconButton
                                onClick={() => setCsvUploadDialogOpen(true)}
                                size="small"
                                color="secondary"
                                sx={{ borderRadius: 1, border: '1px solid', borderColor: 'secondary.light' }}
                            >
                                <UploadIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Refresh Table List">
                            <IconButton
                                onClick={loadData}
                                size="small"
                                sx={{ borderRadius: 1 }}
                            >
                                <RefreshIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    </Box>
                </Box>

                {error && (
                    <Alert severity="error" variant="outlined" sx={{ mb: 1.5, borderRadius: 0 }}>
                        {error}
                    </Alert>
                )}

                {/* Grid */}
                <Paper
                    className="grid-wrapper ag-theme-quartz-dark"
                    elevation={0}
                    sx={{
                        height: 'calc(100vh - 140px)',
                        borderRadius: 0,
                        border: '1px solid #E0E0E0'
                    }}
                >
                    {loading ? (
                        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                            <CircularProgress sx={{ color: 'primary.main' }} />
                        </Box>
                    ) : (
                        <AgGridReact
                            rowData={rowData}
                            columnDefs={colDefs}
                            pagination={true}
                            paginationPageSize={50}
                            rowHeight={28}
                            headerHeight={60}
                            defaultColDef={{
                                sortable: true,
                                filter: true,
                                resizable: true,
                                wrapHeaderText: true,
                                autoHeaderHeight: true,
                                cellStyle: { display: 'flex', alignItems: 'center' }
                            }}
                            onGridReady={(params) => {
                                // Set initial sort to show most recently updated/created first
                                params.api.applyColumnState({
                                    state: [
                                        { colId: 'updatedAt', sort: 'desc' },
                                        { colId: 'createdAt', sort: 'desc' }
                                    ],
                                    defaultState: { sort: null }
                                });
                            }}
                        />
                    )}
                </Paper>
            </Container>

            <CreateTableDialog
                open={createDialogOpen}
                onClose={() => setCreateDialogOpen(false)}
                onSubmit={handleCreateTable}
            />
            <CsvUploadDialog
                open={csvUploadDialogOpen}
                onClose={() => setCsvUploadDialogOpen(false)}
                onUpload={handleCsvUpload}
            />
            <BatchUploadProgressDialog
                open={progressOpen}
                batchId={activeBatchId}
                onClose={() => setProgressOpen(false)}
            />
            <TableMigrationDialog
                open={migrationDialogOpen}
                onClose={() => {
                    setMigrationDialogOpen(false);
                    setTableToMigrate(null);
                }}
                onSuccess={(jobId: string) => {
                    // Store the job ID for this table
                    if (tableToMigrate) {
                        setTableMigrationJobs(prev => {
                            const newMap = new Map(prev);
                            newMap.set(tableToMigrate.id, jobId);
                            return newMap;
                        });
                    }
                    loadData();
                    setSnackbar({
                        open: true,
                        message: `Migration job queued successfully. Job ID: ${jobId.substring(0, 8)}...`,
                        severity: 'success'
                    });
                }}
                table={tableToMigrate || undefined}
                sourceSchema={currentSchema}
                availableTables={rowData}
            />

            <MigrationStatusDialog
                open={migrationStatusDialogOpen}
                onClose={() => setMigrationStatusDialogOpen(false)}
                jobId={selectedJobId}
                tableLabel={selectedTableLabel}
            />

            <Dialog
                open={deleteConfirmOpen}
                onClose={() => setDeleteConfirmOpen(false)}
                maxWidth="xs"
                fullWidth
            >
                <DialogTitle sx={{
                    borderBottom: '1px solid #E0E0E0',
                    backgroundColor: 'background.paper',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                    color: 'error.main',
                    fontWeight: 700
                }}>
                    Delete Table
                </DialogTitle>
                <DialogContent sx={{ mt: 2 }}>
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                        This will delete the table and its data.
                    </Typography>
                    <Typography variant="body2" sx={{ mt: 1, fontWeight: 700 }}>
                        {tableToDelete?.label}
                    </Typography>
                    {tableToDelete?.physicalName && (
                        <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'Roboto Mono' }}>
                            {tableToDelete.physicalName}
                        </Typography>
                    )}
                </DialogContent>
                <DialogActions sx={{ px: 3, pb: 2 }}>
                    <Button onClick={() => setDeleteConfirmOpen(false)} size="small">
                        Cancel
                    </Button>
                    <Button onClick={handleConfirmDelete} variant="contained" color="error" size="small">
                        Delete
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                open={snackbar.open}
                autoHideDuration={5000}
                onClose={() => setSnackbar({ ...snackbar, open: false })}
            >
                <Alert
                    onClose={() => setSnackbar({ ...snackbar, open: false })}
                    severity={snackbar.severity}
                    sx={{ width: '100%' }}
                >
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </Box>
    );
};
