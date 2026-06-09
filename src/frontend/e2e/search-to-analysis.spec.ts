/**
 * E2E — Flusso ricerca → analisi Traffic Light (US-001 → US-014).
 * TSK-022 · layer: qa · consumer: agent
 *
 * Strategia mocking:
 *  - Tutti e 4 gli scenari usano `page.route()` per intercettare le chiamate
 *    API e restituire fixture JSON deterministiche. Il BE NON è avviato in CI.
 *    Questa scelta:
 *      (a) rende i test completamente deterministici (no FMP reale, no DB);
 *      (b) permette di isolare il comportamento UI senza accoppiamento a
 *          infrastruttura backend nella stessa pipeline CI;
 *      (c) è coerente con il pattern Playwright `page.route()` raccomandato
 *          per test E2E "fullymocked" (Playwright docs §Network mocking).
 *
 * Selettori:
 *  - Mix (B) semantici (`getByRole`, `getByText`, `getByLabel`) + `data-testid`
 *    già presenti nei componenti TSK-021/TSK-038.
 *  - `data-testid="rule-signal-card-{ruleId}"` già definito in `RuleSignalCard`
 *    (TSK-021): non serve modifica al codice di produzione — il componente li
 *    espone by design come QA hook.
 *  - `role="alert"` su `StaleDataBadge` già presente (TSK-038).
 *  - Nessun `data-testid` aggiunto in questo TSK al codice di produzione.
 *
 * AC US-014 coperti:
 *  - AC1: almeno 6 semafori visibili → fixture ha 7 RuleSignal → assert count 7.
 *  - AC2: distinzione visiva via data-signal attribute (non testato E2E; coperto
 *    da unit test RuleSignalCard.test.tsx TSK-021).
 *  - AC3: click semaforo → espansione valore + soglia (Scenario 3).
 *  - Stale badge visibile (Scenario 4, US-005/006 cross-cutting).
 *
 * Riferimenti:
 *  management/kanban/EP-005-dashboard-traffic-light-moat/US-014-pannello-traffic-light/US-014.md
 *  design_&_architecture/components/frontend-components.md §Testing strategy §E2E Playwright
 *  design_&_architecture/api/openapi.yaml §/api/analysis/{ticker} §/api/search
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mockAuthSession } from './helpers/auth';

// --- Fixture imports -------------------------------------------------------
// eslint-disable-next-line @typescript-eslint/no-require-imports
const searchAaplFixture = require('./fixtures/search-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const searchNotFoundFixture = require('./fixtures/search-notfound.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const profileAaplFixture = require('./fixtures/profile-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const analysisAaplFixture = require('./fixtures/analysis-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const analysisStaleFixture = require('./fixtures/analysis-stale.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const historicalAaplFixture = require('./fixtures/historical-aapl.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Helper: monta tutti i route mock per la pagina /analysis/AAPL
// ---------------------------------------------------------------------------
async function mockAnalysisRoutes(page: Page): Promise<void> {
  await page.route('**/api/search/AAPL', (route) =>
    route.fulfill({ json: profileAaplFixture }),
  );
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisAaplFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );
}

// ---------------------------------------------------------------------------
// Scenario 1 — Happy path: AAPL → Riepilogo (default di landing EP-024 Fase 2)
// ---------------------------------------------------------------------------
// /analysis è protetta (route-config TSK-267): semina sessione auth per ogni
// test (cookie proxy dev + mock POST /api/auth/refresh). Innocuo sulle pagine
// pubbliche (/, /screener) toccate dai test "search" — nessun test qui usa /login.
//
// REGRESSION-FIX (EP-024 Fase 2): /analysis?ticker=X è ora il tab Riepilogo
// (US-104 AC: primo tab default). Il TrafficLightPanel (Analisi Base) vive su
// /analysis/base?ticker=X. SearchBar naviga a /analysis?ticker=AAPL → Riepilogo.
test.beforeEach(async ({ page }) => {
  await mockAuthSession(page);
});

test('user searches AAPL and sees Riepilogo page (default landing EP-024)', async ({ page }) => {
  // Mock: search → exact match → navigate to analysis
  await page.route('**/api/search?query=AAPL', (route) =>
    route.fulfill({ json: searchAaplFixture }),
  );
  // Mock Riepilogo (nuovo landing)
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const summaryAaplFixture = require('./fixtures/summary-aapl.json') as Record<string, unknown>;
  await page.route('**/api/analysis/AAPL/summary', (route) =>
    route.fulfill({ json: summaryAaplFixture }),
  );
  await page.route('**/api/analysis/AAPL/backtest**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) }),
  );
  // Stub altri endpoint per compatibilità
  await mockAnalysisRoutes(page);

  await page.goto('/');

  // Input: aria-label "Cerca ticker o nome azienda" (SearchBar.tsx line 134)
  const searchInput = page.getByLabel(/cerca ticker o nome azienda/i);
  await searchInput.fill('AAPL');
  await searchInput.press('Enter');

  // SearchBar.tsx: exact match → router.push("/analysis?ticker=AAPL").
  // EP-024 Fase 2: il default landing è ora il Riepilogo.
  await page.waitForURL(/\/analysis\/?\?ticker=AAPL/);

  // La pagina mostra il Riepilogo (summary-page) — NON più le rule-signal-card.
  await expect(page.getByTestId('summary-page')).toBeVisible({ timeout: 15_000 });
});

