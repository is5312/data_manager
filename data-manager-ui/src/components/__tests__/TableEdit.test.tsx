import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TableEdit } from '../TableEdit';
import * as api from '../../services/api';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';

const theme = createTheme();

const renderWithRouter = (ui: React.ReactElement, initialEntries = ['/tables/1/edit']) => {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <ThemeProvider theme={theme}>
        <Routes>
          <Route path="/tables/:id/edit" element={ui} />
        </Routes>
      </ThemeProvider>
    </MemoryRouter>
  );
};

vi.mock('../../services/api');

const mockFetchTableById = vi.mocked(api.fetchTableById);
const mockFetchTableColumns = vi.mocked(api.fetchTableColumns);
const mockAddColumn = vi.mocked(api.addColumn);
const mockChangeColumnType = vi.mocked(api.changeColumnType);

describe('TableEdit', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchTableById.mockResolvedValue({
      id: 1,
      label: 'Test Table',
      physicalName: 'test_table',
      versionNo: 1,
    });
    mockFetchTableColumns.mockResolvedValue([
      {
        id: 1,
        tableId: 1,
        label: 'Name',
        physicalName: 'name',
        tablePhysicalName: 'test_table',
        type: 'VARCHAR',
        versionNo: 1,
      },
    ]);
  });

  it('renders all columns with metadata', async () => {
    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.getByText('Name')).toBeInTheDocument();
    });

    expect(screen.getByText(/physical: name/i)).toBeInTheDocument();
    expect(screen.getByText('VARCHAR')).toBeInTheDocument();
  });

  it('shows column type chips', async () => {
    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.getByText('VARCHAR')).toBeInTheDocument();
    });
  });

  it('opens add column dialog', async () => {
    const user = userEvent.setup();
    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    const addButton = screen.getByLabelText(/add new column/i);
    await user.click(addButton);

    expect(screen.getByText(/add new column/i)).toBeInTheDocument();
  });

  it('validates column name in add dialog', async () => {
    const user = userEvent.setup();
    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    const addButton = screen.getByLabelText(/add new column/i);
    await user.click(addButton);

    const submitButton = screen.getByRole('button', { name: /add column/i });
    await user.click(submitButton);

    expect(mockAddColumn).not.toHaveBeenCalled();
  });

  it('creates column on submit', async () => {
    const user = userEvent.setup();
    mockAddColumn.mockResolvedValue({
      id: 2,
      tableId: 1,
      label: 'New Column',
      physicalName: 'new_column',
      tablePhysicalName: 'test_table',
      type: 'INTEGER',
      versionNo: 1,
    });

    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    const addButton = screen.getByLabelText(/add new column/i);
    await user.click(addButton);

    const columnNameInput = screen.getByLabelText(/column label/i);
    await user.type(columnNameInput, 'New Column');

    const submitButton = screen.getByRole('button', { name: /add column/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockAddColumn).toHaveBeenCalledWith(1, 'New Column', 'VARCHAR');
    });
  });

  it('opens change column type dialog', async () => {
    const user = userEvent.setup();
    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.getByText('VARCHAR')).toBeInTheDocument();
    });

    const typeChip = screen.getByText('VARCHAR');
    await user.click(typeChip);

    expect(screen.getByText(/change column type/i)).toBeInTheDocument();
  });

  it('updates column type on submit', async () => {
    const user = userEvent.setup();
    mockChangeColumnType.mockResolvedValue({
      id: 1,
      tableId: 1,
      label: 'Name',
      physicalName: 'name',
      tablePhysicalName: 'test_table',
      type: 'INTEGER',
      versionNo: 1,
    });

    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.getByText('VARCHAR')).toBeInTheDocument();
    });

    const typeChip = screen.getByText('VARCHAR');
    await user.click(typeChip);

    await waitFor(() => {
      expect(screen.getByText(/change column type/i)).toBeInTheDocument();
    });

    const typeSelect = screen.getByRole('combobox', { name: /data type/i });
    await user.click(typeSelect);
    
    await waitFor(() => {
      expect(screen.getByText('INTEGER')).toBeInTheDocument();
    });
    await user.click(screen.getByText('INTEGER'));

    const submitButton = screen.getByRole('button', { name: /update type/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockChangeColumnType).toHaveBeenCalledWith(1, 1, 'INTEGER');
    });
  });

  it('displays API errors', async () => {
    mockFetchTableById.mockRejectedValue(new Error('API Error'));

    renderWithRouter(<TableEdit />);

    await waitFor(() => {
      expect(screen.getByText(/failed to load table information/i)).toBeInTheDocument();
    });
  });
});

