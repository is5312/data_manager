import React, { useEffect, useState } from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    Typography,
    Box,
    CircularProgress,
    Alert,
    Chip,
    Divider
} from '@mui/material';
import {
    CheckCircle as SuccessIcon,
    Error as ErrorIcon,
    HourglassEmpty as PendingIcon,
    PlayArrow as ProcessingIcon
} from '@mui/icons-material';
import { fetchJobStatus, JobDetails } from '../services/api';

interface MigrationStatusDialogProps {
    open: boolean;
    onClose: () => void;
    jobId: string;
    tableLabel: string;
}

export const MigrationStatusDialog: React.FC<MigrationStatusDialogProps> = ({
    open,
    onClose,
    jobId,
    tableLabel
}) => {
    const [jobDetails, setJobDetails] = useState<JobDetails | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!open || !jobId) return;

        let intervalId: NodeJS.Timeout;

        const loadJobStatus = async () => {
            try {
                const details = await fetchJobStatus(jobId);
                setJobDetails(details);
                setLoading(false);
                setError(null);

                // Stop polling if job is in terminal state
                if (details.status === 'SUCCEEDED' || details.status === 'FAILED') {
                    if (intervalId) {
                        clearInterval(intervalId);
                    }
                }
            } catch (err) {
                setError(err instanceof Error ? err.message : 'Failed to load job status');
                setLoading(false);
            }
        };

        // Initial load
        loadJobStatus();

        // Poll every 2 seconds while dialog is open and job is running
        intervalId = setInterval(loadJobStatus, 2000);

        return () => {
            if (intervalId) {
                clearInterval(intervalId);
            }
        };
    }, [open, jobId]);

    const getStatusIcon = (status: string) => {
        switch (status) {
            case 'SUCCEEDED':
                return <SuccessIcon color="success" />;
            case 'FAILED':
                return <ErrorIcon color="error" />;
            case 'PROCESSING':
                return <ProcessingIcon color="info" />;
            case 'ENQUEUED':
                return <PendingIcon color="warning" />;
            default:
                return <PendingIcon />;
        }
    };

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

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>
                Migration Status - {tableLabel}
            </DialogTitle>
            <DialogContent>
                {loading && (
                    <Box display="flex" justifyContent="center" alignItems="center" py={4}>
                        <CircularProgress />
                    </Box>
                )}

                {error && (
                    <Alert severity="error" sx={{ mb: 2 }}>
                        {error}
                    </Alert>
                )}

                {jobDetails && !loading && (
                    <Box>
                        <Box display="flex" alignItems="center" gap={2} mb={3}>
                            {getStatusIcon(jobDetails.status)}
                            <Chip 
                                label={jobDetails.status} 
                                color={getStatusColor(jobDetails.status)}
                                size="medium"
                            />
                        </Box>

                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Job ID: {jobDetails.jobId}
                        </Typography>

                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Job Name: {jobDetails.jobName}
                        </Typography>

                        <Divider sx={{ my: 2 }} />

                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Created: {new Date(jobDetails.createdAt).toLocaleString()}
                        </Typography>

                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Updated: {new Date(jobDetails.updatedAt).toLocaleString()}
                        </Typography>

                        {jobDetails.status === 'SUCCEEDED' && jobDetails.result && (
                            <>
                                <Divider sx={{ my: 2 }} />
                                <Alert severity="success" sx={{ mt: 2 }}>
                                    <Typography variant="body2">
                                        {jobDetails.result.message}
                                    </Typography>
                                    {jobDetails.result.shadowTableName && (
                                        <Typography variant="body2" sx={{ mt: 1 }}>
                                            Shadow Table: <strong>{jobDetails.result.shadowTableName}</strong>
                                        </Typography>
                                    )}
                                    {jobDetails.result.targetSchema && (
                                        <Typography variant="body2">
                                            Target Schema: <strong>{jobDetails.result.targetSchema}</strong>
                                        </Typography>
                                    )}
                                </Alert>
                            </>
                        )}

                        {jobDetails.status === 'FAILED' && jobDetails.failureReason && (
                            <>
                                <Divider sx={{ my: 2 }} />
                                <Alert severity="error" sx={{ mt: 2 }}>
                                    <Typography variant="body2">
                                        <strong>Error:</strong> {jobDetails.failureReason}
                                    </Typography>
                                </Alert>
                            </>
                        )}

                        {(jobDetails.status === 'ENQUEUED' || jobDetails.status === 'PROCESSING') && (
                            <Box display="flex" alignItems="center" gap={1} mt={2}>
                                <CircularProgress size={20} />
                                <Typography variant="body2" color="text.secondary">
                                    {jobDetails.status === 'ENQUEUED' 
                                        ? 'Migration job is queued and will start soon...'
                                        : 'Migration is in progress...'}
                                </Typography>
                            </Box>
                        )}
                    </Box>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} variant="contained">
                    Close
                </Button>
            </DialogActions>
        </Dialog>
    );
};
