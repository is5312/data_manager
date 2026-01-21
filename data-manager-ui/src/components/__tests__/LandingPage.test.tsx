import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { render } from '../../__tests__/test-utils';
import userEvent from '@testing-library/user-event';
import { LandingPage } from '../LandingPage';
import * as api from '../../services/api';
import * as duckdb from '../../utils/duckdb';

// Mock dependencies
vi.mock('../../services/api');
// DuckDB is already mocked globally in setup.ts, but we can override if needed
vi.mock('../../utils/duckdb', () => ({
  initDuckDB: vi.fn().mockResolvedValue({
    connect: vi.fn().mockResolvedValue({
      query: vi.fn().mockResolvedValue({
        toArray: vi.fn().mockReturnValue([]),
      }),
    }),
  }),
}));

const mockFetchTables = vi.mocked(api.fetchTables);
const mockInitDuckDB = vi.mocked(duckdb.initDuckDB);

describe('LandingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockInitDuckDB.mockResolvedValue({} as any);
  });

  it('renders table list with correct columns', async () => {
    mockFetchTables.mockResolvedValue([
      {
        id: 1,
        label: 'Test Table',
        physicalName: 'test_table',
        description: 'Test description',
        versionNo: 1,
        createdAt: '2024-01-01T00:00:00Z',
        createdBy: 'test_user',
        updatedAt: '2024-01-01T00:00:00Z',
        updatedBy: 'test_user',
      },
    ]);

    render(<LandingPage />);

    await waitFor(() => {
      expect(screen.getByText('Test Table')).toBeInTheDocument();
    });

    expect(screen.getByText('test_table')).toBeInTheDocument();
    expect(screen.getByText('Test description')).toBeInTheDocument();
  });

  it('displays loading state correctly', () => {
    mockFetchTables.mockImplementation(() => new Promise(() => {})); // Never resolves

    render(<LandingPage />);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows error state when API fails', async () => {
    mockFetchTables.mockRejectedValue(new Error('API Error'));

    render(<LandingPage />);

    await waitFor(() => {
      expect(screen.getByText(/Failed to fetch tables/i)).toBeInTheDocument();
    });
  });

  it('renders empty state when no tables exist', async () => {
    mockFetchTables.mockResolvedValue([]);

    render(<LandingPage />);

    await waitFor(() => {
      const grid = screen.getByRole('grid');
      expect(grid).toBeInTheDocument();
    });
  });

  it('opens create table dialog', async () => {
    mockFetchTables.mockResolvedValue([]);
    const user = userEvent.setup();

    render(<LandingPage />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    const createButton = screen.getByLabelText(/create new table/i);
    await user.click(createButton);

    expect(screen.getByText(/create new table/i)).toBeInTheDocument();
  });

  it('opens CSV upload dialog', async () => {
    mockFetchTables.mockResolvedValue([]);
    const user = userEvent.setup();

    render(<LandingPage />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    const uploadButton = screen.getByLabelText(/batch upload/i);
    await user.click(uploadButton);

    expect(screen.getByText(/upload csv file/i)).toBeInTheDocument();
  });

  it('shows green indicator when DuckDB ready', async () => {
    mockFetchTables.mockResolvedValue([]);
    mockInitDuckDB.mockResolvedValue({} as any);

    render(<LandingPage />);

    await waitFor(() => {
      const indicator = screen.getByRole('status', { hidden: true });
      expect(indicator).toHaveStyle({ backgroundColor: '#2E7D32' });
    });
  });

  it('shows orange indicator when DuckDB not ready', () => {
    mockFetchTables.mockResolvedValue([]);
    mockInitDuckDB.mockRejectedValue(new Error('DuckDB init failed'));

    render(<LandingPage />);

    const indicator = screen.getByRole('status', { hidden: true });
    expect(indicator).toHaveStyle({ backgroundColor: '#ED6C02' });
  });

  it('refreshes data when refresh button clicked', async () => {
    mockFetchTables.mockResolvedValue([]);
    const user = userEvent.setup();

    render(<LandingPage />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    const refreshButton = screen.getByLabelText(/refresh table list/i);
    await user.click(refreshButton);

    expect(mockFetchTables).toHaveBeenCalledTimes(2);
  });
});

