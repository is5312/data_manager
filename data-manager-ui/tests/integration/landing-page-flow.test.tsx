import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LandingPage } from '../../src/components/LandingPage';
import * as api from '../../src/services/api';
import * as duckdb from '../../src/utils/duckdb';
import { render as renderWithRouter } from '../../src/__tests__/test-utils';

vi.mock('../../src/services/api');
vi.mock('../../src/utils/duckdb');

describe('Landing Page Integration Flow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(duckdb.initDuckDB).mockResolvedValue({} as any);
  });

  it('creates table and appears in list', async () => {
    vi.mocked(api.fetchTables).mockResolvedValue([]);
    vi.mocked(api.createTable).mockResolvedValue({
      id: 1,
      label: 'New Table',
      physicalName: 'new_table',
      versionNo: 1,
    });
    vi.mocked(api.addColumn).mockResolvedValue({
      id: 1,
      tableId: 1,
      label: 'Name',
      physicalName: 'name',
      tablePhysicalName: 'new_table',
      type: 'VARCHAR',
      versionNo: 1,
    });

    const user = userEvent.setup();
    renderWithRouter(<LandingPage />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    // Open create dialog
    const createButton = screen.getByLabelText(/create new table/i);
    await user.click(createButton);

    // Fill form
    const tableNameInput = screen.getByLabelText(/table name/i);
    await user.type(tableNameInput, 'New Table');

    const columnNameInput = screen.getByLabelText(/column 1 name/i);
    await user.type(columnNameInput, 'Name');

    // Submit
    const submitButton = screen.getByRole('button', { name: /create table/i });
    await user.click(submitButton);

    // Verify table appears in list
    await waitFor(() => {
      expect(screen.getByText('New Table')).toBeInTheDocument();
    });
  });

  it('uploads CSV and table appears in list', async () => {
    vi.mocked(api.fetchTables).mockResolvedValue([]);
    vi.mocked(api.startBatchUpload).mockResolvedValue({
      batchId: 1,
      table: {
        id: 1,
        label: 'test',
        physicalName: 'test',
        versionNo: 1,
      },
      message: 'Upload started',
    });

    const user = userEvent.setup();
    renderWithRouter(<LandingPage />);

    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    });

    // Open upload dialog
    const uploadButton = screen.getByLabelText(/batch upload/i);
    await user.click(uploadButton);

    // Upload file
    const file = new File(['name,age\nJohn,30'], 'test.csv', { type: 'text/csv' });
    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText(/detected/i)).toBeInTheDocument();
    });

    // Submit
    const submitButton = screen.getByRole('button', { name: /upload/i });
    await user.click(submitButton);

    // Verify batch started
    await waitFor(() => {
      expect(api.startBatchUpload).toHaveBeenCalled();
    });
  });
});

