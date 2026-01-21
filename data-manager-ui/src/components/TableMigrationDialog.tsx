import React, { useState, useEffect } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    SelectChangeEvent,
    TextField,
    Alert,
    CircularProgress,
    Box,
    Typography,
    Divider
} from '@mui/material';
import { TableMetadata, checkActiveMigration, ActiveMigrationDto } from '../services/api';
import { fetchAvailableSchemas, migrateTable } from '../services/api';

export interface TableMigrationDialogProps {
    open: boolean;
    onClose: () => void;
    onSuccess: (jobId: string) => void;
    table?: TableMetadata;
    sourceSchema: string;
    availableTables?: TableMetadata[];
}

export const TableMigrationDialog: React.FC<TableMigrationDialogProps> = ({
    open,
    onClose,
    onSuccess,
    table,
    sourceSchema,
    availableTables = []
}) => {
    const [targetSchema, setTargetSchema] = useState<string>('');
    const [selectedTableId, setSelectedTableId] = useState<number | null>(table?.id || null);
    const [availableSchemas, setAvailableSchemas] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);
    const [loadingSchemas, setLoadingSchemas] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);
    const [activeMigration, setActiveMigration] = useState<ActiveMigrationDto | null>(null);
    const [checkingActiveMigration, setCheckingActiveMigration] = useState(false);

    useEffect(() => {
        if (open) {
            setLoadingSchemas(true);
            setError(null);
            setSuccess(false);
            setTargetSchema('');
            setSelectedTableId(table?.id || null);
            
            fetchAvailableSchemas()
                .then(schemas => {
                    // Filter out source schema from available schemas
                    const filtered = schemas.filter(s => s !== sourceSchema);
                    setAvailableSchemas(filtered);
                    if (filtered.length > 0) {
                        setTargetSchema(filtered[0]);
                    }
                })
                .catch(err => {
                    console.error('Failed to fetch available schemas', err);
                    setError('Failed to load available schemas');
                })
                .finally(() => {
                    setLoadingSchemas(false);
                });
        }
    }, [open, sourceSchema, table]);

    const handleTargetSchemaChange = (event: SelectChangeEvent<string>) => {
        const newTargetSchema = event.target.value;
        setTargetSchema(newTargetSchema);
        
        // Check for active migration when target schema is selected
        if (selectedTableId && newTargetSchema) {
            setCheckingActiveMigration(true);
            checkActiveMigration(selectedTableId, newTargetSchema)
                .then(result => {
                    if (result.hasActiveMigration) {
                        setActiveMigration(result);
                    } else {
                        setActiveMigration(null);
                    }
                })
                .catch(err => {
                    console.error('Failed to check for active migration', err);
                    setActiveMigration(null);
                })
                .finally(() => {
                    setCheckingActiveMigration(false);
                });
        }
    };

    const handleTableChange = (event: SelectChangeEvent<number>) => {
        setSelectedTableId(event.target.value as number);
    };

    const handleMigrate = async () => {
        if (!selectedTableId || !targetSchema) {
            setError('Please select a table and target schema');
            return;
        }

        setLoading(true);
        setError(null);
        setSuccess(false);

        try {
            const response = await migrateTable(selectedTableId, sourceSchema, targetSchema);
            
            // Check if it's a duplicate (HTTP 409)
            if (response.status === 'DUPLICATE') {
                setError(`Migration already in progress. Job ID: ${response.jobId.substring(0, 8)}...`);
                setActiveMigration({
                    hasActiveMigration: true,
                    jobId: response.jobId,
                    status: response.status
                });
                return;
            }
            
            setSuccess(true);
            setTimeout(() => {
                onSuccess(response.jobId);
                onClose();
            }, 1500);
        } catch (err) {
            console.error('Migration failed', err);
            setError(err instanceof Error ? err.message : 'Migration failed');
        } finally {
            setLoading(false);
        }
    };

    const selectedTable = table || availableTables.find(t => t.id === selectedTableId);

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle>Migrate Table</DialogTitle>
            <DialogContent>
                {loadingSchemas ? (
                    <Box display="flex" justifyContent="center" p={3}>
                        <CircularProgress />
                    </Box>
                ) : (
                    <>
                        {error && (
                            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
                                {error}
                            </Alert>
                        )}
                        {success && (
                            <Alert severity="success" sx={{ mb: 2 }}>
                                Migration job queued successfully!
                            </Alert>
                        )}
                        {activeMigration && activeMigration.hasActiveMigration && (
                            <Alert severity="warning" sx={{ mb: 2 }}>
                                ⚠️ Migration already in progress for this schema
                                <br />
                                <Typography variant="body2" sx={{ mt: 1 }}>
                                    Job ID: {activeMigration.jobId?.substring(0, 8)}...
                                </Typography>
                            </Alert>
                        )}

                        <Box sx={{ mb: 3 }}>
                            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                                Source Schema
                            </Typography>
                            <TextField
                                fullWidth
                                value={sourceSchema}
                                disabled
                                size="small"
                                sx={{ mt: 1 }}
                            />
                        </Box>

                        {!table && (
                            <FormControl fullWidth sx={{ mb: 3 }}>
                                <InputLabel id="table-select-label">Select Table</InputLabel>
                                <Select
                                    labelId="table-select-label"
                                    id="table-select"
                                    value={selectedTableId || ''}
                                    label="Select Table"
                                    onChange={handleTableChange}
                                    disabled={loading}
                                >
                                    {availableTables.map((t) => (
                                        <MenuItem key={t.id} value={t.id}>
                                            {t.label} ({t.physicalName})
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        )}

                        {selectedTable && (
                            <Box sx={{ mb: 3 }}>
                                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                                    Table to Migrate
                                </Typography>
                                <TextField
                                    fullWidth
                                    value={`${selectedTable.label} (${selectedTable.physicalName})`}
                                    disabled
                                    size="small"
                                    sx={{ mt: 1 }}
                                />
                            </Box>
                        )}

                        <Divider sx={{ my: 2 }} />

                        <FormControl fullWidth>
                            <InputLabel id="target-schema-label">Target Schema</InputLabel>
                            <Select
                                labelId="target-schema-label"
                                id="target-schema"
                                value={targetSchema}
                                label="Target Schema"
                                onChange={handleTargetSchemaChange}
                                disabled={loading || availableSchemas.length === 0}
                            >
                                {availableSchemas.map((schema) => (
                                    <MenuItem key={schema} value={schema}>
                                        {schema}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        {availableSchemas.length === 0 && (
                            <Alert severity="warning" sx={{ mt: 2 }}>
                                No target schemas available
                            </Alert>
                        )}
                    </>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={loading}>
                    Cancel
                </Button>
                <Button
                    onClick={handleMigrate}
                    variant="contained"
                    disabled={loading || !selectedTableId || !targetSchema || availableSchemas.length === 0 || (activeMigration?.hasActiveMigration || false)}
                >
                    {loading ? <CircularProgress size={20} /> : 'Migrate'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
