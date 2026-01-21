# Test Suite Documentation

This directory contains the comprehensive test suite for the data-manager-ui application.

## Test Structure

```
tests/
├── integration/          # Integration tests
├── e2e/                 # End-to-end tests (Playwright)
└── fixtures/            # Test data and fixtures
```

## Running Tests

### Unit Tests (Vitest)

```bash
# Run all unit tests
npm test

# Run tests in watch mode
npm test -- --watch

# Run tests with UI
npm run test:ui

# Run tests with coverage
npm run test:coverage
```

### E2E Tests (Playwright)

```bash
# Run all E2E tests
npm run test:e2e

# Run E2E tests with UI
npm run test:e2e:ui

# Run specific test file
npx playwright test tests/e2e/create-table.spec.ts
```

## Test Coverage

The test suite aims for >80% code coverage. Coverage reports are generated in the `coverage/` directory.

## Writing Tests

### Unit Tests

Unit tests are located alongside the source files:
- Component tests: `src/components/__tests__/`
- Hook tests: `src/hooks/__tests__/`
- Store tests: `src/stores/__tests__/`
- Service tests: `src/services/__tests__/`

### Integration Tests

Integration tests verify component interactions and are located in `tests/integration/`.

### E2E Tests

E2E tests use Playwright and are located in `tests/e2e/`. They test complete user flows.

## Test Utilities

- `src/__tests__/test-utils.tsx` - Custom render function with providers
- `src/__tests__/setup.ts` - Test setup and global mocks
- `src/__mocks__/handlers.ts` - MSW request handlers for API mocking

## Mocking

The test suite uses MSW (Mock Service Worker) to mock API requests. Handlers are defined in `src/__mocks__/handlers.ts`.

## Test Data

Sample test data is available in `tests/fixtures/sample-data.ts`.

