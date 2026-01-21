import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { BatchUploadProgressDialog } from '../BatchUploadProgressDialog';
import * as api from '../../services/api';

vi.mock('../../services/api');

// Mock timers for polling tests
beforeAll(() => {
  vi.useFakeTimers();
});

afterAll(() => {
  vi.useRealTimers();
});

const mockFetchBatchStatus = vi.mocked(api.fetchBatchStatus);

describe('BatchUploadProgressDialog', () => {
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows batch ID', async () => {
    mockFetchBatchStatus.mockResolvedValue({
      batchId: 1,
      status: 'COMPLETED',
      exitCode: 'COMPLETED',
      exitDescription: 'Success',
      readCount: 100,
      writeCount: 100,
      skipCount: 0,
    });

    render(
      <BatchUploadProgressDialog
        open={true}
        batchId={1}
        onClose={mockOnClose}
      />
    );

    // Run pending timers to allow async operations
    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(screen.getByText(/batch id: 1/i)).toBeInTheDocument();
    });
  });

  it('shows current status', async () => {
    mockFetchBatchStatus.mockResolvedValue({
      batchId: 1,
      status: 'RUNNING',
      readCount: 50,
      writeCount: 50,
      skipCount: 0,
    });

    render(
      <BatchUploadProgressDialog
        open={true}
        batchId={1}
        onClose={mockOnClose}
      />
    );

    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(screen.getByText(/running/i)).toBeInTheDocument();
    });
  });

  it('shows progress metrics', async () => {
    mockFetchBatchStatus.mockResolvedValue({
      batchId: 1,
      status: 'COMPLETED',
      exitCode: 'COMPLETED',
      readCount: 100,
      writeCount: 100,
      skipCount: 5,
    });

    render(
      <BatchUploadProgressDialog
        open={true}
        batchId={1}
        onClose={mockOnClose}
      />
    );

    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(screen.getByText(/100/i)).toBeInTheDocument(); // read count
      expect(screen.getByText(/5/i)).toBeInTheDocument(); // skip count
    });
  });

  it('displays error messages if failed', async () => {
    mockFetchBatchStatus.mockResolvedValue({
      batchId: 1,
      status: 'FAILED',
      exitCode: 'FAILED',
      exitDescription: 'Error occurred',
      failureExceptions: ['Error 1', 'Error 2'],
    });

    render(
      <BatchUploadProgressDialog
        open={true}
        batchId={1}
        onClose={mockOnClose}
      />
    );

    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(screen.getByText(/failed/i)).toBeInTheDocument();
      expect(screen.getByText(/error occurred/i)).toBeInTheDocument();
    });
  });

  it('polls status endpoint', async () => {
    mockFetchBatchStatus.mockResolvedValue({
      batchId: 1,
      status: 'RUNNING',
      readCount: 50,
      writeCount: 50,
      skipCount: 0,
    });

    render(
      <BatchUploadProgressDialog
        open={true}
        batchId={1}
        onClose={mockOnClose}
      />
    );

    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(mockFetchBatchStatus).toHaveBeenCalled();
    });

    // Advance timer to trigger next poll
    vi.advanceTimersByTime(2000);
    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(mockFetchBatchStatus).toHaveBeenCalledTimes(2);
    });
  });

  it('stops polling when complete', async () => {
    mockFetchBatchStatus.mockResolvedValue({
      batchId: 1,
      status: 'COMPLETED',
      exitCode: 'COMPLETED',
      readCount: 100,
      writeCount: 100,
      skipCount: 0,
    });

    render(
      <BatchUploadProgressDialog
        open={true}
        batchId={1}
        onClose={mockOnClose}
      />
    );

    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(mockFetchBatchStatus).toHaveBeenCalled();
    });

    const initialCallCount = mockFetchBatchStatus.mock.calls.length;

    // Advance timer - should not poll again since status is COMPLETED
    vi.advanceTimersByTime(5000);
    await vi.runAllTimersAsync();

    await waitFor(() => {
      expect(mockFetchBatchStatus.mock.calls.length).toBe(initialCallCount);
    });
  });
});

