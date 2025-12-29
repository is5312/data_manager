import { createTheme } from '@mui/material/styles';

// Light Bloomberg-inspired financial theme
export const theme = createTheme({
    palette: {
        mode: 'light',
        primary: {
            main: '#0066CC', // Professional Blue
            light: '#3385D6',
            dark: '#004C99',
            contrastText: '#FFFFFF',
        },
        secondary: {
            main: '#0066CC', // Professional Blue
            light: '#3385D6',
            dark: '#004C99',
            contrastText: '#FFFFFF',
        },
        background: {
            default: '#FFFFFF',
            paper: '#FAFAFA',
        },
        text: {
            primary: '#1A1A1A',
            secondary: '#666666',
        },
        divider: '#E0E0E0',
        error: {
            main: '#D32F2F',
        },
        success: {
            main: '#2E7D32',
        },
        warning: {
            main: '#ED6C02',
        },
        info: {
            main: '#0288D1',
        },
    },
    typography: {
        fontFamily: '"Roboto Mono", "Inter", "Roboto", monospace',
        h1: {
            fontSize: '1.5rem',
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
        },
        h2: {
            fontSize: '1.25rem',
            fontWeight: 600,
            letterSpacing: '0.02em',
        },
        h5: {
            fontWeight: 600,
            fontSize: '0.875rem',
            letterSpacing: '0.01em',
        },
        h6: {
            fontWeight: 600,
            textTransform: 'uppercase',
            fontSize: '0.75rem',
            letterSpacing: '0.05em',
        },
        subtitle1: {
            fontSize: '0.8rem',
            color: '#666666',
        },
        body1: {
            fontSize: '0.8125rem',
        },
        body2: {
            fontSize: '0.75rem',
            color: '#808080',
        },
        button: {
            textTransform: 'uppercase',
            fontWeight: 600,
            letterSpacing: '0.05em',
            fontSize: '0.75rem',
        },
    },
    shape: {
        borderRadius: 0,
    },
    components: {
        MuiCssBaseline: {
            styleOverrides: {
                body: {
                    scrollbarColor: "#CCC #F8F8F8",
                    "&::-webkit-scrollbar, & *::-webkit-scrollbar": {
                        backgroundColor: "#F8F8F8",
                        width: "6px",
                    },
                    "&::-webkit-scrollbar-thumb, & *::-webkit-scrollbar-thumb": {
                        backgroundColor: "#CCC",
                        minHeight: 24,
                    },
                    "&::-webkit-scrollbar-thumb:focus, & *::-webkit-scrollbar-thumb:focus": {
                        backgroundColor: "#999",
                    },
                },
            },
        },
        MuiAppBar: {
            styleOverrides: {
                root: {
                    backgroundColor: '#FFFFFF',
                    borderBottom: '1px solid #E0E0E0',
                    boxShadow: 'none',
                },
            },
        },
        MuiCard: {
            styleOverrides: {
                root: {
                    border: '1px solid #E0E0E0',
                    boxShadow: 'none',
                },
            },
        },
        MuiButton: {
            styleOverrides: {
                root: {
                    borderRadius: 0,
                    padding: '4px 16px',
                },
                contained: {
                    boxShadow: 'none',
                    '&:hover': {
                        boxShadow: 'none',
                    },
                },
                outlined: {
                    borderColor: '#E0E0E0',
                    '&:hover': {
                        borderColor: '#FF6200',
                        backgroundColor: 'rgba(255, 98, 0, 0.04)',
                    },
                },
            },
        },
        MuiChip: {
            styleOverrides: {
                root: {
                    borderRadius: 0,
                    fontWeight: 600,
                    height: '18px',
                    fontSize: '0.7rem',
                },
                outlined: {
                    borderColor: '#E0E0E0',
                },
            },
        },
        MuiPaper: {
            styleOverrides: {
                root: {
                    backgroundImage: 'none',
                },
            },
        },
        MuiTableCell: {
            styleOverrides: {
                root: {
                    borderBottom: '1px solid #E0E0E0',
                    padding: '6px 12px',
                    fontSize: '0.75rem',
                },
                head: {
                    fontWeight: 700,
                    backgroundColor: '#FAFAFA',
                    color: '#666666',
                    textTransform: 'uppercase',
                    fontSize: '0.7rem',
                },
            },
        },
    },
});
