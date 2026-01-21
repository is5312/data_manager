import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateTableDialog } from '../CreateTableDialog';

describe('CreateTableDialog', () => {
  const mockOnSubmit = vi.fn();
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('validates table name is required', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const submitButton = screen.getByRole('button', { name: /create table/i });
    await userEvent.click(submitButton);

    expect(screen.getByText(/table name is required/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('validates table name alphanumeric', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const tableNameInput = screen.getByLabelText(/table name/i);
    await userEvent.type(tableNameInput, 'invalid-name!');

    const submitButton = screen.getByRole('button', { name: /create table/i });
    await userEvent.click(submitButton);

    expect(
      screen.getByText(/must contain only alphanumeric/i)
    ).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('validates at least one column required', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const tableNameInput = screen.getByLabelText(/table name/i);
    await userEvent.type(tableNameInput, 'valid_table');

    // Remove the default column
    const removeButton = screen.getByRole('button', { name: /delete/i });
    await userEvent.click(removeButton);

    const submitButton = screen.getByRole('button', { name: /create table/i });
    await userEvent.click(submitButton);

    expect(screen.getByText(/at least one column is required/i)).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('validates column names alphanumeric', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const tableNameInput = screen.getByLabelText(/table name/i);
    await userEvent.type(tableNameInput, 'valid_table');

    const columnNameInput = screen.getByLabelText(/column 1 name/i);
    await userEvent.clear(columnNameInput);
    await userEvent.type(columnNameInput, 'invalid-column!');

    const submitButton = screen.getByRole('button', { name: /create table/i });
    await userEvent.click(submitButton);

    expect(
      screen.getByText(/must contain only alphanumeric/i)
    ).toBeInTheDocument();
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  it('adds column when add button clicked', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const addButton = screen.getByRole('button', { name: /add column/i });
    await userEvent.click(addButton);

    expect(screen.getByLabelText(/column 2 name/i)).toBeInTheDocument();
  });

  it('removes column when remove button clicked', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const addButton = screen.getByRole('button', { name: /add column/i });
    await userEvent.click(addButton);

    const removeButtons = screen.getAllByRole('button', { name: /delete/i });
    await userEvent.click(removeButtons[0]);

    expect(screen.queryByLabelText(/column 2 name/i)).not.toBeInTheDocument();
  });

  it('prevents removing last column', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const removeButton = screen.getByRole('button', { name: /delete/i });
    expect(removeButton).toBeDisabled();
  });

  it('calls onSubmit with correct data', async () => {
    render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const tableNameInput = screen.getByLabelText(/table name/i);
    await userEvent.type(tableNameInput, 'test_table');

    const columnNameInput = screen.getByLabelText(/column 1 name/i);
    await userEvent.type(columnNameInput, 'test_column');

    const submitButton = screen.getByRole('button', { name: /create table/i });
    await userEvent.click(submitButton);

    expect(mockOnSubmit).toHaveBeenCalledWith('test_table', [
      { id: '1', label: 'test_column', type: 'VARCHAR' },
    ], 'DESIGN_TIME');
  });

  it('resets form on close', async () => {
    const { rerender } = render(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const tableNameInput = screen.getByLabelText(/table name/i);
    await userEvent.type(tableNameInput, 'test_table');

    rerender(
      <CreateTableDialog
        open={false}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    rerender(
      <CreateTableDialog
        open={true}
        onClose={mockOnClose}
        onSubmit={mockOnSubmit}
      />
    );

    const tableNameInputAfter = screen.getByLabelText(/table name/i);
    expect(tableNameInputAfter).toHaveValue('');
  });
});

