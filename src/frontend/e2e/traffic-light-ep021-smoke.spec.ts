/**
 * E2E Smoke — TrafficLight EP-021 typed-driven subtitles (US-095 / TSK-321).
 *
 * Verifica che la pagina `/analysis?ticker=AAPL` mostri:
 *  1. Esattamente 13 card di segnale (tutti i ruleId EP-021).
 *  2. Ogni card ha un subtitle visibile sulla faccia compressa (Graham cards)
 *     o sia espandibile con un pannello dettaglio non vuoto.
 *  3. Nessuna stringa "N/A" visibile come subtitle (regressione fallback stale).
 *  4. Entrambe le sezioni "Criteri Buffett Quality" e "Criteri Graham Defensive"
 *     sono visibili.
 *
 * Strategia mocking:
 *  - Usa `page.route()` per intercettare `/api/analysis/AAPL` con il fixture
 *    EP-021 tipato (analysis-aapl-ep021.json, 13 segnali con campi typed).
 *  - Zero dipendenza BE/DB: deterministico in CI.
 *
 * Nota Playwright: se l'ambiente di test non ha un server Next.js avviato
 * (`npm run dev` su porta 3000), i test falliranno sulla navigazione iniziale.
 * In assenza di un server locale, il test è scritto ma non può essere eseguito
 * (vedi deviazione TSK-321 in gaps.md).
 *
 * Riferimenti:
 *  - TSK-321 §Test Playwright (smoke), US-095 AC, ADR-028 §6.
 *  - Fixture: e2e/fixtures/analysis-aapl-ep021.json (13 ruleId EP-021 tipati).
 */

import { test, expect } from '@playwright/test';
import { mockAuthSession } from './helpers/auth';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const analysisEp021Fixture = require('./fixtures/analysis-aapl-ep021.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const historicalAaplFixture = require('./fixtures/historical-aapl.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Setup — auth session + route mocking
// ---------------------------------------------------------------------------
test.beforeEach(async ({ page }) => {
  await mockAuthSession(page);

  // Mock API analysis con fixture EP-021 (13 ruleId tipati)
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisEp021Fixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );
});

// ---------------------------------------------------------------------------
// Smoke 1 — 13 card visibili con fixture EP-021
// ---------------------------------------------------------------------------
test('US-095 AC — TrafficLightPanel mostra 13 signal cards con fixture EP-021', async ({ page }) => {
  await page.goto('/analysis/?ticker=AAPL');

  // Aspetta che almeno una card sia visibile (caricamento completato)
  const firstCard = page.locator('[data-testid^="rule-signal-card-"]').first();
  await expect(firstCard).toBeVisible({ timeout: 15_000 });

  // Verifica esattamente 13 cards (i 13 ruleId EP-021 della fixture)
  const cards = page.locator('[data-testid^="rule-signal-card-"]');
  await expect(cards).toHaveCount(13);
});

// ---------------------------------------------------------------------------
// Smoke 2 — sezioni Buffett e Graham entrambe visibili
// ---------------------------------------------------------------------------
test('US-095 AC — TrafficLightPanel mostra sezione Buffett e sezione Graham', async ({ page }) => {
  await page.goto('/analysis/?ticker=AAPL');

  // Aspetta panel
  await expect(page.locator('[data-testid="traffic-light-panel"]')).toBeVisible({ timeout: 15_000 });

  // Sezione Buffett
  const buffettSection = page.locator('[data-testid="traffic-light-section-buffett"]');
  await expect(buffettSection).toBeVisible();
  await expect(page.locator('[data-testid="traffic-light-section-buffett-heading"]')).toHaveText('Criteri Buffett Quality');

  // Sezione Graham
  const grahamSection = page.locator('[data-testid="traffic-light-section-graham"]');
  await expect(grahamSection).toBeVisible();
  await expect(page.locator('[data-testid="traffic-light-section-graham-heading"]')).toHaveText('Criteri Graham Defensive');
});

// ---------------------------------------------------------------------------
// Smoke 3 — Graham cards mostrano subtitle typed-driven (non vuoto, non "N/A")
// ---------------------------------------------------------------------------
test('US-095 AC — Graham cards mostrano subtitle typed-driven non vuoto', async ({ page }) => {
  await page.goto('/analysis/?ticker=AAPL');

  // Aspetta le card Graham (SIZE_LATEST è sempre Graham)
  const sizeCard = page.locator('[data-testid="rule-signal-card-SIZE_LATEST"]');
  await expect(sizeCard).toBeVisible({ timeout: 15_000 });

  // I ruleId Graham che hanno subtitle visibile sulla faccia compressa
  const grahamRuleIds = [
    'SIZE_LATEST',
    'EARNINGS_STABILITY_10Y',
    'EPS_GROWTH_10Y',
    'PE_3Y_AVG',
    'PB_LATEST',
    'DIVIDEND_CONTINUITY_20Y',
  ];

  for (const ruleId of grahamRuleIds) {
    const subtitle = page.locator(`[data-testid="rule-signal-subtitle-${ruleId}"]`);
    await expect(subtitle).toBeVisible();

    // Subtitle non vuoto (regressione fallback paranoid stale)
    const text = await subtitle.textContent();
    expect(text, `subtitle vuoto per ${ruleId}`).toBeTruthy();
    expect(text!.trim().length, `subtitle empty string per ${ruleId}`).toBeGreaterThan(0);

    // Nessun subtitle = "N/A" (regressione record JSONB stale pre-EP-021)
    expect(text!.trim(), `subtitle "N/A" per ${ruleId}`).not.toBe('N/A');
  }
});

// ---------------------------------------------------------------------------
// Smoke 4 — click su una card Buffett → pannello espanso con subtitle non vuoto
// ---------------------------------------------------------------------------
test('US-095 AC — click su ROE_10Y_AVG espande il pannello con subtitle typed-driven', async ({ page }) => {
  await page.goto('/analysis/?ticker=AAPL');

  const roeCard = page.locator('[data-testid="rule-signal-card-ROE_10Y_AVG"]');
  await expect(roeCard).toBeVisible({ timeout: 15_000 });

  // Stato iniziale: details non visibile
  await expect(page.locator('[data-testid="rule-signal-details-ROE_10Y_AVG"]')).not.toBeVisible();

  // Click per espandere
  await roeCard.getByRole('button').click();

  // Details visibile dopo click
  const details = page.locator('[data-testid="rule-signal-details-ROE_10Y_AVG"]');
  await expect(details).toBeVisible();

  // "Valore osservato" presente e non vuoto (typed subtitle)
  const observed = details.locator('[data-testid="rule-signal-observed-ROE_10Y_AVG"]');
  await expect(observed).toBeVisible();
  const observedText = await observed.textContent();
  expect(observedText?.trim()).toBeTruthy();
  expect(observedText!.trim()).not.toBe('N/A');

  // Razionale (tooltip) presente e non vuoto
  const rationale = details.locator('[data-testid="rule-signal-rationale-ROE_10Y_AVG"]');
  await expect(rationale).toBeVisible();
  const rationaleText = await rationale.textContent();
  expect(rationaleText?.trim()).toBeTruthy();
});
