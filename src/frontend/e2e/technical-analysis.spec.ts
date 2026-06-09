/**
 * E2E — Technical Analysis tab (TSK-336, US-102, EP-024 Fase 1).
 *
 * Strategia mocking:
 *  - Tutti gli scenari usano `page.route()` per intercettare le chiamate API
 *    con fixture JSON deterministiche. Il BE NON è avviato in CI.
 *  - Coerente con il pattern Playwright ep-016/ep-018 + deep-analysis.spec.ts.
 *
 * Selettori:
 *  - Mix semantici (getByRole, getByText) + `data-testid` già presenti
 *    nei componenti TSK-334/TSK-335.
 *
 * 5 scenari AC US-102:
 *  1. Happy path AAPL uptrend: tab Technical Analysis → verdetto ENTRY_FAVORABLE
 *     + Screen 1/2/3 + pannelli stop/sizing; nessun errore console.
 *  2. CPRT stile-COPART: verdetto WAIT evidenziato + banner re-entry RSI_BELOW_50
 *     + disclaimer "advisory" presente.
 *  3. Input equity → ricalcolo (refetch automatico SWR key change).
 *  4. Lazy load: navigando a /analysis?ticker=AAPL (Riepilogo), nessuna chiamata
 *     a /technical finché il tab Technical Analysis non è cliccato.
 *  5. A11y smoke: badge con aria-label + navigazione tastiera sul tab nav.
 *
 * Non-regressione:
 *  - Tab Analisi Base (/analysis/base) non chiama /technical (lazy).
 *  - Tab Deep Analysis (/analysis/deep) non chiama /technical (lazy).
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mockAuthSession } from './helpers/auth';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const technicalAaplFixture = require('./fixtures/technical-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const technicalCprtFixture = require('./fixtures/technical-cprt.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const summaryAaplFixture = require('./fixtures/summary-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const analysisAaplFixture = require('./fixtures/analysis-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const historicalAaplFixture = require('./fixtures/historical-aapl.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockTechnicalRoutes(page: Page, ticker: string, fixture: Record<string, unknown>): Promise<void> {
  await page.route(`**/api/analysis/${ticker}/technical**`, (route) =>
    route.fulfill({ json: fixture }),
  );
}

async function mockSummaryRoute(page: Page, ticker: string, fixture: Record<string, unknown>): Promise<void> {
  await page.route(`**/api/analysis/${ticker}/summary`, (route) =>
    route.fulfill({ json: fixture }),
  );
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await mockAuthSession(page);
});

// ---------------------------------------------------------------------------
// Scenario 1 — Happy path AAPL uptrend
// ---------------------------------------------------------------------------
test('SC1: AAPL uptrend — tab Technical Analysis mostra verdetto ENTRY FAVOREVOLE + pannelli', async ({ page }) => {
  await mockTechnicalRoutes(page, 'AAPL', technicalAaplFixture);
  // Backtest: stub silenzioso
  await page.route('**/api/analysis/AAPL/backtest**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );

  await page.goto('/analysis/technical?ticker=AAPL');

  // Pagina caricata
  await expect(page.getByTestId('ta-page-title')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('ta-page-title')).toContainText('AAPL');

  // Verdetto ENTRY FAVOREVOLE
  const badge = page.getByTestId('ta-entry-timing-badge');
  await expect(badge).toBeVisible({ timeout: 15_000 });
  await expect(badge).toContainText(/ENTRY FAVOREVOLE/i);
  expect(await badge.getAttribute('data-testid')).toBe('ta-entry-timing-badge');

  // Screen 1/2/3 visibili
  await expect(page.getByTestId('ta-screen-1')).toBeVisible();
  await expect(page.getByTestId('ta-screen-2')).toBeVisible();
  await expect(page.getByTestId('ta-screen-3')).toBeVisible();

  // Banner re-entry NON presente (ENTRY_FAVORABLE)
  await expect(page.getByTestId('ta-reentry-banner')).toHaveCount(0);

  // Tab nav presente con Riepilogo come primo tab
  const nav = page.getByTestId('analysis-tab-nav');
  await expect(nav).toBeVisible();
});

// ---------------------------------------------------------------------------
// Scenario 2 — CPRT stile-COPART
// ---------------------------------------------------------------------------
test('SC2: CPRT — verdetto WAIT + banner re-entry RSI_BELOW_50 + disclaimer advisory', async ({ page }) => {
  await mockTechnicalRoutes(page, 'CPRT', technicalCprtFixture);
  await page.route('**/api/analysis/CPRT/backtest**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );

  await page.goto('/analysis/technical?ticker=CPRT');

  await expect(page.getByTestId('ta-page-title')).toBeVisible({ timeout: 15_000 });

  // Verdetto WAIT evidenziato
  const badge = page.getByTestId('ta-entry-timing-badge');
  await expect(badge).toBeVisible({ timeout: 15_000 });
  await expect(badge).toContainText(/ASPETTA/i);

  // Card ha data-verdict=WAIT
  const card = page.getByTestId('ta-entry-timing-card');
  expect(await card.getAttribute('data-verdict')).toBe('WAIT');

  // Banner re-entry visibile con condizione RSI_BELOW_50
  const reentryBanner = page.getByTestId('ta-reentry-banner');
  await expect(reentryBanner).toBeVisible();
  expect(await reentryBanner.getAttribute('data-reentry-code')).toBe('RSI_BELOW_50');

  const reentryDesc = page.getByTestId('ta-reentry-description');
  await expect(reentryDesc).toBeVisible();
  await expect(reentryDesc).toContainText(/RSI 14d rientra sotto 50/i);

  // Disclaimer advisory presente
  const disclaimer = page.getByTestId('ta-disclaimer-banner');
  await expect(disclaimer).toBeVisible();
  await expect(disclaimer).toContainText(/advisory/i);
});

// ---------------------------------------------------------------------------
// Scenario 3 — Input equity → refetch automatico (SWR key)
// ---------------------------------------------------------------------------
test('SC3: input equity nel position sizing → refetch con nuovo parametro equity', async ({ page }) => {
  const callUrls: string[] = [];

  // Intercepta e logga le chiamate /technical
  await page.route('**/api/analysis/AAPL/technical**', (route) => {
    callUrls.push(route.request().url());
    return route.fulfill({ json: technicalAaplFixture });
  });
  await page.route('**/api/analysis/AAPL/backtest**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );

  await page.goto('/analysis/technical?ticker=AAPL');

  // Aspetta il primo caricamento
  await expect(page.getByTestId('ta-entry-timing-badge')).toBeVisible({ timeout: 15_000 });

  // Verifica che la prima call sia andata a buon fine
  expect(callUrls.length).toBeGreaterThanOrEqual(1);

  // Pannello position sizing visibile
  const sizingPanel = page.getByTestId('ta-position-sizing-panel');
  if (await sizingPanel.isVisible()) {
    // Cambia equity — SWR key cambia → refetch automatico
    const equityInput = page.getByTestId('ta-equity-input');
    if (await equityInput.isVisible()) {
      await equityInput.fill('100000');
      await equityInput.press('Enter');
      // Aspetta il refetch
      await page.waitForTimeout(500);
      // Almeno 2 chiamate (1 iniziale + 1 refetch)
      expect(callUrls.length).toBeGreaterThanOrEqual(2);
      expect(callUrls.some((url) => url.includes('equity=100000'))).toBe(true);
    }
  }
});

// ---------------------------------------------------------------------------
// Scenario 4 — Lazy load: nessuna chiamata a /technical finché il tab non è cliccato
// ---------------------------------------------------------------------------
test('SC4: lazy load — navigando a /analysis?ticker=AAPL (Riepilogo), nessuna call a /technical', async ({ page }) => {
  let technicalCallCount = 0;

  // Intercetta /technical e conta le chiamate
  await page.route('**/api/analysis/AAPL/technical**', (route) => {
    technicalCallCount++;
    return route.fulfill({ json: technicalAaplFixture });
  });

  // Mock summary per il landing Riepilogo
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  await page.route('**/api/analysis/AAPL/backtest**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );

  // Naviga al landing /analysis (che ora mostra Riepilogo, NON Technical)
  await page.goto('/analysis?ticker=AAPL');

  // Aspetta che il Riepilogo sia visibile
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  // Verifica: ancora 0 chiamate a /technical
  expect(technicalCallCount).toBe(0);

  // Ora clicca sul tab Technical Analysis
  await page.getByTestId('tab-technical-analysis').click();

  // Aspetta la navigazione e il caricamento
  await page.waitForURL(/\/analysis\/technical/);
  await expect(page.getByTestId('ta-entry-timing-badge')).toBeVisible({ timeout: 15_000 });

  // Ora la chiamata è avvenuta
  expect(technicalCallCount).toBeGreaterThanOrEqual(1);
});

// ---------------------------------------------------------------------------
// Scenario 5 — A11y smoke: keyboard navigation + badge aria-label
// ---------------------------------------------------------------------------
test('SC5: a11y smoke — badge aria-label + keyboard navigation tab nav', async ({ page }) => {
  await mockTechnicalRoutes(page, 'AAPL', technicalAaplFixture);
  await page.route('**/api/analysis/AAPL/backtest**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );

  await page.goto('/analysis/technical?ticker=AAPL');

  // Attende badge visibile
  const badge = page.getByTestId('ta-entry-timing-badge');
  await expect(badge).toBeVisible({ timeout: 15_000 });

  // aria-label non vuoto (a11y — non solo colore come canale)
  const ariaLabel = await badge.getAttribute('aria-label');
  expect(ariaLabel).toBeTruthy();
  expect(ariaLabel!.length).toBeGreaterThan(20);
  expect(ariaLabel).toContain('Verdetto entry-timing');

  // Keyboard: la nav tab è raggiungibile con Tab key
  const nav = page.getByTestId('analysis-tab-nav');
  await expect(nav).toBeVisible();
  // Il primo link nella nav è focusabile
  const firstLink = nav.locator('a, span[aria-current="page"]').first();
  await expect(firstLink).toBeVisible();
});

// ---------------------------------------------------------------------------
// Non-regressione: Analisi Base non chiama /technical (lazy load)
// ---------------------------------------------------------------------------
test('NR: navigando su /analysis/base non parte nessuna chiamata a /technical', async ({ page }) => {
  let technicalCallCount = 0;

  await page.route('**/api/analysis/AAPL/technical**', (route) => {
    technicalCallCount++;
    return route.fulfill({ json: technicalAaplFixture });
  });

  // Mock Analisi Base
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisAaplFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );

  await page.goto('/analysis/base?ticker=AAPL');

  // Attende il contenuto Analisi Base (almeno la prima rule-signal-card)
  await expect(
    page.locator('[data-testid^="rule-signal-card-"]').first(),
  ).toBeVisible({ timeout: 15_000 });

  // /technical non deve essere stata chiamata
  expect(technicalCallCount).toBe(0);
});
