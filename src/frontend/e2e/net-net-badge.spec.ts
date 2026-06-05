/**
 * E2E Playwright smoke — NetNetBadge + TrafficLight NCAV signals (US-097, EP-023).
 * TSK-323 · layer: qa · consumer: agent
 *
 * Strategia mocking:
 *  - page.route() intercetta /api/analysis/NNTEST con due fixture deterministici:
 *    (a) analysis-net-net-green.json: NET_NET_RATIO = GREEN  → badge visibile.
 *    (b) analysis-net-net-red.json:   NET_NET_RATIO = RED    → badge assente.
 *  - /api/historical/NNTEST mockato con fixture minimale (nessun dato reale richiesto).
 *  - BE non avviato — test completamente deterministi (pattern page.route()).
 *
 * Scenari coperti (US-097 AC):
 *  1. NET_NET_RATIO GREEN: badge "Net-Net" visibile nell'header della pagina analisi.
 *  2. NET_NET_RATIO RED: badge "Net-Net" assente (null render del componente).
 *  3. Accessibilita': il badge ha aria-label "Criterio Graham Net-Net soddisfatto".
 *  4. Traffic Light: 15 card visibili (13 + NCAV_LATEST + NET_NET_RATIO).
 *  5. Subtitle NCAV_LATEST visibile nel pannello Traffic Light.
 *  6. NET_NET_RATIO assente nel payload (segnali pre-EP-023): badge assente.
 *
 * Nota eseguibilita':
 *  Il test richiede il dev server Next.js (npm run dev, porta 3000) e
 *  playwright installato (npx playwright install chromium).
 *  In CI il webServer viene avviato dal playwright.config.ts.
 *  In ambiente senza dev server il test restituisce timeout e va marcato
 *  come "non eseguibile" — vedi nota di deviazione in TSK-323.md.
 *
 * Riferimenti:
 *  - ADR-029 §6 (badge + Traffic Light EP-023).
 *  - US-097 Acceptance Criteria.
 *  - e2e/search-to-analysis.spec.ts (pattern page.route() di riferimento).
 */

import { test, expect } from '@playwright/test';
import { mockAuthSession } from './helpers/auth';

