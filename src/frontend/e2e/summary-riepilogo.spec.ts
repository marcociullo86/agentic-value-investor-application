/**
 * E2E — Tab Riepilogo (TSK-344, US-104, EP-024 Fase 2).
 *
 * Strategia mocking:
 *  - Tutti gli scenari usano `page.route()` con fixture JSON deterministiche.
 *    Il BE NON è avviato in CI.
 *  - Coerente con il pattern Playwright ep-016/ep-018 + deep-analysis.spec.ts.
 *
 * 4 scenari E2E AC TSK-344:
 *  1. CPRT → Riepilogo attivo + hero "ASPETTA" + banner anti-COPART + decision path.
 *  2. AAPL → hero "ENTRA ORA" senza banner anti-COPART.
 *  3. VI-negativo → hero "EVITA" con decision path "VI gate failed".
 *  4. Deep-link compat: `/analysis/AAPL/deep` apre Deep (non Riepilogo);
 *     `/analysis?ticker=AAPL` apre Riepilogo.
 *
 * A11y: axe-style checks manuali (aria-label, role=alert, focus management).
 *
 * Non-regressione 3 tab:
 *  - Analisi Base (/analysis/base) presente e navigabile.
 *  - Deep Analysis (/analysis/deep) presente e navigabile.
 *  - Technical Analysis (/analysis/technical) presente e navigabile.
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mockAuthSession } from './helpers/auth';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const summaryCprtFixture = require('./fixtures/summary-cprt.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const summaryAaplFixture = require('./fixtures/summary-aapl.json') as Record<string, unknown>;
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

async function stubSilentBacktest(page: Page, ticker: string): Promise<void> {
  await page.route(`**/api/analysis/${ticker}/backtest**`, (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await mockAuthSession(page);
});

// ---------------------------------------------------------------------------
// Scenario 1 — CPRT: hero "ASPETTA" + banner anti-COPART
// ---------------------------------------------------------------------------
test('SC1: CPRT — Riepilogo attivo, hero ASPETTA, banner anti-COPART, decision path', async ({ page }) => {
  await mockSummaryRoute(page, 'CPRT', summaryCprtFixture);
  await stubSilentBacktest(page, 'CPRT');

  await page.goto('/analysis?ticker=CPRT');

  // La pagina principale Riepilogo è quella attiva
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('summary-page-title')).toContainText('CPRT');

  // Hero verdetto "ASPETTA"
  const hero = page.getByTestId('summary-hero');
  await expect(hero).toBeVisible({ timeout: 15_000 });
  expect(await hero.getAttribute('data-verdict')).toBe('WAIT_FOR_SETUP');

  const badge = page.getByTestId('summary-hero-badge');
  await expect(badge).toContainText(/ASPETTA/i);

  // Sub-headline con condizione di re-entry
  const sub = page.getByTestId('summary-hero-subheadline');
  await expect(sub).toContainText(/RSI 14d rientra sotto 50/i);

  // Banner anti-COPART visibile con role=alert
  const banner = page.getByTestId('summary-anti-copart-banner');
  await expect(banner).toBeVisible();
  expect(await banner.getAttribute('role')).toBe('alert');
  await expect(page.getByTestId('summary-anti-copart-text')).toContainText(/situazione COPART/i);

  // Link "Approfondisci" presente
  await expect(page.getByTestId('summary-anti-copart-deeplink')).toBeVisible();

  // Decision path
  const decisionPath = page.getByTestId('summary-decision-path');
  await expect(decisionPath).toBeVisible();
  await expect(decisionPath).toContainText(/WAIT_FOR_SETUP/i);

  // Footer disclaimer presente
  const footer = page.getByTestId('summary-disclaimer-footer');
  await expect(footer).toBeVisible();
  await expect(footer).toContainText(/advisory/i);
});

// ---------------------------------------------------------------------------
// Scenario 2 — AAPL: hero "ENTRA ORA" senza banner anti-COPART
// ---------------------------------------------------------------------------
test('SC2: AAPL — hero ENTRA ORA, nessun banner anti-COPART, 3 card fattori', async ({ page }) => {
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  await stubSilentBacktest(page, 'AAPL');

  await page.goto('/analysis?ticker=AAPL');

  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  // Hero "ENTRA ORA"
  const hero = page.getByTestId('summary-hero');
  await expect(hero).toBeVisible({ timeout: 15_000 });
  expect(await hero.getAttribute('data-verdict')).toBe('ENTER_NOW');

  const badge = page.getByTestId('summary-hero-badge');
  await expect(badge).toContainText(/ENTRA ORA/i);

  // Banner anti-COPART ASSENTE
  await expect(page.getByTestId('summary-anti-copart-banner')).toHaveCount(0);

  // 3 card fattori chiave visibili
  await expect(page.getByTestId('summary-factor-cards')).toBeVisible();
  await expect(page.getByTestId('summary-card-vi')).toBeVisible();
  await expect(page.getByTestId('summary-card-deep')).toBeVisible();
  await expect(page.getByTestId('summary-card-ta')).toBeVisible();

  // Link "Vedi Analisi Base →" nella card VI
  await expect(page.getByTestId('summary-card-vi-link')).toBeVisible();
  expect(await page.getByTestId('summary-card-vi-link').getAttribute('href')).toContain('/analysis/base');
});

// ---------------------------------------------------------------------------
// Scenario 3 — VI-negativo: hero "EVITA" + decision path "VI gate failed"
// ---------------------------------------------------------------------------
test('SC3: VI-negativo — hero EVITA, decision path mostra VI gate failed', async ({ page }) => {
  await mockSummaryRoute(page, 'XVIT', summaryViNegativeFixture);
  await stubSilentBacktest(page, 'XVIT');

  await page.goto('/analysis?ticker=XVIT');

  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  // Hero "EVITA"
  const hero = page.getByTestId('summary-hero');
  await expect(hero).toBeVisible({ timeout: 15_000 });
  expect(await hero.getAttribute('data-verdict')).toBe('AVOID');

  const badge = page.getByTestId('summary-hero-badge');
  await expect(badge).toContainText(/EVITA/i);

  // Sub-headline cita il gate VI fallito
  const sub = page.getByTestId('summary-hero-subheadline');
  await expect(sub).toContainText(/gate Value Investing è fallito/i);

  // Decision path mostra il fallimento
  const decisionPath = page.getByTestId('summary-decision-path');
  await expect(decisionPath).toBeVisible();
  await expect(decisionPath).toContainText(/VI gate failed/i);
});

// ---------------------------------------------------------------------------
// Scenario 4 — Deep-link compat
// ---------------------------------------------------------------------------
test('SC4a: /analysis?ticker=AAPL — apre Riepilogo (non Analisi Base)', async ({ page }) => {
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  await stubSilentBacktest(page, 'AAPL');

  await page.goto('/analysis?ticker=AAPL');

  // Deve mostrare il Riepilogo — NON la TrafficLightPanel
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });
  // La TrafficLightPanel (Analisi Base) NON deve essere qui
  await expect(page.locator('[data-testid="traffic-light-panel"]')).toHaveCount(0);
  // Il tab Riepilogo è attivo (aria-current=page)
  await expect(page.getByTestId('tab-summary-active')).toBeVisible();
});

test('SC4b: /analysis/deep?ticker=AAPL — apre Deep Analysis (non Riepilogo)', async ({ page }) => {
  // Mock per il tab Deep Analysis
  await page.route('**/api/analysis/AAPL/deep/latest', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ticker: 'AAPL',
        status: 'NONE',
        runId: null,
        invokeLlm: false,
        requestedAt: null,
        completedAt: null,
        result: null,
        error: null,
      }),
    }),
  );
  await page.route('**/api/analysis/AAPL/deep/ingest/latest', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ ticker: 'AAPL', status: 'NONE', runId: null, requestedAt: null, completedAt: null, summary: null, error: null }),
    }),
  );

  await page.goto('/analysis/deep?ticker=AAPL');

  // Deve mostrare la Deep Analysis — NON il Riepilogo
  await expect(page.getByTestId('deep-analysis-empty')).toBeVisible({ timeout: 15_000 });
  // La SummaryPage NON deve essere qui
  await expect(page.getByTestId('summary-page')).toHaveCount(0);
});

// ---------------------------------------------------------------------------
// A11y smoke — banner anti-COPART con role=alert
// ---------------------------------------------------------------------------
test('A11y: banner anti-COPART ha role=alert e aria-live=assertive', async ({ page }) => {
  await mockSummaryRoute(page, 'CPRT', summaryCprtFixture);
  await stubSilentBacktest(page, 'CPRT');

  await page.goto('/analysis?ticker=CPRT');

  const banner = page.getByTestId('summary-anti-copart-banner');
  await expect(banner).toBeVisible({ timeout: 15_000 });

  expect(await banner.getAttribute('role')).toBe('alert');
  expect(await banner.getAttribute('aria-live')).toBe('assertive');
});

// ---------------------------------------------------------------------------
// Non-regressione: Analisi Base navigabile e indipendente
// ---------------------------------------------------------------------------
test('NR1: /analysis/base?ticker=AAPL — mostra TrafficLightPanel (Analisi Base invariata)', async ({ page }) => {
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisAaplFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );

  await page.goto('/analysis/base?ticker=AAPL');

  // TrafficLightPanel visibile — Analisi Base invariata
  const firstCard = page.locator('[data-testid^="rule-signal-card-"]').first();
  await expect(firstCard).toBeVisible({ timeout: 15_000 });

  // Tab Riepilogo è LINK (non attivo) — si può navigare indietro
  await expect(page.getByTestId('tab-summary')).toBeVisible();
  await expect(page.getByTestId('tab-analisi-base-active')).toBeVisible();
});

// ---------------------------------------------------------------------------
// Non-regressione: Technical Analysis navigabile da Riepilogo
// ---------------------------------------------------------------------------
test('NR2: da /analysis?ticker=AAPL → click tab Technical Analysis → naviga a /analysis/technical', async ({ page }) => {
  await mockSummaryRoute(page, 'AAPL', summaryAaplFixture);
  await stubSilentBacktest(page, 'AAPL');
  // Stub /technical per quando navighiamo
  await page.route('**/api/analysis/AAPL/technical**', (route) =>
    route.fulfill({ json: {} }),
  );

  await page.goto('/analysis?ticker=AAPL');
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });

  // Click sul tab Technical Analysis nella nav
  await page.getByTestId('tab-technical-analysis').click();

  // Deve navigare a /analysis/technical
  await page.waitForURL(/\/analysis\/technical/, { waitUntil: 'commit' });
  expect(page.url()).toContain('/analysis/technical');
  expect(page.url()).toContain('ticker=AAPL');
});