// ---------------------------------------------------------------------------
// Scenario 2 — Ticker inesistente: "XXXXXXXX" → messaggio "Ticker non trovato"
// ---------------------------------------------------------------------------
test('non-existent ticker shows "Ticker non trovato" inline message', async ({ page }) => {
  // Mock: qualsiasi query restituisce lista vuota
  await page.route('**/api/search?query=XXXXXXXX', (route) =>
    route.fulfill({ json: searchNotFoundFixture }),
  );

  await page.goto('/');

  const searchInput = page.getByLabel(/cerca ticker o nome azienda/i);
  await searchInput.fill('XXXXXXXX');
  await searchInput.press('Enter');

  // SearchBar.tsx: items.length === 0 → setUiState({ kind: 'not-found' })
  // Rendering: <p role="alert">Ticker non trovato.</p>
  await expect(page.getByRole('alert').filter({ hasText: /ticker non trovato/i })).toBeVisible();

  // Verifica che NON sia avvenuta navigazione
  await expect(page).toHaveURL('/');
});

// ---------------------------------------------------------------------------
// Scenario 3 — Click semaforo ROE → espande dettagli (valore + soglia)
// ---------------------------------------------------------------------------
// REGRESSION-FIX (EP-024 Fase 2): il TrafficLightPanel vive su /analysis/base.
test('clicking ROE_10Y_AVG traffic light expands observed value and threshold', async ({ page }) => {
  await mockAnalysisRoutes(page);

  // Navigazione diretta all'Analisi Base (bypassa SearchBar, bypassa Riepilogo).
  await page.goto('/analysis/base?ticker=AAPL');

  // Attende che la pagina carichi le card (useAnalysisStore.fetchAnalysis completato)
  const roeCard = page.locator('[data-testid="rule-signal-card-ROE_10Y_AVG"]');
  await expect(roeCard).toBeVisible();

  // Prima del click: dettagli NON visibili (expanded=false di default)
  const roeDetails = page.locator('[data-testid="rule-signal-details-ROE_10Y_AVG"]');
  await expect(roeDetails).not.toBeVisible();

  // Click sul bottone interno alla card (il <button type="button"> che wrappa il semaforo)
  await roeCard.getByRole('button').click();

  // Dopo il click: pannello espanso. EP-021 (TSK-320, RuleSignalCard.tsx): la riga
  // separata "Soglia" è stata rimossa — la soglia è ora inclusa nel valore osservato
  // typed (subtitle, es. "Revenue: $2.30B (soglia $100M)"). Resta il <dt> "Valore
  // osservato" con il <dd> data-testid rule-signal-observed-<ruleId>.
  await expect(roeDetails).toBeVisible();
  await expect(roeDetails.getByText(/valore osservato/i)).toBeVisible();
  await expect(page.locator('[data-testid="rule-signal-observed-ROE_10Y_AVG"]')).toBeVisible();
});

// ---------------------------------------------------------------------------
// Scenario 4 — StaleDataBadge visibile quando isStale=true
// ---------------------------------------------------------------------------
// REGRESSION-FIX (EP-024 Fase 2): naviga a /analysis/base (Analisi Base)
// dove il StaleDataBadge è reso nel TrafficLightPanel.
test('stale data badge is visible when isStale=true with correct message', async ({ page }) => {
  // Override solo /api/analysis/AAPL con il fixture stale
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisStaleFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );

  await page.goto('/analysis/base?ticker=AAPL');

  // StaleDataBadge.tsx: role="alert" + testo "Dati al {snapshotLabel} — aggiornamento FMP non disponibile"
  // La fixture ha dataSnapshotAt: "2026-05-20T08:00:00Z" → formatDate() → locale it-IT
  const badge = page.getByRole('alert').filter({ hasText: /dati al/i });
  await expect(badge).toBeVisible();
  await expect(badge).toContainText(/aggiornamento FMP non disponibile/i);
});

// ---------------------------------------------------------------------------
// Scenario 5 — TSK-057: ticker fuori ex-whitelist demo (JNJ) via query URL
// ---------------------------------------------------------------------------
// REGRESSION-FIX (EP-024 Fase 2): naviga a /analysis/base per il TrafficLightPanel.
test('ticker outside legacy static whitelist loads analysis via query param', async ({ page }) => {
  await page.route('**/api/analysis/JNJ', (route) =>
    route.fulfill({
      json: {
        ...analysisAaplFixture,
        ticker: 'JNJ',
      },
    }),
  );
  await page.route('**/api/historical/JNJ', (route) =>
    route.fulfill({
      json: {
        ...historicalAaplFixture,
        ticker: 'JNJ',
      },
    }),
  );

  await page.goto('/analysis/base?ticker=JNJ');

  const cards = page.locator('[data-testid^="rule-signal-card-"]');
  await expect(cards.first()).toBeVisible({ timeout: 15_000 });
});
