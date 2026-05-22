import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config (TSK-036). E2E suite for the auth + watchlist flow
 * (Track B). Track A's analysis-dashboard E2E (TSK-022) will plug into the
 * same project once it lands.
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §Testing strategy §E2E Playwright.
 */
const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]]
    : [['list']],
  outputDir: 'test-results',
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: process.env.CI ? 'retain-on-failure' : 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