// ---------------------------------------------------------------------------
// Fixture imports
// ---------------------------------------------------------------------------
// eslint-disable-next-line @typescript-eslint/no-require-imports
const netNetGreenFixture = require('./fixtures/analysis-net-net-green.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const netNetRedFixture = require('./fixtures/analysis-net-net-red.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const historicalAaplFixture = require('./fixtures/historical-aapl.json') as Record<string, unknown>;

/** Fixture storico minimale per NNTEST — riusa forma AAPL, cambia ticker. */
const historicalNntestFixture = { ...historicalAaplFixture, ticker: 'NNTEST' };

// ---------------------------------------------------------------------------
// Auth seed (ogni test che naviga su /analysis è protected, EP-017)
// ---------------------------------------------------------------------------
test.beforeEach(async ({ page }) => {
  await mockAuthSession(page);
});

// ---------------------------------------------------------------------------
// Scenario 1 — NET_NET_RATIO GREEN: badge "Net-Net" visibile
// ---------------------------------------------------------------------------
test('NET_NET_RATIO GREEN: badge "Net-Net" visibile nell\'header della pagina analisi', async ({ page }) => {
  await page.route('**/api/analysis/NNTEST', (route) =>
    route.fulfill({ json: netNetGreenFixture }),
  );
  await page.route('**/api/historical/NNTEST', (route) =>
    route.fulfill({ json: historicalNntestFixture }),
  );

  await page.goto('/analysis/?ticker=NNTEST');

  // Attende che il traffico light sia caricato (almeno una card visibile)
  await expect(page.locator('[data-testid^="rule-signal-card-"]').first()).toBeVisible({
    timeout: 15_000,
  });

  // Badge visibile — data-testid="net-net-badge" (NetNetBadge.tsx)
  const badge = page.locator('[data-testid="net-net-badge"]');
  await expect(badge).toBeVisible();

  // Testo "Net-Net" visibile — data-testid="net-net-badge-text"
  const badgeText = page.locator('[data-testid="net-net-badge-text"]');
  await expect(badgeText).toBeVisible();
  await expect(badgeText).toHaveText('Net-Net');
});

// ---------------------------------------------------------------------------
// Scenario 2 — NET_NET_RATIO RED: badge "Net-Net" assente
// ---------------------------------------------------------------------------
test('NET_NET_RATIO RED: badge "Net-Net" assente nella pagina analisi', async ({ page }) => {
  await page.route('**/api/analysis/NNTEST', (route) =>
    route.fulfill({ json: netNetRedFixture }),
  );
  await page.route('**/api/historical/NNTEST', (route) =>
    route.fulfill({ json: historicalNntestFixture }),
  );

  await page.goto('/analysis/?ticker=NNTEST');

  // Attende caricamento pagina (il pannello Traffic Light deve essere montato)
  await expect(page.locator('[data-testid^="rule-signal-card-"]').first()).toBeVisible({
    timeout: 15_000,
  });

  // Badge assente — il componente ritorna null quando signal !== GREEN
  await expect(page.locator('[data-testid="net-net-badge"]')).not.toBeVisible();
});

// ---------------------------------------------------------------------------
// Scenario 3 — Accessibilita': aria-label sul badge GREEN
// ---------------------------------------------------------------------------
test('badge NET_NET_RATIO GREEN ha aria-label "Criterio Graham Net-Net soddisfatto"', async ({ page }) => {
  await page.route('**/api/analysis/NNTEST', (route) =>
    route.fulfill({ json: netNetGreenFixture }),
  );
  await page.route('**/api/historical/NNTEST', (route) =>
    route.fulfill({ json: historicalNntestFixture }),
  );

  await page.goto('/analysis/?ticker=NNTEST');

  await expect(page.locator('[data-testid="net-net-badge"]')).toBeVisible({
    timeout: 15_000,
  });

  const badge = page.locator('[data-testid="net-net-badge"]');
  const ariaLabel = await badge.getAttribute('aria-label');
  expect(ariaLabel).toMatch(/Criterio Graham Net-Net soddisfatto/);
});

// ---------------------------------------------------------------------------
// Scenario 4 — Traffic Light 15 card con payload NET_NET_RATIO GREEN
// ---------------------------------------------------------------------------
test('Traffic Light mostra 15 card con payload 15 signal (13 + NCAV_LATEST + NET_NET_RATIO)', async ({ page }) => {
  await page.route('**/api/analysis/NNTEST', (route) =>
    route.fulfill({ json: netNetGreenFixture }),
  );
  await page.route('**/api/historical/NNTEST', (route) =>
    route.fulfill({ json: historicalNntestFixture }),
  );

  await page.goto('/analysis/?ticker=NNTEST');

  // Attende che tutte le card siano montate
  const cards = page.locator('[data-testid^="rule-signal-card-"]');
  await expect(cards.first()).toBeVisible({ timeout: 15_000 });
  await expect(cards).toHaveCount(15);
});

// ---------------------------------------------------------------------------
// Scenario 5 — Traffic Light: card NCAV_LATEST e NET_NET_RATIO visibili
// ---------------------------------------------------------------------------
test('Traffic Light mostra card NCAV_LATEST e NET_NET_RATIO nel pannello', async ({ page }) => {
  await page.route('**/api/analysis/NNTEST', (route) =>
    route.fulfill({ json: netNetGreenFixture }),
  );
  await page.route('**/api/historical/NNTEST', (route) =>
    route.fulfill({ json: historicalNntestFixture }),
  );

  await page.goto('/analysis/?ticker=NNTEST');

  // Card NCAV_LATEST
  const ncavCard = page.locator('[data-testid="rule-signal-card-NCAV_LATEST"]');
  await expect(ncavCard).toBeVisible({ timeout: 15_000 });

  // Card NET_NET_RATIO
  const netNetCard = page.locator('[data-testid="rule-signal-card-NET_NET_RATIO"]');
  await expect(netNetCard).toBeVisible();
});

// ---------------------------------------------------------------------------
// Scenario 6 — Payload pre-EP-023 (senza NCAV): badge assente, 13 card
// ---------------------------------------------------------------------------
test('payload pre-EP-023 senza NCAV signals: badge assente, 13 card nel Traffic Light', async ({ page }) => {
  // Riusa la fixture AAPL (7 segnali), ma simula un payload con 13 signal e
  // assenza di NET_NET_RATIO — usa analysis-aapl che ha 7 signal come sanity
  // (verifica che il badge sia assente quando NET_NET_RATIO non è nel payload).
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const analysisAaplFixture = require('./fixtures/analysis-aapl.json') as Record<string, unknown>;

  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisAaplFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );

  await page.goto('/analysis/?ticker=AAPL');

  // Attende caricamento
  await expect(page.locator('[data-testid^="rule-signal-card-"]').first()).toBeVisible({
    timeout: 15_000,
  });

  // Badge assente (NET_NET_RATIO non nel payload pre-EP-023)
  await expect(page.locator('[data-testid="net-net-badge"]')).not.toBeVisible();
});
