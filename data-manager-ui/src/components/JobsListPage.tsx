import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllEnterpriseModule } from 'ag-grid-enterprise';
import { ColDef, ICellRendererParams } from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import {
    Container,
    Typography,
    Box,
    Button,
    IconButton,
    Chip,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Paper,
    Stack,
    Tooltip,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    CircularProgress,
    Alert
} from '@mui/material';
import {
    Refresh as RefreshIcon,
    Visibility as ViewIcon
} from '@mui/icons-material';
import { NavigationBar } from './NavigationBar';
import { fetchJobs, fetchJobStatus, JobDetails } from '../services/api';

// Register all AG Grid Enterprise modules
ModuleRegistry.registerModules([AllEnterpriseModule]);

export const JobsListPage: React.FC = () => {
    const [rowData, setRowData] = useState<JobDetails[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [statusFilter, setStatusFilter] = useState<string>('');
    const [selectedJob, setSelectedJob] = useState<JobDetails | null>(null);
    const [detailsDialogOpen, setDetailsDialogOpen] = useState(false);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const loadJobs = useCallback(async () => {
        try {
            setLoading(true);
            const response = await fetchJobs(page, 100, statusFilter || undefined);
            setRowData(response.content);
            setTotalPages(response.totalPages);
            setError(null);
        } catch (err) {
            console.error('Error fetching jobs', err);
            setError(err instanceof Error ? err.message : 'Failed to fetch jobs');
        } finally {
            setLoading(false);
        }
    }, [page, statusFilter]);

    useEffect(() => {
        loadJobs();
    }, [loadJobs]);

    // Auto-refresh every 10 seconds
    useEffect(() => {
        const intervalId = setInterval(loadJobs, 10000);
        return () => clearInterval(intervalId);
    }, [loadJobs]);

    const handleViewDetails = useCallback(async (jobId: string) => {
        try {
            const details = await fetchJobStatus(jobId);
            setSelectedJob(details);
            setDetailsDialogOpen(true);
        } catch (err) {
            console.error('Error fetching job details', err);
            setError(err instanceof Error ? err.message : 'Failed to fetch job details');
        }
    }, []);

    const getStatusColor = (status: string): "success" | "error" | "info" | "warning" | "default" => {
        switch (status) {
            case 'SUCCEEDED':
                return 'success';
            case 'FAILED':
                return 'error';
            case 'PROCESSING':
                return 'info';
            case 'ENQUEUED':
                return 'warning';
            default:
                return 'default';
        }
    };

    const columnDefs = useMemo<ColDef<JobDetails>[]>(() => [
        {
            headerName: 'Job ID',
            field: 'jobId',
            width: 150,
            cellRenderer: (params: ICellRendererParams<JobDetails>) => (
                <Typography variant="body2" sx={{ fontSize: '0.75rem', fontFamily: 'monospace' }}>
                    {params.value?.substring(0, 8)}...
                </Typography>
            )
        },
        {
            headerName: 'Job Name',
            field: 'jobName',
            flex: 1,
            minWidth: 300
        },
        {
            headerName: 'Status',
            field: 'status',
            width: 140,
            cellRenderer: (params: ICellRendererParams<JobDetails>) => (
                <Chip
                    label={params.value}
                    color={getStatusColor(params.value)}
                    size="small"
                />
            )
        },
        {
            headerName: 'Created At',
            field: 'createdAt',
            width: 180,
            valueFormatter: (params) => 
                params.value ? new Date(params.value).toLocaleString() : ''
        },
        {
            headerName: 'Updated At',
            field: 'updatedAt',
            width: 180,
            valueFormatter: (params) => 
                params.value ? new Date(params.value).toLocaleString() : ''
        },
        {
            headerName: 'Actions',
            width: 100,
            cellRenderer: (params: ICellRendererParams<JobDetails>) => (
                <Tooltip title="View Details">
                    <IconButton
                        size="small"
                        onClick={() => handleViewDetails(params.data!.jobId)}
                    >
                        <ViewIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
            )
        }
    ], [handleViewDetails]);

    const defaultColDef = useMemo<ColDef>(() => ({
        sortable: true,
        filter: true,
        resizable: true
    }), []);

    return (
        <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
            <NavigationBar />
            <Container maxWidth="xl" sx={{ py: 4 }}>
                <Paper elevation={2} sx={{ p: 3 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
                        <Typography variant="h4" component="h1">
                            Migration Jobs
                        </Typography>
                        <Stack direction="row" spacing={2} alignItems="center">
                            <FormControl size="small" sx={{ minWidth: 150 }}>
                                <InputLabel>Status Filter</InputLabel>
                                <Select
                                    value={statusFilter}
                                    label="Status Filter"
                                    onChange={(e) => {
                                        setStatusFilter(e.target.value);
                                        setPage(0);
                                    }}
                                >
                                    <MenuItem value="">All</MenuItem>
                                    <MenuItem value="ENQUEUED">Enqueued</MenuItem>
                                    <MenuItem value="PROCESSING">Processing</MenuItem>
                                    <MenuItem value="SUCCEEDED">Succeeded</MenuItem>
                                    <MenuItem value="FAILED">Failed</MenuItem>
                                </Select>
                            </FormControl>
                            <Tooltip title="Refresh">
                                <IconButton onClick={loadJobs} color="primary">
                                    <RefreshIcon />
                                </IconButton>
                            </Tooltip>
                        </Stack>
                    </Stack>

                    {error && (
                        <Alert severity="error" sx={{ mb: 2 }}>
                            {error}
                        </Alert>
                    )}

                    <Box sx={{ height: 600, width: '100%' }}>
                        {loading && rowData.length === 0 ? (
                            <Box display="flex" justifyContent="center" alignItems="center" height="100%">
                                <CircularProgress />
                            </Box>
                        ) : (
                            <div className="ag-theme-quartz" style={{ height: '100%', width: '100%' }}>
                                <AgGridReact<JobDetails>
                                    rowData={rowData}
                                    columnDefs={columnDefs}
                                    defaultColDef={defaultColDef}
                                    pagination={false}
                                    domLayout="normal"
                                    suppressCellFocus={true}
                                />
                            </div>
                        )}
                    </Box>
                </Paper>
            </Container>

            {/* Job Details Dialog */}
            <Dialog 
                open={detailsDialogOpen} 
                onClose={() => setDetailsDialogOpen(false)}
                maxWidth="md"
                fullWidth
            >
                <DialogTitle>Job Details</DialogTitle>
                <DialogContent>
                    {selectedJob && (
                        <Box>
                            <Typography variant="body2" gutterBottom>
                                <strong>Job ID:</strong> {selectedJob.jobId}
                            </Typography>
                            <Typography variant="body2" gutterBottom>
                                <strong>Job Name:</strong> {selectedJob.jobName}
                            </Typography>
                            <Typography variant="body2" gutterBottom>
                                <strong>Status:</strong> <Chip 
                                    label={selectedJob.status} 
                                    color={getStatusColor(selectedJob.status)}
                                    size="small"
                                />
                            </Typography>
                            <Typography variant="body2" gutterBottom>
                                <strong>Created:</strong> {new Date(selectedJob.createdAt).toLocaleString()}
                            </Typography>
                            <Typography variant="body2" gutterBottom>
                                <strong>Updated:</strong> {new Date(selectedJob.updatedAt).toLocaleString()}
                            </Typography>

                            {selectedJob.result && (
                                <Box mt={2}>
                                    <Typography variant="subtitle2" gutterBottom>
                                        <strong>Result:</strong>
                                    </Typography>
                                    <Alert severity="success">
                                        <Typography variant="body2">
                                            {selectedJob.result.message}
                                        </Typography>
                                        {selectedJob.result.shadowTableName && (
                                            <Typography variant="body2" sx={{ mt: 1 }}>
                                                Shadow Table: <strong>{selectedJob.result.shadowTableName}</strong>
                                            </Typography>
                                        )}
                                    </Alert>
                                </Box>
                            )}

                            {selectedJob.failureReason && (
                                <Box mt={2}>
                                    <Typography variant="subtitle2" gutterBottom>
                                        <strong>Error:</strong>
                                    </Typography>
                                    <Alert severity="error">
                                        <Typography variant="body2">
                                            {selectedJob.failureReason}
                                        </Typography>
                                    </Alert>
                                </Box>
                            )}
                        </Box>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDetailsDialogOpen(false)} variant="contained">
                        Close
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
