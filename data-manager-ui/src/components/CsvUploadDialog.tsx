import React, { useState, useEffect } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    TextField,
    Box,
    Typography,
    Alert,
    Stack,
    Checkbox,
    List,
    ListItem,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Paper,
    Divider,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Accordion,
    AccordionSummary,
    AccordionDetails,
} from '@mui/material';
import { CloudUpload as UploadIcon, Warning as WarningIcon, ExpandMore as ExpandMoreIcon, Settings as SettingsIcon } from '@mui/icons-material';

interface CsvUploadDialogProps {
    open: boolean;
    onClose: () => void;
    onUpload: (file: File, tableName: string, deploymentType: string, columnTypes?: string[], selectedColumnIndices?: number[], csvOptions?: { delimiter?: string; quoteChar?: string; escapeChar?: string }) => Promise<void>;
}

type ColumnPreview = {
    name: string;
    type: string;
};

export const CsvUploadDialog: React.FC<CsvUploadDialogProps> = ({ open, onClose, onUpload }) => {
    const [tableName, setTableName] = useState('');
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [deploymentType, setDeploymentType] = useState<string>('RUN_TIME');
    const [error, setError] = useState<string | null>(null);
    const [uploading, setUploading] = useState(false);
    const [columns, setColumns] = useState<ColumnPreview[]>([]);
    const [selectedColumns, setSelectedColumns] = useState<Set<number>>(new Set());
    const [showColumnSelector, setShowColumnSelector] = useState(false);
    const [maxColumnIndex, setMaxColumnIndex] = useState<number>(500);
    const [columnSearchFilter, setColumnSearchFilter] = useState<string>('');

    // Advanced CSV Options
    const [delimiter, setDelimiter] = useState<string>(',');
    const [quoteChar, setQuoteChar] = useState<string>('"');
    const [escapeChar, setEscapeChar] = useState<string>('\\');
    const [customDelimiter, setCustomDelimiter] = useState<string>('');
    const [showAdvanced, setShowAdvanced] = useState(false);

    // Cache the raw text for re-parsing when options change
    const [rawPreviewText, setRawPreviewText] = useState<string | null>(null);

    const MAX_COLUMNS = 500;

    // Track React render time for columns
    useEffect(() => {
        if (columns.length > 0) {
            console.log('🎨 React finished rendering', columns.length, 'column inputs');
            console.timeEnd('⏱️ React render columns');
        }
    }, [columns]);

    // Re-parse when options change
    useEffect(() => {
        if (rawPreviewText) {
            previewCsvText(rawPreviewText);
        }
    }, [delimiter, quoteChar, escapeChar, customDelimiter]);

    const parseCsvLine = (line: string): string[] => {
        const out: string[] = [];
        let cur = '';
        let inQuotes = false;

        const effectiveDelimiter = delimiter === 'custom' ? customDelimiter : delimiter;
        // Basic parsing logic (simple state machine)
        // Note: exact behavior may differ slightly from backend OpenCSV but checks out for preview

        for (let i = 0; i < line.length; i++) {
            const ch = line[i];

            if (ch === escapeChar && i + 1 < line.length) {
                cur += line[i + 1];
                i++;
                continue;
            }

            if (ch === quoteChar) {
                // Escaped quote (double quote) check can be tricky with custom escapes
                // Here we assume standard CSV: " inside "" is escaped, OR escapeChar + "
                if (inQuotes && i + 1 < line.length && line[i + 1] === quoteChar) {
                    cur += quoteChar;
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch === effectiveDelimiter && !inQuotes) {
                out.push(cur);
                cur = '';
                continue;
            }
            cur += ch;
        }
        out.push(cur);
        return out.map(v => v.trim());
    };

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (file) {
            console.time('⏱️ Total file preview');
            console.log('📁 File selected:', file.name, `(${(file.size / 1024 / 1024).toFixed(2)} MB)`);

            const lower = file.name.toLowerCase();
            const isCsv = lower.endsWith('.csv');
            const isGzip = lower.endsWith('.gz') || lower.endsWith('.gzip');

            if (!isCsv && !isGzip) {
                setError('Please select a .csv or .gz/.gzip file');
                setSelectedFile(null);
                setColumns([]);
                console.timeEnd('⏱️ Total file preview');
                return;
            }
            setSelectedFile(file);
            setError(null);

            // Auto-fill table name from filename
            if (!tableName) {
                const base = file.name
                    .replace(/\.csv$/i, '')
                    .replace(/\.gzip$/i, '')
                    .replace(/\.gz$/i, '');
                const name = base.replace(/[^a-zA-Z0-9_]/g, '_');
                setTableName(name);
            }

            // Preview: read header + sample rows for both CSV and GZIP
            if (isCsv) {
                console.time('⏱️ CSV text read');
                // Plain CSV: Read first 1MB
                file.slice(0, 1024 * 1024).text().then(text => {
                    console.timeEnd('⏱️ CSV text read');
                    console.log('📊 CSV preview data:', (text.length / 1024).toFixed(2), 'KB');
                    setRawPreviewText(text); // Cache for re-parsing
                    console.time('⏱️ CSV parse & type inference');
                    previewCsvText(text);
                    console.timeEnd('⏱️ CSV parse & type inference');
                    console.timeEnd('⏱️ Total file preview');
                }).catch(err => {
                    console.error('❌ CSV preview failed:', err);
                    console.timeEnd('⏱️ CSV text read');
                    console.timeEnd('⏱️ Total file preview');
                    setColumns([]);
                });
            } else if (isGzip) {
                console.time('⏱️ GZIP decompress');
                // GZIP: Decompress first chunk to get header + sample
                decompressGzipPreview(file).then(text => {
                    console.timeEnd('⏱️ GZIP decompress');
                    console.log('📊 Decompressed preview:', (text.length / 1024).toFixed(2), 'KB');
                    setRawPreviewText(text); // Cache for re-parsing
                    console.time('⏱️ GZIP parse & type inference');
                    previewCsvText(text);
                    console.timeEnd('⏱️ GZIP parse & type inference');
                    console.timeEnd('⏱️ Total file preview');
                }).catch(err => {
                    console.error('❌ GZIP preview failed:', err);
                    console.timeEnd('⏱️ GZIP decompress');
                    console.timeEnd('⏱️ Total file preview');
                    setColumns([]);
                });
            } else {
                console.timeEnd('⏱️ Total file preview');
                setColumns([]);
            }
        }
    };

    const previewCsvText = (text: string) => {
        const lines = text.split(/\r?\n/).filter(l => l.trim().length > 0);
        if (lines.length === 0) {
            setColumns([]);
            return;
        }
        const header = parseCsvLine(lines[0]);

        const nextCols: ColumnPreview[] = header.map((h, idx) => {
            const colName = h?.trim() || `column_${idx + 1}`;
            // Type inference removed - just use TEXT for all
            return { name: colName, type: 'TEXT' };
        });

        console.log('📋 Setting', nextCols.length, 'columns in state (React will render next)');
        console.time('⏱️ React render columns');
        setColumns(nextCols);

        // Check if we need column selection (>500 columns)
        if (nextCols.length > MAX_COLUMNS) {
            setShowColumnSelector(true);
            // Pre-select first 500 columns
            const initialSelection = new Set<number>();
            for (let i = 0; i < MAX_COLUMNS; i++) {
                initialSelection.add(i);
            }
            setSelectedColumns(initialSelection);
            setMaxColumnIndex(MAX_COLUMNS);
        } else {
            setShowColumnSelector(false);
            setSelectedColumns(new Set());
        }
    };

    const decompressGzipPreview = async (file: File): Promise<string> => {
        // Read only a small chunk of compressed data (50KB should give us plenty for preview)
        // A typical CSV row might be 100-500 bytes, so 50KB compressed could be 500KB+ uncompressed
        const chunk = file.slice(0, 50 * 1024);
        const arrayBuffer = await chunk.arrayBuffer();

        // Decompress using browser's native DecompressionStream (modern browsers)
        if ('DecompressionStream' in window) {
            const stream = new ReadableStream({
                start(controller) {
                    controller.enqueue(new Uint8Array(arrayBuffer));
                    controller.close();
                }
            });

            const decompressedStream = stream.pipeThrough(new (window as any).DecompressionStream('gzip'));
            const reader = decompressedStream.getReader();
            const chunks: Uint8Array[] = [];

            // Read decompressed data until we have enough for preview (~100KB is plenty for 50+ rows)
            let totalBytes = 0;
            const maxBytes = 100 * 1024;

            while (totalBytes < maxBytes) {
                const { done, value } = await reader.read();
                if (done) break;
                if (value && value instanceof Uint8Array) {
                    chunks.push(value);
                    totalBytes += value.length;
                }
            }

            // Cancel the rest of the stream
            reader.cancel();

            // Combine chunks and decode to text
            const combined = new Uint8Array(totalBytes);
            let offset = 0;
            for (const chunk of chunks) {
                combined.set(chunk, offset);
                offset += chunk.length;
            }

            return new TextDecoder().decode(combined);
        } else {
            throw new Error('Browser does not support DecompressionStream');
        }
    };

    const handleSubmit = async () => {
        if (!selectedFile || !tableName.trim()) {
            setError('Please provide both a file and table name');
            return;
        }

        // Check if column selection is required but not done
        if (showColumnSelector && selectedColumns.size === 0) {
            setError('Please select at least one column (max 500)');
            return;
        }

        if (showColumnSelector && selectedColumns.size > MAX_COLUMNS) {
            setError(`Too many columns selected (${selectedColumns.size}). Maximum is ${MAX_COLUMNS}.`);
            return;
        }

        console.time('⏱️ Upload API call');
        console.log('🚀 Starting upload:', tableName.trim(), `(${showColumnSelector ? selectedColumns.size : columns.length} columns)`);
        setUploading(true);
        setError(null);

        // Prepare selected column indices (only if column selector was shown)
        const columnIndices = showColumnSelector
            ? Array.from(selectedColumns).sort((a, b) => a - b)  // Convert Set to sorted array
            : undefined;  // null means "all columns"

        console.log('📋 Selected column indices:', columnIndices?.length || 'all', columnIndices?.slice(0, 10));

        // Close dialog immediately - don't wait for upload to complete
        handleClose();

        // Fire-and-forget: start the upload but don't await it
        // The LandingPage will handle tracking progress via batch ID
        const effectiveDelimiter = delimiter === 'custom' ? customDelimiter : delimiter;
        const opts = {
            delimiter: effectiveDelimiter,
            quoteChar,
            escapeChar
        };

        onUpload(selectedFile, tableName.trim(), deploymentType, undefined, columnIndices, opts)
            .then(() => {
                console.timeEnd('⏱️ Upload API call');
                console.log('✅ Upload initiated successfully');
            })
            .catch((err: any) => {
                console.error('❌ Upload failed:', err);
                console.timeEnd('⏱️ Upload API call');
                // Error will be shown in batch progress dialog
            });
    };

    const handleToggleColumn = (index: number) => {
        const newSelection = new Set(selectedColumns);
        if (newSelection.has(index)) {
            newSelection.delete(index);
        } else {
            if (newSelection.size >= MAX_COLUMNS) {
                setError(`Maximum ${MAX_COLUMNS} columns allowed`);
                return;
            }
            newSelection.add(index);
        }
        setSelectedColumns(newSelection);
        setError(null);
    };

    const handleSelectRange = () => {
        if (maxColumnIndex < 1 || maxColumnIndex > MAX_COLUMNS) {
            setError(`Range must be between 1 and ${MAX_COLUMNS}`);
            return;
        }
        const newSelection = new Set<number>();
        for (let i = 0; i < Math.min(maxColumnIndex, columns.length); i++) {
            newSelection.add(i);
        }
        setSelectedColumns(newSelection);
        setError(null);
    };

    const handleSelectAll = () => {
        const newSelection = new Set<number>();
        for (let i = 0; i < Math.min(MAX_COLUMNS, columns.length); i++) {
            newSelection.add(i);
        }
        setSelectedColumns(newSelection);
    };

    const handleDeselectAll = () => {
        setSelectedColumns(new Set());
    };

    const handleClose = () => {
        setTableName('');
        setSelectedFile(null);
        setDeploymentType('RUN_TIME');
        setError(null);
        setUploading(false);
        setColumns([]);
        setSelectedColumns(new Set());
        setShowColumnSelector(false);
        setMaxColumnIndex(500);
        setColumnSearchFilter('');
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth={showColumnSelector ? "md" : "sm"} fullWidth>
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
                Upload CSV File {showColumnSelector && '- Select Columns'}
            </DialogTitle>
            <DialogContent sx={{ mt: 2 }}>
                <Typography variant="body2" sx={{ mb: 2, color: 'text.secondary', fontSize: '0.8rem' }}>
                    Upload a CSV or GZIP file to create a table and load data as a background batch job
                </Typography>

                {error && (
                    <Alert severity="error" variant="outlined" sx={{ mb: 2, borderRadius: 0 }}>
                        {error}
                    </Alert>
                )}

                <TextField
                    fullWidth
                    label="Table Name"
                    value={tableName}
                    onChange={(e) => setTableName(e.target.value)}
                    placeholder="Enter table name"
                    sx={{ mb: 2 }}
                    size="small"
                    required
                />

                <FormControl fullWidth size="small" sx={{ mb: 2 }}>
                    <InputLabel>Deployment Type</InputLabel>
                    <Select
                        value={deploymentType}
                        label="Deployment Type"
                        onChange={(e) => setDeploymentType(e.target.value)}
                    >
                        <MenuItem value="RUN_TIME">RUN_TIME - High volume read and write tables where a background process will insert and read data</MenuItem>
                        <MenuItem value="DESIGN_TIME">DESIGN_TIME - Low volume tables where data is entered by a user via the UI</MenuItem>
                    </Select>
                </FormControl>

                <Accordion expanded={showAdvanced} onChange={() => setShowAdvanced(!showAdvanced)} sx={{ mb: 2, border: '1px solid #E0E0E0', boxShadow: 'none' }}>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                        <Stack direction="row" spacing={1} alignItems="center">
                            <SettingsIcon fontSize="small" color="action" />
                            <Typography variant="body2">Advanced Options (Delimiter, Quotes)</Typography>
                        </Stack>
                    </AccordionSummary>
                    <AccordionDetails>
                        <Stack spacing={2}>
                            <Stack direction="row" spacing={2}>
                                <Box sx={{ width: '50%' }}>
                                    <FormControl fullWidth size="small">
                                        <InputLabel>Delimiter</InputLabel>
                                        <Select
                                            value={delimiter}
                                            label="Delimiter"
                                            onChange={(e) => setDelimiter(e.target.value)}
                                        >
                                            <MenuItem value=",">Comma (,)</MenuItem>
                                            <MenuItem value=";">Semicolon (;)</MenuItem>
                                            <MenuItem value="|">Pipe (|)</MenuItem>
                                            <MenuItem value="\t">Tab (\t)</MenuItem>
                                            <MenuItem value="custom">Custom</MenuItem>
                                        </Select>
                                    </FormControl>
                                </Box>
                                {delimiter === 'custom' && (
                                    <Box sx={{ width: '50%' }}>
                                        <TextField
                                            fullWidth
                                            size="small"
                                            label="Custom Delimiter"
                                            value={customDelimiter}
                                            onChange={(e) => setCustomDelimiter(e.target.value)}
                                            inputProps={{ maxLength: 1 }}
                                        />
                                    </Box>
                                )}
                            </Stack>
                            <Stack direction="row" spacing={2}>
                                <Box sx={{ width: '50%' }}>
                                    <FormControl fullWidth size="small">
                                        <InputLabel>Quote Char</InputLabel>
                                        <Select
                                            value={quoteChar}
                                            label="Quote Char"
                                            onChange={(e) => setQuoteChar(e.target.value)}
                                        >
                                            <MenuItem value={'"'}>Default (")</MenuItem>
                                            <MenuItem value={"'"}>Single Quote (')</MenuItem>
                                            <MenuItem value={""}>None</MenuItem>
                                        </Select>
                                    </FormControl>
                                </Box>
                                <Box sx={{ width: '50%' }}>
                                    <FormControl fullWidth size="small">
                                        <InputLabel>Escape Char</InputLabel>
                                        <Select
                                            value={escapeChar}
                                            label="Escape Char"
                                            onChange={(e) => setEscapeChar(e.target.value)}
                                        >
                                            <MenuItem value={'\\'}>Backslash (\)</MenuItem>
                                            <MenuItem value={'"'}>Double Quote (")</MenuItem>
                                            <MenuItem value={""}>None</MenuItem>
                                        </Select>
                                    </FormControl>
                                </Box>
                            </Stack>
                        </Stack>
                    </AccordionDetails>
                </Accordion>

                <Box
                    sx={{
                        border: '2px dashed #E0E0E0',
                        borderRadius: 0,
                        p: 3,
                        textAlign: 'center',
                        cursor: 'pointer',
                        '&:hover': {
                            borderColor: 'primary.main',
                            bgcolor: 'rgba(0, 102, 204, 0.02)'
                        }
                    }}
                    onClick={() => document.getElementById('csv-file-input')?.click()}
                >
                    <input
                        id="csv-file-input"
                        type="file"
                        accept=".csv,.gz,.gzip"
                        onChange={handleFileChange}
                        style={{ display: 'none' }}
                    />
                    <UploadIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                        {selectedFile ? selectedFile.name : 'Click to select CSV or GZIP file'}
                    </Typography>
                    {selectedFile && (
                        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 1 }}>
                            {(selectedFile.size / 1024).toFixed(2)} KB
                        </Typography>
                    )}
                </Box>

                {selectedFile && (selectedFile.name.toLowerCase().endsWith('.gz') || selectedFile.name.toLowerCase().endsWith('.gzip')) && (
                    <Alert severity="info" variant="outlined" sx={{ mt: 2, borderRadius: 0 }}>
                        GZIP uploads are processed as a background batch job. We'll infer the schema by streaming the file (no full decompression in the browser).
                    </Alert>
                )}

                {showColumnSelector ? (
                    <Box sx={{ mt: 2 }}>
                        <Alert severity="warning" icon={<WarningIcon />} variant="outlined" sx={{ mb: 2, borderRadius: 0 }}>
                            <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
                                ⚠️ Too many columns detected ({columns.length})
                            </Typography>
                            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                                PostgreSQL has a practical limit of ~500 columns per table. Please select which columns to include (max {MAX_COLUMNS}).
                            </Typography>
                        </Alert>

                        <Paper sx={{ p: 2, mb: 2, bgcolor: 'grey.50' }}>
                            <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
                                Quick Select: First N Columns
                            </Typography>
                            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                                <TextField
                                    type="number"
                                    size="small"
                                    value={maxColumnIndex}
                                    onChange={(e) => setMaxColumnIndex(Math.min(MAX_COLUMNS, Math.max(1, parseInt(e.target.value) || 1)))}
                                    inputProps={{ min: 1, max: MAX_COLUMNS }}
                                    sx={{ width: 100 }}
                                />
                                <Button size="small" variant="outlined" onClick={handleSelectRange}>
                                    Select 0 to {maxColumnIndex - 1}
                                </Button>
                                <Button size="small" onClick={handleSelectAll}>
                                    First {MAX_COLUMNS}
                                </Button>
                                <Button size="small" onClick={handleDeselectAll}>
                                    Clear All
                                </Button>
                            </Stack>

                            <Divider sx={{ my: 1.5 }} />

                            <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
                                Search Columns
                            </Typography>
                            <TextField
                                fullWidth
                                size="small"
                                placeholder="Filter by column name..."
                                value={columnSearchFilter}
                                onChange={(e) => setColumnSearchFilter(e.target.value)}
                                sx={{ mb: 1 }}
                            />

                            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                                Selected: {selectedColumns.size} / {MAX_COLUMNS} columns
                                {columnSearchFilter && ` • Showing ${columns.filter((col) =>
                                    col.name.toLowerCase().includes(columnSearchFilter.toLowerCase())
                                ).length} matches`}
                            </Typography>
                        </Paper>

                        <Paper sx={{ maxHeight: 500, overflow: 'auto', border: '1px solid #E0E0E0' }}>
                            <List dense>
                                {columns
                                    .map((col, idx) => ({ col, idx }))
                                    .filter(({ col }) =>
                                        !columnSearchFilter ||
                                        col.name.toLowerCase().includes(columnSearchFilter.toLowerCase()) ||
                                        col.type.toLowerCase().includes(columnSearchFilter.toLowerCase())
                                    )
                                    .map(({ col, idx }) => (
                                        <ListItem
                                            key={idx}
                                            disablePadding
                                            sx={{
                                                borderBottom: '1px solid #f0f0f0',
                                                '&:hover': { bgcolor: 'rgba(0, 102, 204, 0.04)' }
                                            }}
                                        >
                                            <ListItemButton onClick={() => handleToggleColumn(idx)} dense sx={{ py: 1.5 }}>
                                                <ListItemIcon sx={{ minWidth: 40 }}>
                                                    <Checkbox
                                                        edge="start"
                                                        checked={selectedColumns.has(idx)}
                                                        tabIndex={-1}
                                                        disableRipple
                                                        size="small"
                                                    />
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={
                                                        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                                                            <Typography
                                                                component="span"
                                                                sx={{
                                                                    fontFamily: 'monospace',
                                                                    fontSize: '0.75rem',
                                                                    color: 'text.secondary',
                                                                    minWidth: 45,
                                                                    flexShrink: 0,
                                                                    fontWeight: 600
                                                                }}
                                                            >
                                                                [{idx}]
                                                            </Typography>
                                                            <Typography
                                                                component="span"
                                                                sx={{
                                                                    fontSize: '0.875rem',
                                                                    wordBreak: 'break-word',
                                                                    whiteSpace: 'normal',
                                                                    lineHeight: 1.4,
                                                                    fontWeight: 500
                                                                }}
                                                            >
                                                                {col.name}
                                                            </Typography>
                                                        </Box>
                                                    }
                                                    secondary={
                                                        <Typography
                                                            component="span"
                                                            sx={{
                                                                fontSize: '0.75rem',
                                                                color: 'primary.main',
                                                                fontWeight: 500,
                                                                ml: 6
                                                            }}
                                                        >
                                                            {col.type}
                                                        </Typography>
                                                    }
                                                    sx={{ my: 0 }}
                                                />
                                            </ListItemButton>
                                        </ListItem>
                                    ))}
                            </List>
                        </Paper>
                    </Box>
                ) : columns.length > 0 ? (
                    <Alert severity="success" variant="outlined" sx={{ mt: 2, borderRadius: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
                            ✅ Detected {columns.length} columns
                        </Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                            All columns will be created as TEXT type. You can change column types after upload using "Edit Schema".
                        </Typography>
                    </Alert>
                ) : null}
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 2 }}>
                <Button onClick={handleClose} disabled={uploading} size="small">
                    Cancel
                </Button>
                <Button
                    onClick={handleSubmit}
                    variant="contained"
                    disabled={!selectedFile || !tableName.trim() || uploading || (showColumnSelector && selectedColumns.size === 0)}
                    size="small"
                >
                    {uploading ? 'Uploading...' : 'Upload'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
