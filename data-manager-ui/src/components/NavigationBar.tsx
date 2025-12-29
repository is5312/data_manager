import React from 'react';
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
} from '@mui/icons-material';

export const NavigationBar: React.FC = () => {
    return (
        <AppBar position="static" elevation={0}>
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
                <Typography
                    variant="h6"
                    component="div"
                    sx={{
                        flexGrow: 1,
                        fontWeight: 600,
                        background: 'linear-gradient(135deg, #38bdf8 0%, #818cf8 100%)',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                    }}
                >
                    Data Manager
                </Typography>

                <Box sx={{ display: 'flex', gap: 1 }}>
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
        </AppBar>
    );
};
