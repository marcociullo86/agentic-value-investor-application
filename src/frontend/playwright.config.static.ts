/**
 * Playwright configuration — STATIC-EXPORT mode (TSK-269 / US-087 / ADR-026).
 *
 * Validates ClientAuthGuard flows against the Next.js static bundle served by
 * a plain HTTP file server.  NO Next.js Edge/Node middleware runs, matching
 * the production-like environment described in ADR-026.
 *
 * Prerequisites
 * -------------
 *   cd src/frontend && npm run build   # generates out/
 *
 * Run
 * ---
 *   cd src/frontend && npx playwright test --config playwright.config.static.ts
 *
 * webServer starts scripts/static-test-server.js to serve out/ on port 4000.
 * All /api/* calls are intercepted by page.route()
 * inside the spec — no real backend is required for auth-flow assertions.
 *
 * CI usage
 * --------
 *   npm run build && npx playwright test --config playwright.config.static.ts
 *
 * Design rationale (Finding 1, TSK-269-iter-1)
 * ---------------------------------------------
 * The default playwright.config.ts targets `npm run dev` where the Next.js
 * middleware is active (ADR-026 §dev-only).  This config uses a plain static
 * file server so every redirect observed from a protected route is definitively
 * produced by ClientAuthGuard running in the browser — the sole auth actor in
 * the production static-export environment.
 */

import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/auth-guard-static-export.spec.ts'],
  outputDir: 'e2e/test-results-static',

  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,

  reporter: process.env.CI
    ? [
        ['junit', { outputFile: 'e2e/test-results-static/results.xml' }],
        ['html', { outputFolder: 'e2e/playwright-report-static', open: 'never' }],
      ]
    : [['list']],

  use: {
    baseURL: process.env.E2E_STATIC_URL ?? 'http://localhost:4000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium-static',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: {
    // Custom Node.js static server (scripts/static-test-server.js) instead of
    // Python's http.server.  Python returns HTTP 501 for POST requests, which
    // interferes with the /api/auth/refresh mock if Playwright's page.route()
    // interception is delayed.  The Node server returns 204 for non-GET methods
    // (all API calls are mocked by page.route() before they reach the server).
    command: 'node scripts/static-test-server.js 4000',
    url: 'http://localhost:4000',
    reuseExistingServer: !process.env.CI,
    timeout: 15_000,
  },
});
