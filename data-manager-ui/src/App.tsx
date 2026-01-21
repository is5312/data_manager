import { Box } from '@mui/material';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LandingPage } from './components/LandingPage';
import { TableDataView } from './components/TableDataView';
import { TableEdit } from './components/TableEdit';
import { JobsListPage } from './components/JobsListPage';

function App() {
    return (
        <BrowserRouter>
            <Box>
                <Routes>
                    <Route path="/" element={<LandingPage />} />
                    <Route path="/tables/:id" element={<TableDataView />} />
                    <Route path="/tables/:id/edit" element={<TableEdit />} />
                    <Route path="/jobs" element={<JobsListPage />} />
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </Box>
        </BrowserRouter>
    );
}

export default App;
