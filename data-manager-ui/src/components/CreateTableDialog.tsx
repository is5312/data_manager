import React, { useState } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Button,
    Typography,
    Box,
    IconButton,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    Stack,
    Divider,
    Chip
} from '@mui/material';
import {
    Add as AddIcon,
    Delete as DeleteIcon
} from '@mui/icons-material';

interface Column {
    id: string;
    label: string;
    type: string;
}

interface CreateTableDialogProps {
    open: boolean;
    onClose: () => void;
    onSubmit: (tableName: string, columns: Column[], deploymentType: string) => void;
}

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

export const CreateTableDialog: React.FC<CreateTableDialogProps> = ({ open, onClose, onSubmit }) => {
    const [tableName, setTableName] = useState('');
    const [columns, setColumns] = useState<Column[]>([
        { id: '1', label: '', type: 'VARCHAR' }
    ]);
    const [deploymentType, setDeploymentType] = useState<string>('DESIGN_TIME');
    const [error, setError] = useState<string | null>(null);

    const handleAddColumn = () => {
        setColumns([...columns, { id: Date.now().toString(), label: '', type: 'VARCHAR' }]);
    };

    const handleRemoveColumn = (id: string) => {
        if (columns.length > 1) {
            setColumns(columns.filter(col => col.id !== id));
        }
    };

    const handleColumnChange = (id: string, field: 'label' | 'type', value: string) => {
        setColumns(columns.map(col =>
            col.id === id ? { ...col, [field]: value } : col
        ));
    };

    const handleSubmit = () => {
        if (!tableName.trim()) {
            setError('Table name is required');
            return;
        }
        if (!/^[a-zA-Z0-9_]+$/.test(tableName)) {
            setError('Table name must contain only alphanumeric characters and underscores');
            return;
        }

        // Validate columns
        const validColumns = columns.filter(col => col.label.trim());
        if (validColumns.length === 0) {
            setError('At least one column is required');
            return;
        }

        for (const col of validColumns) {
            if (!/^[a-zA-Z0-9_]+$/.test(col.label)) {
                setError(`Column "${col.label}" must contain only alphanumeric characters and underscores`);
                return;
            }
        }

        onSubmit(tableName, validColumns, deploymentType);

        // Reset form
        setTableName('');
        setColumns([{ id: '1', label: '', type: 'VARCHAR' }]);
        setDeploymentType('DESIGN_TIME');
        setError(null);
    };

    const handleClose = () => {
        setTableName('');
        setColumns([{ id: '1', label: '', type: 'VARCHAR' }]);
        setDeploymentType('DESIGN_TIME');
        setError(null);
        onClose();
    };

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            maxWidth="md"
            fullWidth
            PaperProps={{
                elevation: 0,
                sx: {
                    border: '1px solid #E0E0E0',
                    minWidth: '600px'
                }
            }}
        >
            <DialogTitle sx={{
                borderBottom: '1px solid #E0E0E0',
                backgroundColor: 'background.paper',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
                color: 'primary.main',
                fontSize: '0.875rem',
                fontWeight: 700,
                py: 1.5
            }}>
                Create New Table
            </DialogTitle>
            <DialogContent sx={{ mt: 2 }}>
                <Typography variant="body2" sx={{ mb: 3, color: 'text.secondary' }}>
                    Define your table structure by specifying a name and columns with their data types.
                </Typography>

                {/* Table Name */}
                <TextField
                    autoFocus
                    margin="dense"
                    id="name"
                    label="Table Name"
                    type="text"
                    fullWidth
                    variant="outlined"
                    value={tableName}
                    onChange={(e) => {
                        setTableName(e.target.value);
                        if (error) setError(null);
                    }}
                    error={!!error && error.includes('Table name')}
                    helperText={error && error.includes('Table name') ? error : ''}
                    size="small"
                    sx={{ mb: 3 }}
                />

                {/* Deployment Type */}
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                    <InputLabel>Deployment Type</InputLabel>
                    <Select
                        value={deploymentType}
                        label="Deployment Type"
                        onChange={(e) => setDeploymentType(e.target.value)}
                    >
                        <MenuItem value="DESIGN_TIME">DESIGN_TIME - Low volume tables where data is entered by a user via the UI</MenuItem>
                        <MenuItem value="RUN_TIME">RUN_TIME - High volume read and write tables where a background process will insert and read data</MenuItem>
                    </Select>
                </FormControl>

                <Divider sx={{ mb: 2 }}>
                    <Chip label="COLUMNS" size="small" sx={{ borderRadius: 0, fontWeight: 600 }} />
                </Divider>

                {/* Column Definitions */}
                <Stack spacing={2}>
                    {columns.map((column, index) => (
                        <Box key={column.id} sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
                            <TextField
                                label={`Column ${index + 1} Name`}
                                value={column.label}
                                onChange={(e) => handleColumnChange(column.id, 'label', e.target.value)}
                                size="small"
                                sx={{ flex: 1 }}
                                placeholder="e.g., customer_name"
                            />
                            <FormControl size="small" sx={{ minWidth: 150 }}>
                                <InputLabel>Data Type</InputLabel>
                                <Select
                                    value={column.type}
                                    label="Data Type"
                                    onChange={(e) => handleColumnChange(column.id, 'type', e.target.value)}
                                >
                                    {DATA_TYPES.map(type => (
                                        <MenuItem key={type} value={type}>{type}</MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                            <IconButton
                                onClick={() => handleRemoveColumn(column.id)}
                                disabled={columns.length === 1}
                                size="small"
                                sx={{
                                    color: 'error.main',
                                    '&:disabled': { color: 'action.disabled' }
                                }}
                            >
                                <DeleteIcon />
                            </IconButton>
                        </Box>
                    ))}
                </Stack>

                <Button
                    startIcon={<AddIcon />}
                    onClick={handleAddColumn}
                    variant="outlined"
                    size="small"
                    sx={{ mt: 2, borderRadius: 0 }}
                >
                    Add Column
                </Button>

                {error && !error.includes('Table name') && (
                    <Typography variant="body2" color="error" sx={{ mt: 2 }}>
                        {error}
                    </Typography>
                )}
            </DialogContent>
            <DialogActions sx={{ p: 2, borderTop: '1px solid #E0E0E0', bgcolor: 'background.paper' }}>
                <Button onClick={handleClose} variant="outlined" color="inherit">
                    Cancel
                </Button>
                <Button onClick={handleSubmit} variant="contained" color="primary" disableElevation>
                    Create Table
                </Button>
            </DialogActions>
        </Dialog>
    );
};
