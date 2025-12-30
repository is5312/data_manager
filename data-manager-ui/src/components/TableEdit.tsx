import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Container,
    Typography,
    Box,
    CircularProgress,
    Alert,
    Paper,
    Stack,
    Button,
    List,
    ListItem,
    ListItemText,
    IconButton,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    Chip,
    Snackbar,
    Tooltip
} from '@mui/material';
import {
    ArrowBack as ArrowBackIcon,
    Delete as DeleteIcon,
    Add as AddIcon,
    TableChart as TableChartIcon
} from '@mui/icons-material';
import { fetchTableById, fetchTableColumns, addColumn, changeColumnType, TableMetadata, ColumnMetadata } from '../services/api';
import './LandingPage.css';

export const TableEdit: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [tableInfo, setTableInfo] = useState<TableMetadata | null>(null);
    const [columns, setColumns] = useState<ColumnMetadata[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [addColumnDialogOpen, setAddColumnDialogOpen] = useState(false);
    const [newColumn, setNewColumn] = useState({ label: '', type: 'VARCHAR' });
    const [changeTypeDialogOpen, setChangeTypeDialogOpen] = useState(false);
    const [selectedColumn, setSelectedColumn] = useState<ColumnMetadata | null>(null);
    const [selectedType, setSelectedType] = useState<string>('VARCHAR');
    const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
        open: false,
        message: '',
        severity: 'success'
    });

    const DATA_TYPES = [
        'VARCHAR',
        'INTEGER',
        'BIGINT',
        'DECIMAL',
        'BOOLEAN',
        'DATE',
        'TIMESTAMP',
        'TEXT'
    ];

    const loadTableInfo = useCallback(async () => {
        if (!id) return;

        try {
            setLoading(true);
            const [table, cols] = await Promise.all([
                fetchTableById(Number(id)),
                fetchTableColumns(Number(id))
            ]);
            setTableInfo(table);
            setColumns(cols);
            setLoading(false);
        } catch (err) {
            console.error("Error loading table info", err);
            setError("Failed to load table information");
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        loadTableInfo();
    }, [loadTableInfo]);

    const handleAddColumn = async () => {
        try {
            await addColumn(Number(id), newColumn.label, newColumn.type);
            setAddColumnDialogOpen(false);
            setNewColumn({ label: '', type: 'VARCHAR' });
            loadTableInfo(); // Refresh
        } catch (err) {
            console.error("Failed to add column", err);
            alert("Failed to add column. Check console.");
        }
    };

    const openChangeTypeDialog = (col: ColumnMetadata) => {
        setSelectedColumn(col);
        setSelectedType(col.type || 'VARCHAR');
        setChangeTypeDialogOpen(true);
    };

    const handleChangeColumnType = async () => {
        if (!selectedColumn) return;
        try {
            await changeColumnType(Number(id), selectedColumn.id, selectedType);
            setSnackbar({ open: true, message: 'Column type updated', severity: 'success' });
            setChangeTypeDialogOpen(false);
            setSelectedColumn(null);
            loadTableInfo();
        } catch (err: any) {
            console.error("Failed to change column type", err);
            setSnackbar({ open: true, message: err?.message || 'Failed to change column type', severity: 'error' });
        }
    };

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
                        <Tooltip title="Go Back">
                            <IconButton
                                onClick={() => navigate('/')}
                                sx={{ color: 'text.secondary' }}
                                size="small"
                            >
                                <ArrowBackIcon />
                            </IconButton>
                        </Tooltip>
                        <Box sx={{ width: '1px', height: '20px', bgcolor: '#E0E0E0' }} />
                        <TableChartIcon sx={{ color: 'primary.main', fontSize: 20 }} />
                        <Typography variant="h6" sx={{ color: 'text.primary', letterSpacing: '0.1em', fontSize: '0.875rem' }}>
                            EDIT TABLE: {tableInfo?.label || 'LOADING...'}
                        </Typography>
                        {tableInfo && (
                            <Chip
                                label={tableInfo.physicalName}
                                size="small"
                                variant="outlined"
                                sx={{ borderRadius: 0 }}
                            />
                        )}
                    </Stack>
                    <Tooltip title="Add New Column">
                        <IconButton
                            color="primary"
                            onClick={() => setAddColumnDialogOpen(true)}
                            disabled={loading}
                            size="small"
                            sx={{
                                borderRadius: 1,
                                border: '1px solid',
                                borderColor: 'primary.light'
                            }}
                        >
                            <AddIcon />
                        </IconButton>
                    </Tooltip>
                </Box>

                {error && (
                    <Alert severity="error" variant="outlined" sx={{ mb: 1.5, borderRadius: 0 }}>
                        {error}
                    </Alert>
                )}

                {/* Columns List */}
                <Paper
                    elevation={0}
                    sx={{
                        borderRadius: 0,
                        border: '1px solid #E0E0E0',
                        bgcolor: '#FAFAFA',
                        p: 2
                    }}
                >
                    {loading ? (
                        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
                            <CircularProgress sx={{ color: 'primary.main' }} />
                        </Box>
                    ) : (
                        <>
                            <Typography variant="h6" sx={{ color: 'text.primary', mb: 1.5, letterSpacing: '0.05em', fontSize: '0.75rem' }}>
                                COLUMNS ({columns.length})
                            </Typography>
                            <List>
                                {columns.map((col) => (
                                    <ListItem
                                        key={col.id}
                                        sx={{
                                            border: '1px solid #E0E0E0',
                                            mb: 1,
                                            bgcolor: '#FFFFFF',
                                            '&:hover': { bgcolor: '#F5F5F5' },
                                            py: 1.5
                                        }}
                                        secondaryAction={
                                            <IconButton edge="end" sx={{ color: 'error.main' }}>
                                                <DeleteIcon />
                                            </IconButton>
                                        }
                                    >
                                        <ListItemText
                                            primary={
                                                <Stack direction="row" spacing={1} alignItems="center">
                                                    <Typography sx={{ color: 'text.primary', fontWeight: 600, fontSize: '0.875rem' }}>
                                                        {col.label}
                                                    </Typography>
                                                    {col.versionNo && (
                                                        <Chip
                                                            label={`v${col.versionNo}`}
                                                            size="small"
                                                            sx={{ height: '18px', fontSize: '0.65rem', bgcolor: '#E3F2FD', color: '#0066CC' }}
                                                        />
                                                    )}
                                                </Stack>
                                            }
                                            secondary={
                                                <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                                    <Typography sx={{ color: 'text.secondary', fontSize: '0.75rem', fontFamily: 'Roboto Mono' }}>
                                                        Physical: {col.physicalName}
                                                    </Typography>
                                                    {col.description && (
                                                        <Typography sx={{ color: '#666', fontSize: '0.75rem', fontStyle: 'italic' }}>
                                                            {col.description}
                                                        </Typography>
                                                    )}
                                                    <Stack direction="row" spacing={2} sx={{ fontSize: '0.7rem', color: '#999' }}>
                                                        {col.createdBy && (
                                                            <Typography variant="caption" sx={{ fontSize: '0.7rem' }}>
                                                                Created: {col.createdBy} {col.createdAt && `(${new Date(col.createdAt).toLocaleDateString()})`}
                                                            </Typography>
                                                        )}
                                                        {col.updatedBy && (
                                                            <Typography variant="caption" sx={{ fontSize: '0.7rem' }}>
                                                                Updated: {col.updatedBy} {col.updatedAt && `(${new Date(col.updatedAt).toLocaleDateString()})`}
                                                            </Typography>
                                                        )}
                                                    </Stack>
                                                </Stack>
                                            }
                                        />
                                        <Chip
                                            label={col.type || 'VARCHAR'}
                                            size="small"
                                            sx={{ mr: 3, borderRadius: 0, bgcolor: '#F0F0F0', fontSize: '0.7rem', height: '18px' }}
                                            onClick={() => openChangeTypeDialog(col)}
                                        />
                                    </ListItem>
                                ))}
                            </List>
                        </>
                    )}
                </Paper>
            </Container>

            {/* Add Column Dialog */}
            <Dialog
                open={addColumnDialogOpen}
                onClose={() => setAddColumnDialogOpen(false)}
                maxWidth="sm"
                fullWidth
                PaperProps={{
                    elevation: 0,
                    sx: {
                        border: '1px solid #E0E0E0',
                        minWidth: '500px'
                    }
                }}
            >
                <DialogTitle sx={{
                    borderBottom: '1px solid #E0E0E0',
                    backgroundColor: 'background.paper',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                    color: 'secondary.main',
                    fontWeight: 700
                }}>
                    Add New Column
                </DialogTitle>
                <DialogContent sx={{ mt: 3 }}>
                    <Stack spacing={2}>
                        <TextField
                            label="Column Label"
                            value={newColumn.label}
                            onChange={(e) => setNewColumn({ ...newColumn, label: e.target.value })}
                            size="small"
                            fullWidth
                            autoFocus
                        />
                        <FormControl size="small" fullWidth>
                            <InputLabel>Data Type</InputLabel>
                            <Select
                                value={newColumn.type}
                                label="Data Type"
                                onChange={(e) => setNewColumn({ ...newColumn, type: e.target.value })}
                            >
                                <MenuItem value="VARCHAR">VARCHAR</MenuItem>
                                <MenuItem value="INTEGER">INTEGER</MenuItem>
                                <MenuItem value="BIGINT">BIGINT</MenuItem>
                                <MenuItem value="DECIMAL">DECIMAL</MenuItem>
                                <MenuItem value="BOOLEAN">BOOLEAN</MenuItem>
                                <MenuItem value="DATE">DATE</MenuItem>
                                <MenuItem value="TIMESTAMP">TIMESTAMP</MenuItem>
                                <MenuItem value="TEXT">TEXT</MenuItem>
                            </Select>
                        </FormControl>
                    </Stack>
                </DialogContent>
                <DialogActions sx={{ p: 2, borderTop: '1px solid #E0E0E0', bgcolor: 'background.paper' }}>
                    <Button onClick={() => setAddColumnDialogOpen(false)} variant="outlined" color="inherit">
                        Cancel
                    </Button>
                    <Button onClick={handleAddColumn} variant="contained" color="primary" disabled={!newColumn.label} disableElevation>
                        Add Column
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Change Column Type Dialog */}
            <Dialog
                open={changeTypeDialogOpen}
                onClose={() => setChangeTypeDialogOpen(false)}
                maxWidth="sm"
                fullWidth
                PaperProps={{
                    elevation: 0,
                    sx: {
                        border: '1px solid #E0E0E0',
                        minWidth: '500px'
                    }
                }}
            >
                <DialogTitle sx={{
                    borderBottom: '1px solid #E0E0E0',
                    backgroundColor: 'background.paper',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                    color: 'secondary.main',
                    fontWeight: 700
                }}>
                    Change Column Type
                </DialogTitle>
                <DialogContent sx={{ mt: 3 }}>
                    <Stack spacing={2}>
                        <TextField
                            label="Column"
                            value={selectedColumn?.label || ''}
                            size="small"
                            fullWidth
                            disabled
                        />
                        <FormControl size="small" fullWidth>
                            <InputLabel>Data Type</InputLabel>
                            <Select
                                value={selectedType}
                                label="Data Type"
                                onChange={(e) => setSelectedType(String(e.target.value))}
                            >
                                {DATA_TYPES.map(type => (
                                    <MenuItem key={type} value={type}>{type}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <Alert severity="warning" variant="outlined" sx={{ borderRadius: 0 }}>
                            Changing a column type may fail if existing data can’t be converted (e.g. VARCHAR → BIGINT).
                        </Alert>
                    </Stack>
                </DialogContent>
                <DialogActions sx={{ p: 2, borderTop: '1px solid #E0E0E0', bgcolor: 'background.paper' }}>
                    <Button onClick={() => setChangeTypeDialogOpen(false)} variant="outlined" color="inherit">
                        Cancel
                    </Button>
                    <Button onClick={handleChangeColumnType} variant="contained" color="primary" disableElevation>
                        Update Type
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                open={snackbar.open}
                autoHideDuration={4000}
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
