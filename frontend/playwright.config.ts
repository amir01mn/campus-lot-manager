import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke tests against a live stack with the backend's `demo` profile. Either run the Compose stack
 * (`docker compose up -d --build --wait`, then `E2E_BASE_URL=http://localhost:3000 npm run e2e`), or run the
 * backend from source (`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`) and let this config
 * start (or reuse) the Vite dev server. SENSOR_API_KEY overrides the dev sensor key used by the screenshot spec.
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
