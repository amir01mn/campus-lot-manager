import { defineConfig, devices } from '@playwright/test';
import baseConfig from './playwright.config';

/**
 * `npm run screenshots`: captures the README screenshots into ../docs/screenshots against the live demo stack.
 * It first populates the demo through the public API (extra drivers, bookings, sensor readings).
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
