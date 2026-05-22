/**
 * Playwright configuration — REAL-BE mode.
 *
 * Scope:
 *  - TSK-036 (Track B): auth + watchlist E2E flow against a real backend +
 *    postgres + frontend dev server. JWT roundtrip and DB persistence cannot
 *    be covered credibly by `page.route()` mocks, so this job stands apart
 *    from the mocked config (`playwright.config.ts`).
 *  - The CI orchestrator (`.github/workflows/ci.yml` job `fe-e2e-realbe`)
 *    boots BE bootJar + postgres service + `npm run dev` and waits on
 *    `/actuator/health` + the FE URL before invoking Playwright. This config
 *    intentionally has NO `webServer:` block — the orchestration lives in CI.
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §Testing strategy §E2E Playwright.
 */

import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

export default defineConfig({
  testDir: './e2e',
  // Only run the real-BE specs here; mocked specs are run via the default config.
  testMatch: ['**/auth-watchlist.spec.ts'],
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
