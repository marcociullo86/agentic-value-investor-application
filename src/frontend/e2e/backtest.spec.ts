/**
 * E2E — Backtest "Verifica storica" (TSK-352, US-106, EP-024 Fase 3).
 *
 * Strategia mocking:
 *  - Tutti gli scenari usano `page.route()` con fixture JSON deterministiche.
 *    Il BE NON è avviato in CI.
 *  - Coerente con il pattern Playwright ep-016/ep-018 + deep-analysis.spec.ts.
 *
 * 2 scenari E2E AC TSK-352:
 *  1. Ticker con storico ricco (AAPL) → pannello completo:
 *     tabella 3 strategie + marker timeline + banner caveat.
 *  2. Ticker IPO recente (NEWIPOQ) → INSUFFICIENT_HISTORY + nessun grafico.
 *
 * A11y:
 *  - Tabella: header semantici scope=col visibili.
 *  - Banner caveat: role="status".
 *  - Focus sul verdetto sintetico dopo apertura pannello.
 *
 * Non-regressione 4 tab:
 *  - Riepilogo / Analisi Base / Deep / Technical invariati con il pannello chiuso.
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mockAuthSession } from './helpers/auth';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const summaryAaplFixture = require('./fixtures/summary-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const backtestAaplFixture = require('./fixtures/backtest-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const backtestIpoFixture = require('./fixtures/backtest-ipo-insufficient.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const summaryViNegativeFixture = require('./fixtures/summary-vi-negative.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const analysisAaplFixture = require('./fixtures/analysis-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const historicalAaplFixture = require('./fixtures/historical-aapl.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockSummaryRoute(page: Page, ticker: string, fixture: Record<string, unknown>): Promise<void> {
  await page.route(`**/api/analysis/${ticker}/summary`, (route) =>
    route.fulfill({ json: fixture }),
  );
}

async function mockBacktestRoute(page: Page, ticker: string, fixture: Record<string, unknown>): Promise<void> {
  await page.route(`**/api/analysis/${ticker}/backtest**`, (route) =>
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
// Scenario 1 — ticker con storico ricco (AAPL)
// ---------------------------------------------------------------------------
test('SC1: AAPL storico ricco — click BACKTEST → pannello completo (tabella 3 strategie + caveat banner)', async ({ page }) => {
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  await mockBacktestRoute(page, 'AAPL', backtestAaplFixture);

  await page.goto('/analysis?ticker=AAPL');

  // Aspetta che il Riepilogo sia caricato
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  // Prima del click: il pannello backtest è in stato idle (hint visibile)
  await expect(page.getByTestId('backtest-panel')).toBeVisible();
  await expect(page.getByTestId('backtest-idle-hint')).toBeVisible();

  // Click sul bottone BACKTEST
  const triggerBtn = page.getByTestId('backtest-trigger-button');
  await expect(triggerBtn).toBeVisible();
  await expect(triggerBtn).not.toBeDisabled();
  await triggerBtn.click();

  // Aspetta il risultato (polling / fetch)
  await expect(page.getByTestId('backtest-verdict-hero')).toBeVisible({ timeout: 15_000 });

  // Verdetto sintetico POSITIVE_EDGE
  const hero = page.getByTestId('backtest-verdict-hero');
  expect(await hero.getAttribute('data-edge')).toBe('POSITIVE_EDGE');

  // Tabella 3 strategie
  await expect(page.getByTestId('backtest-strategy-table')).toBeVisible();
  await expect(page.getByTestId('backtest-strategy-row-EP024_ENTER_NOW')).toBeVisible();
  await expect(page.getByTestId('backtest-strategy-row-VI_ONLY')).toBeVisible();
  await expect(page.getByTestId('backtest-strategy-row-BUY_AND_HOLD')).toBeVisible();

  // Verifica almeno 1 trade EP024 e 1 trade VI nella tabella
  const ep024Row = page.getByTestId('backtest-strategy-row-EP024_ENTER_NOW');
  await expect(ep024Row).toContainText('7'); // 7 trade nella fixture

  // Banner caveat SEMPRE visibile
  const caveat = page.getByTestId('backtest-caveat-banner');
  await expect(caveat).toBeVisible();
  expect(await caveat.getAttribute('role')).toBe('status');
  await expect(caveat).toContainText(/singolo titolo/i);
});

// ---------------------------------------------------------------------------
// Scenario 2 — ticker IPO recente (INSUFFICIENT_HISTORY)
// ---------------------------------------------------------------------------
test('SC2: IPO recente — click BACKTEST → INSUFFICIENT_HISTORY, nessun grafico parziale', async ({ page }) => {
  // Usa un ticker fittizio IPO per la fixture di storico insufficiente
  await page.route('**/api/analysis/NEWIPOQ/summary', (route) =>
    route.fulfill({
      json: {
        ...summaryViNegativeFixture,
        ticker: 'NEWIPOQ',
      },
    }),
  );
  await mockBacktestRoute(page, 'NEWIPOQ', backtestIpoFixture);

  await page.goto('/analysis?ticker=NEWIPOQ');

  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  // Click BACKTEST
  const triggerBtn = page.getByTestId('backtest-trigger-button');
  await expect(triggerBtn).toBeVisible();
  await triggerBtn.click();

  // Deve mostrare il messaggio INSUFFICIENT_HISTORY
  const insufficientPanel = page.getByTestId('backtest-insufficient-history');
  await expect(insufficientPanel).toBeVisible({ timeout: 15_000 });
  await expect(insufficientPanel).toContainText(/storico insufficiente/i);
  await expect(insufficientPanel).toContainText(/Storico FMP copre solo 18 mesi/i);

  // Nessuna tabella e nessun chart parziale (AC TSK-352)
  await expect(page.getByTestId('backtest-strategy-table')).toHaveCount(0);
  await expect(page.getByTestId('backtest-verdict-hero')).toHaveCount(0);

  // Caveat banner sempre visibile (anche in caso insufficiente)
  await expect(page.getByTestId('backtest-caveat-banner')).toBeVisible();
});

// ---------------------------------------------------------------------------
// A11y — tabella header semantici scope=col
// ---------------------------------------------------------------------------
test('A11y: tabella strategie ha header scope=col per screen reader', async ({ page }) => {
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  await mockBacktestRoute(page, 'AAPL', backtestAaplFixture);

  await page.goto('/analysis?ticker=AAPL');
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  await page.getByTestId('backtest-trigger-button').click();
  await expect(page.getByTestId('backtest-strategy-table')).toBeVisible({ timeout: 15_000 });

  // Header colonne con scope="col" (a11y WCAG 2.2 AA)
  const table = page.getByTestId('backtest-strategy-table');
  const colHeaders = table.locator('th[scope="col"]');
  await expect(colHeaders).toHaveCount(7); // Strategia, Trade, Win%, Rend.medio, Totale, Holding, Exit
});

// ---------------------------------------------------------------------------
// A11y — banner caveat ha ruolo ARIA status
// ---------------------------------------------------------------------------
test('A11y: banner caveat ha role=status (non alert — non interrompe SR)', async ({ page }) => {
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  await mockBacktestRoute(page, 'AAPL', backtestAaplFixture);

  await page.goto('/analysis?ticker=AAPL');
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  await page.getByTestId('backtest-trigger-button').click();
  const caveat = page.getByTestId('backtest-caveat-banner');
  await expect(caveat).toBeVisible({ timeout: 15_000 });
  expect(await caveat.getAttribute('role')).toBe('status');
  expect(await caveat.getAttribute('aria-live')).toBe('polite');
});

// ---------------------------------------------------------------------------
// Non-regressione — Riepilogo con pannello idle non interferisce con altri tab
// ---------------------------------------------------------------------------
test('NR1: Riepilogo idle (pannello backtest chiuso) — 4 tab presenti e navigabili', async ({ page }) => {
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  // Backtest non viene triggerato
  await page.route('**/api/analysis/AAPL/backtest**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );

  await page.goto('/analysis?ticker=AAPL');
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  // Stato idle: hint visibile
  await expect(page.getByTestId('backtest-idle-hint')).toBeVisible();
  // Nessun risultato backtest
  await expect(page.getByTestId('backtest-verdict-hero')).toHaveCount(0);

  // 4 tab navigabili
  const nav = page.getByTestId('analysis-tab-nav');
  await expect(nav).toBeVisible();
  await expect(nav.getByTestId('tab-analisi-base')).toBeVisible();
  await expect(nav.getByTestId('tab-deep-analysis')).toBeVisible();
  await expect(nav.getByTestId('tab-technical-analysis')).toBeVisible();
  await expect(nav.getByTestId('tab-summary-active')).toBeVisible();
});

// ---------------------------------------------------------------------------
// Non-regressione — Analisi Base invariata dopo aggiunta bottone BACKTEST
// ---------------------------------------------------------------------------
test('NR2: /analysis/base — TrafficLightPanel invariato (bottone BACKTEST non presente qui)', async ({ page }) => {
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisAaplFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );

  await page.goto('/analysis/base?ticker=AAPL');

  // TrafficLightPanel ancora presente
  const firstCard = page.locator('[data-testid^="rule-signal-card-"]').first();
  await expect(firstCard).toBeVisible({ timeout: 15_000 });

  // Tab nav con i 4 tab (Riepilogo è navigabile)
  await expect(page.getByTestId('tab-summary')).toBeVisible();
});
