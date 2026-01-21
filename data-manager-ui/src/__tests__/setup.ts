import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, vi, beforeAll, afterAll } from 'vitest';
import { setupServer } from 'msw/node';
import { handlers } from '../__mocks__/handlers';

// Mock DuckDB WASM globally for all tests
vi.mock('@duckdb/duckdb-wasm', () => {
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

  return {
    selectBundle: vi.fn().mockResolvedValue({
      mainModule: 'mock.wasm',
      mainWorker: 'mock.worker.js',
      pthreadWorker: undefined,
    }),
    ConsoleLogger: mockConsoleLogger,
    AsyncDuckDB: mockAsyncDuckDB,
  };
}, { virtual: false });

// Mock WASM URL imports
vi.mock('@duckdb/duckdb-wasm/dist/duckdb-mvp.wasm?url', () => ({
  default: 'mock-mvp.wasm',
}));

vi.mock('@duckdb/duckdb-wasm/dist/duckdb-eh.wasm?url', () => ({
  default: 'mock-eh.wasm',
}));

vi.mock('@duckdb/duckdb-wasm/dist/duckdb-browser-mvp.worker.js?url', () => ({
  default: 'mock-mvp.worker.js',
}));

vi.mock('@duckdb/duckdb-wasm/dist/duckdb-browser-eh.worker.js?url', () => ({
  default: 'mock-eh.worker.js',
}));

// Mock Worker globally
global.Worker = vi.fn().mockImplementation(() => ({
  postMessage: vi.fn(),
  terminate: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
})) as any;

// Setup MSW
export const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => server.close());

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock ResizeObserver
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// Mock IntersectionObserver
global.IntersectionObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

