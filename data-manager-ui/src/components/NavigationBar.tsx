import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
    AppBar,
    Toolbar,
    Typography,
    IconButton,
    Box,
    Tooltip,
} from '@mui/material';
import {
    Menu as MenuIcon,
    Dashboard as DashboardIcon,
    Settings as SettingsIcon,
    Help as HelpIcon,
    Work as JobsIcon,
    Speed as MonitorIcon,
} from '@mui/icons-material';

interface NavigationBarProps {
    isDuckDBReady?: boolean;
}

export const NavigationBar: React.FC<NavigationBarProps> = ({ isDuckDBReady }) => {
    const navigate = useNavigate();

    return (
        <AppBar position="static" elevation={0} sx={{ backgroundColor: '#1a1a1a', borderBottom: '1px solid #333' }}>
            <Toolbar>
                <IconButton
                    size="large"
                    edge="start"
                    color="inherit"
                    aria-label="menu"
                    sx={{ mr: 2 }}
                >
                    <MenuIcon />
                </IconButton>

                <DashboardIcon sx={{ mr: 1 }} />
                <Box sx={{ display: 'flex', alignItems: 'center', flexGrow: 1, cursor: 'pointer' }} onClick={() => navigate('/')}>
                    <Typography
                        variant="h6"
                        component="div"
                        sx={{
                            color: '#ffffff',
                            fontWeight: 700,
                            letterSpacing: '0.05em'
                        }}
                    >
                        Data Manager
                    </Typography>

                    {isDuckDBReady !== undefined && (
                        <Box
                            sx={{
                                width: 10,
                                height: 10,
                                borderRadius: '50%',
                                bgcolor: isDuckDBReady ? '#2E7D32' : '#ED6C02',
                                boxShadow: isDuckDBReady ? '0 0 8px rgba(46, 125, 50, 0.6)' : '0 0 8px rgba(237, 108, 2, 0.6)',
                                ml: 1.5
                            }}
                        />
                    )}
                </Box>

                <Box sx={{ display: 'flex', gap: 1 }}>
                    <Tooltip title="Migration Jobs">
                        <IconButton color="inherit" onClick={() => navigate('/jobs')}>
                            <JobsIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="JobRunr Dashboard">
                        <IconButton
                            color="inherit"
                            onClick={() => window.open('http://localhost:8000/dashboard', '_blank')}
                        >
                            <MonitorIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Settings">
                        <IconButton color="inherit">
                            <SettingsIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Help">
                        <IconButton color="inherit">
                            <HelpIcon />
                        </IconButton>
                    </Tooltip>
                </Box>
            </Toolbar>
        </AppBar >
    );
};
