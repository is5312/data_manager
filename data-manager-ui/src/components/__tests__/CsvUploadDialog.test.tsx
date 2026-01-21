import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CsvUploadDialog } from '../CsvUploadDialog';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CsvUploadDialog } from '../CsvUploadDialog';

describe('CsvUploadDialog', () => {
  const mockOnUpload = vi.fn();
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('accepts CSV files', async () => {
    const user = userEvent.setup();
    const file = new File(['name,age\nJohn,30'], 'test.csv', { type: 'text/csv' });

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText('test.csv')).toBeInTheDocument();
    });
  });

  it('accepts GZIP files', async () => {
    const user = userEvent.setup();
    const file = new File([''], 'test.gz', { type: 'application/gzip' });

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText('test.gz')).toBeInTheDocument();
    });
  });

  it('rejects invalid file types', async () => {
    const user = userEvent.setup();
    const file = new File([''], 'test.txt', { type: 'text/plain' });

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText(/please select a .csv or .gz/i)).toBeInTheDocument();
    });
  });

  it('auto-fills table name from filename', async () => {
    const user = userEvent.setup();
    const file = new File(['name,age\nJohn,30'], 'my_test_file.csv', { type: 'text/csv' });

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      const tableNameInput = screen.getByLabelText(/table name/i) as HTMLInputElement;
      expect(tableNameInput.value).toBe('my_test_file');
    });
  });

  it('parses CSV header correctly', async () => {
    const user = userEvent.setup();
    const file = new File(['name,age,city\nJohn,30,NYC'], 'test.csv', { type: 'text/csv' });

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText(/detected 3 columns/i)).toBeInTheDocument();
    });
  });

  it('shows column selector for >500 columns', async () => {
    const user = userEvent.setup();
    const header = Array.from({ length: 600 }, (_, i) => `col${i}`).join(',');
    const file = new File([header], 'test.csv', { type: 'text/csv' });

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText(/too many columns detected/i)).toBeInTheDocument();
    });
  });

  it('validates table name on submit', async () => {
    const user = userEvent.setup();
    const file = new File(['name,age\nJohn,30'], 'test.csv', { type: 'text/csv' });

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    const tableNameInput = screen.getByLabelText(/table name/i);
    await user.clear(tableNameInput);

    const submitButton = screen.getByRole('button', { name: /upload/i });
    await user.click(submitButton);

    expect(mockOnUpload).not.toHaveBeenCalled();
  });

  it('calls onUpload with correct parameters', async () => {
    const user = userEvent.setup();
    const file = new File(['name,age\nJohn,30'], 'test.csv', { type: 'text/csv' });
    mockOnUpload.mockResolvedValue(undefined);

    render(
      <CsvUploadDialog
        open={true}
        onClose={mockOnClose}
        onUpload={mockOnUpload}
      />
    );

    const fileInput = screen.getByLabelText(/click to select csv/i).querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText(/detected/i)).toBeInTheDocument();
    });

    const submitButton = screen.getByRole('button', { name: /upload/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockOnUpload).toHaveBeenCalledWith(
        file,
        'test',
        undefined,
        undefined,
        expect.objectContaining({
          delimiter: ',',
          quoteChar: '"',
          escapeChar: '\\',
        })
      );
    });
  });
});

