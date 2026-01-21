import { FullConfig } from '@playwright/test';

async function globalTeardown(config: FullConfig) {
  // Optional: Add any global teardown here
  // For example, cleaning up test data in the backend
  console.log('Global teardown: Cleaning up test environment...');
}

export default globalTeardown;

