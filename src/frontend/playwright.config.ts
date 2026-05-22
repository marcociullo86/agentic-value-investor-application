/**
 * Playwright configuration — TSK-022 (E2E US-001 → US-014).
 *
 * Strategy:
 *  - Single browser: Chromium only (CI speed; Firefox/WebKit follow-up gap).
 *  - webServer: riusa il dev server Next.js (porta 3000); in CI il BE NON viene
 *    avviato perché tutti e 4 gli scenari usano `page.route()` per mockare
 *    interamente le chiamate API. Zero dipendenza BE reale in CI.
 *  - Screenshot on failure: `only-on-failure` + upload artifact nel job CI.
 *  - Trace on first retry: aiuta il debugging CI senza appesantire ogni run.
 *
 * Riferimento: design_&_architecture/components/frontend-components.md
 *   §Testing strategy §E2E Playwright.
 */

import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  outputDir: 'e2e/test-results',

  /* Esegui i test in parallelo (workers default = metà CPU). */
  fullyParallel: true,

  /* Fallisci la CI se sono stati lasciati test.only per errore. */
  forbidOnly: Boolean(process.env.CI),

  /* Nessun retry in locale; 1 retry in CI per flakiness transitoria di rete. */
  retries: process.env.CI ? 1 : 0,

  /* Reporter: lista compatta in locale; JUnit XML + HTML in CI. */
  reporter: process.env.CI
    ? [['junit', { outputFile: 'e2e/test-results/results.xml' }], ['html', { outputFolder: 'e2e/playwright-report', open: 'never' }]]
    : [['list']],

  use: {
    baseURL: 'http://localhost:3000',

    /* Trace on first retry per debugging CI. */
    trace: 'on-first-retry',

    /* Screenshot solo a fallimento: artifact CI. */
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    /* Firefox + WebKit: follow-up (gap multi-browser — TSK successivo).
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    */
  ],

  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
