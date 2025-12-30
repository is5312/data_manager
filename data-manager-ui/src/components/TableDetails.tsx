import React, { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AgGridReact } from 'ag-grid-react';
import {
    ModuleRegistry,
    ClientSideRowModelModule,
    PaginationModule,
    ColDef
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
    Chip
} from '@mui/material';
import {
    ArrowBack as ArrowBackIcon,
    TableChart as TableChartIcon,
    Refresh as RefreshIcon
} from '@mui/icons-material';
import { fetchTableColumns, ColumnMetadata, fetchTables, TableMetadata, fetchTableById } from '../services/api';
import './LandingPage.css'; // Re-use the theme styles

// Register AG Grid modules
ModuleRegistry.registerModules([ClientSideRowModelModule, PaginationModule]);

export const TableDetails: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [columns, setColumns] = useState<ColumnMetadata[]>([]);
    const [tableInfo, setTableInfo] = useState<TableMetadata | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const loadTableDetails = async () => {
            if (!id) return;

            try {
                setLoading(true);

                // Fetch table info and columns in parallel
                const [table, cols] = await Promise.all([
                    fetchTableById(Number(id)),
                    fetchTableColumns(Number(id))
                ]);

                setTableInfo(table);
                setColumns(cols);
                setLoading(false);
            } catch (err) {
                console.error("Error loading table details", err);
                setError("Failed to load table details");
                setLoading(false);
            }
        };

        loadTableDetails();
    }, [id]);

    const colDefs = useMemo<ColDef<ColumnMetadata>[]>(() => [
        {
            field: 'id',
            headerName: 'ID',
            width: 70,
            cellStyle: () => ({ color: '#A0A0A0', fontFamily: 'Roboto Mono' })
        },
        {
            field: 'label',
            headerName: 'FIELD LABEL',
            flex: 1,
            cellStyle: () => ({ fontWeight: 700, color: '#FF6200' })
        },
        {
            field: 'type',
            headerName: 'TYPE',
            width: 140,
            cellStyle: () => ({ color: '#E0E0E0', fontFamily: 'Roboto Mono' })
        },
        {
            field: 'physicalName',
            headerName: 'PHYSICAL COLUMN',
            flex: 1,
            cellStyle: () => ({ color: '#E0E0E0', fontFamily: 'Roboto Mono' })
        },
        {
            field: 'createdAt',
            headerName: 'CREATED AT',
            width: 200,
            valueFormatter: (params) => params.value ? new Date(params.value).toLocaleString() : '',
            cellStyle: () => ({ color: '#A0A0A0', fontFamily: 'Roboto Mono', fontSize: '11px' })
        }
    ], []);

    return (
        <Box sx={{ minHeight: '100vh', background: '#000000', pt: 2 }}>
            <Container maxWidth="xl">
                {/* Header */}
                <Box sx={{
                    borderBottom: '1px solid #333',
                    mb: 2,
                    pb: 1,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center'
                }}>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <Button
                            startIcon={<ArrowBackIcon />}
                            onClick={() => navigate('/')}
                            sx={{ color: 'text.secondary', minWidth: 'auto' }}
                        >
                            BACK
                        </Button>
                        <Box sx={{ width: '1px', height: '24px', bgcolor: '#333' }} />
                        <TableChartIcon sx={{ color: 'primary.main' }} />
                        <Typography variant="h6" sx={{ color: '#E0E0E0', letterSpacing: '0.1em' }}>
                            {tableInfo?.label || 'LOADING...'} <span style={{ color: '#666', fontSize: '0.8em', marginLeft: '8px' }}>{tableInfo?.physicalName}</span>
                        </Typography>
                        {tableInfo && (
                            <Chip
                                label="ACTIVE"
                                color="success"
                                size="small"
                                variant="outlined"
                                sx={{ borderRadius: 0, height: '20px', fontSize: '10px' }}
                            />
                        )}
                    </Stack>
                </Box>

                {error && (
                    <Alert severity="error" variant="outlined" sx={{ mb: 2, borderRadius: 0 }}>
                        {error}
                    </Alert>
                )}

                {/* Grid */}
                <Paper
                    className="grid-wrapper ag-theme-quartz-dark"
                    elevation={0}
                    sx={{
                        height: 'calc(100vh - 120px)',
                        borderRadius: 0,
                        border: '1px solid #333'
                    }}
                >
                    {loading ? (
                        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                            <CircularProgress sx={{ color: 'primary.main' }} />
                        </Box>
                    ) : (
                        <AgGridReact
                            rowData={columns}
                            columnDefs={colDefs}
                            defaultColDef={{
                                wrapHeaderText: true,
                                autoHeaderHeight: true
                            }}
                            pagination={true}
                            paginationPageSize={100}
                            rowHeight={32}
                            headerHeight={60}
                        />
                    )}
                </Paper>
            </Container>
        </Box>
    );
};
