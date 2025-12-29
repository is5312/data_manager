import React, { useEffect, useState } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    Typography,
    Stack,
    Chip,
    LinearProgress,
    Alert
} from '@mui/material';
import { BatchStatus, fetchBatchStatus } from '../services/api';

interface Props {
    open: boolean;
    batchId: number | null;
    onClose: () => void;
}

export const BatchUploadProgressDialog: React.FC<Props> = ({ open, batchId, onClose }) => {
    const [status, setStatus] = useState<BatchStatus | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!open || !batchId) return;

        let cancelled = false;
        let timer: number | undefined;

        const load = async () => {
            try {
                setLoading(true);
                const s = await fetchBatchStatus(batchId);
                if (!cancelled) {
                    setStatus(s);
                    setError(null);
                }
            } catch (e: any) {
                if (!cancelled) setError(e?.message || 'Failed to load batch status');
            } finally {
                if (!cancelled) setLoading(false);
            }
        };

        load();
        timer = window.setInterval(load, 2000);

        return () => {
            cancelled = true;
            if (timer) window.clearInterval(timer);
        };
    }, [open, batchId]);

    const terminal = status?.status === 'COMPLETED' || status?.status === 'FAILED' || status?.status === 'STOPPED';

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle sx={{
                borderBottom: '1px solid #E0E0E0',
                backgroundColor: 'background.paper',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
                color: 'secondary.main',
                fontWeight: 700
            }}>
                Batch Upload Progress
            </DialogTitle>
            <DialogContent sx={{ mt: 2 }}>
                {!batchId && (
                    <Alert severity="info" variant="outlined" sx={{ borderRadius: 0 }}>
                        No batch upload in progress.
                    </Alert>
                )}

                {batchId && (
                    <Stack spacing={2}>
                        {error && (
                            <Alert severity="error" variant="outlined" sx={{ borderRadius: 0 }}>
                                {error}
                            </Alert>
                        )}

                        <Stack direction="row" spacing={1} alignItems="center">
                            <Typography variant="body2" sx={{ fontFamily: 'Roboto Mono' }}>
                                Batch ID: {batchId}
                            </Typography>
                            {status?.status && (
                                <Chip label={status.status} size="small" variant="outlined" sx={{ borderRadius: 0 }} />
                            )}
                        </Stack>

                        {!terminal && <LinearProgress />}
                        {terminal && <Alert severity={status?.status === 'COMPLETED' ? 'success' : 'warning'} variant="outlined" sx={{ borderRadius: 0 }}>
                            {status?.status === 'COMPLETED' ? 'Upload completed.' : 'Upload finished with issues.'}
                        </Alert>}

                        <Stack direction="row" spacing={2}>
                            <Typography variant="body2">Read: {status?.readCount ?? '-'}</Typography>
                            <Typography variant="body2">Written: {status?.writeCount ?? '-'}</Typography>
                            <Typography variant="body2">Skipped: {status?.skipCount ?? '-'}</Typography>
                        </Stack>

                        {status?.exitDescription && status.status !== 'COMPLETED' && (
                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                                {status.exitDescription}
                            </Typography>
                        )}

                        {status?.failureExceptions && status.failureExceptions.length > 0 && (
                            <Alert severity="error" variant="outlined" sx={{ borderRadius: 0 }}>
                                <Typography variant="body2" sx={{ fontWeight: 700, mb: 1 }}>
                                    Failure details
                                </Typography>
                                {status.failureExceptions.slice(0, 5).map((msg, idx) => (
                                    <Typography key={idx} variant="caption" sx={{ display: 'block', fontFamily: 'Roboto Mono' }}>
                                        {msg}
                                    </Typography>
                                ))}
                            </Alert>
                        )}

                        {loading && (
                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                                Refreshing…
                            </Typography>
                        )}
                    </Stack>
                )}
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 2 }}>
                <Button onClick={onClose} size="small">Close</Button>
            </DialogActions>
        </Dialog>
    );
};


