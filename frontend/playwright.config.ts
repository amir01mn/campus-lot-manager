import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke tests against a live stack: the backend must already be running with the `demo` profile
 * (`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`). The Vite dev server is started
 * here unless one is already listening. Override the UI address with E2E_BASE_URL.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';

export default defineConfig({
  testDir: './e2e',
  testIgnore: ['**/screenshots.spec.ts'],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    timezoneId: 'America/Toronto',
    locale: 'en-CA',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: 'npm run dev', url: baseURL, reuseExistingServer: true, timeout: 60_000 },
});
