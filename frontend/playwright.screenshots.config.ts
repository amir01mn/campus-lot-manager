import { defineConfig, devices } from '@playwright/test';
import baseConfig from './playwright.config';

/**
 * `npm run screenshots`: captures the README screenshots into ../docs/screenshots against the live demo stack.
 * The data comes from the backend's `demo` profile seeders; the spec only adds live sensor readings and flags two spots
 * (see e2e/support/populateDemo.ts). Use a fresh demo database for the intended picture.
 */
export default defineConfig({
  ...baseConfig,
  testIgnore: [],
  testMatch: ['**/screenshots.spec.ts'],
  timeout: 300_000,
  reporter: [['list']],
  use: { ...baseConfig.use, trace: 'off', screenshot: 'off', actionTimeout: 15_000 },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
