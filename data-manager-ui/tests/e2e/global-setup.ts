import { chromium, FullConfig } from '@playwright/test';

async function globalSetup(config: FullConfig) {
  // Optional: Add any global setup here
  // For example, clearing test data in the backend
  console.log('Global setup: Preparing test environment...');
}

export default globalSetup;

