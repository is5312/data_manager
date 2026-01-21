import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TableDataView } from '../TableDataView';
import * as api from '../../services/api';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';

const theme = createTheme();

const renderWithRouter = (ui: React.ReactElement, initialEntries = ['/tables/1']) => {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <ThemeProvider theme={theme}>
        <Routes>
          <Route path="/tables/:id" element={ui} />
        </Routes>
      </ThemeProvider>
    </MemoryRouter>
  );
};

vi.mock('../../services/api');
vi.mock('../../services/duckdb.service', () => ({
  queryDuckDB: vi.fn().mockResolvedValue([{ count: 0 }]),
  tableExistsInDuckDB: vi.fn().mockResolvedValue(false),
  loadTableDataIntoDuckDBArrow: vi.fn().mockResolvedValue({ rowCount: 0, columnCount: 0 }),
  dropTable: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('../../hooks/useTableData', () => ({
  useTableData: () => ({
    tableInfo: {
      id: 1,
      label: 'Test Table',
      physicalName: 'test_table',
    },
    columns: [
      {
        id: 1,
        tableId: 1,
        label: 'Name',
        physicalName: 'name',
        tablePhysicalName: 'test_table',
        type: 'VARCHAR',
      },
    ],
    loading: false,
    loadingData: false,
    loadProgress: '',
    loadProgressPercent: 0,
    error: null,
    rowCount: 0,
    dataLoaded: true,
    loadDataIntoDuckDB: vi.fn(),
    setError: vi.fn(),
  }),
}));

vi.mock('../../hooks/useTableMutations', () => ({
  useTableMutations: () => ({
    snackbar: { open: false, message: '', severity: 'success' as const },
    setSnackbar: vi.fn(),
    selectedCount: 0,
    setSelectedCount: vi.fn(),
    addDialogOpen: false,
    setAddDialogOpen: vi.fn(),
    saveDialogOpen: false,
    setSaveDialogOpen: vi.fn(),
    limitWarningDialogOpen: false,
    setLimitWarningDialogOpen: vi.fn(),
    newRowData: {},
    setNewRowData: vi.fn(),
    savingRow: false,
    savingChanges: false,
    onCellValueChanged: vi.fn(),
    handleBulkDelete: vi.fn(),
    handleSaveNewRow: vi.fn(),
    batchSaveChanges: vi.fn(),
  }),
}));

vi.mock('../../stores/tableEditStore', () => ({
  useTableEditStore: () => ({
    isRowDeleted: vi.fn().mockReturnValue(false),
    isRowInserted: vi.fn().mockReturnValue(false),
    isCellUpdated: vi.fn().mockReturnValue(false),
    hasChanges: vi.fn().mockReturnValue(false),
    getPendingChangesCount: vi.fn().mockReturnValue(0),
    clearChanges: vi.fn(),
  }),
}));

describe('TableDataView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('displays table name correctly', async () => {
    renderWithRouter(<TableDataView />);

    await waitFor(() => {
      expect(screen.getByText(/test table/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/test_table/i)).toBeInTheDocument();
  });

  it('shows row count when loaded', async () => {
    renderWithRouter(<TableDataView />);

    await waitFor(() => {
      expect(screen.getByText(/0 rows/i)).toBeInTheDocument();
    });
  });

  it('navigates back when back button clicked', async () => {
    renderWithRouter(<TableDataView />);

    const backButton = screen.getByRole('button', { name: /back/i });
    await userEvent.click(backButton);

    // Note: This test would need proper mocking setup to verify navigation
  });

  it('shows save button when there are changes', async () => {
    const { useTableEditStore } = await import('../../stores/tableEditStore');
    vi.mocked(useTableEditStore).mockReturnValue({
      isRowDeleted: vi.fn().mockReturnValue(false),
      isRowInserted: vi.fn().mockReturnValue(false),
      isCellUpdated: vi.fn().mockReturnValue(false),
      hasChanges: vi.fn().mockReturnValue(true),
      getPendingChangesCount: vi.fn().mockReturnValue(5),
      clearChanges: vi.fn(),
    });

    renderWithRouter(<TableDataView />);

    await waitFor(() => {
      const saveButton = screen.getByRole('button', { name: /save/i });
      expect(saveButton).toBeInTheDocument();
    });
  });
});

