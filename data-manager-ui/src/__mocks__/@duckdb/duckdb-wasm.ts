import { vi } from 'vitest';

const mockAsyncDuckDB = vi.fn().mockImplementation(() => ({
  instantiate: vi.fn().mockResolvedValue(undefined),
  connect: vi.fn().mockResolvedValue({
    query: vi.fn().mockResolvedValue({
      toArray: vi.fn().mockReturnValue([]),
      schema: { fields: [] },
    }),
    insertArrowFromIPCStream: vi.fn().mockResolvedValue(undefined),
    registerEmptyTableBuffer: vi.fn().mockResolvedValue(undefined),
    unregisterFileBuffer: vi.fn().mockResolvedValue(undefined),
    close: vi.fn().mockResolvedValue(undefined),
  }),
  terminate: vi.fn().mockResolvedValue(undefined),
}));

const mockConsoleLogger = vi.fn().mockImplementation(() => ({
  log: vi.fn(),
  error: vi.fn(),
}));

export const selectBundle = vi.fn().mockResolvedValue({
  mainModule: 'mock.wasm',
  mainWorker: 'mock.worker.js',
  pthreadWorker: undefined,
});

export const ConsoleLogger = mockConsoleLogger;
export const AsyncDuckDB = mockAsyncDuckDB;

export default {};

