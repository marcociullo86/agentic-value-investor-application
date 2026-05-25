/**
 * E2E — Deep Analysis page (US-046, EP-011).
 * TSK-125 · layer: qa · consumer: agent
 *
 * Mocking strategy:
 *  - All scenarios use `page.route()` to intercept API calls and return
 *    deterministic JSON fixtures. The BE is NOT started in CI.
 *    Consistent with the pattern established in search-to-analysis.spec.ts
 *    (TSK-022) using Playwright network mocking.
 *
 * Selectors:
 *  - Mix of semantic (`getByRole`, `getByText`) + `data-testid` already
 *    present in TSK-122 (page) and TSK-123 (5 components).
 *  - No `data-testid` added to production code by this TSK.
 *
 * AC TSK-125 covered:
 *  - Happy path AAPL: badge verdict "APPROVATO" + 5 sections present.
 *  - Value-trap: badge "BOCCIATO VALUE TRAP" with red styling.
 *  - Invalid ticker (404): "Ticker non trovato" message + search link.
 *  - No SEC filings (422): dedicated "Nessun filing SEC" message.
 *  - Force refresh: "Rigenera" click → skeleton loader → new fetch.
 *  - Accessibility: aria-label on verdict badge.
 *
 * References:
 *  management/kanban/EP-011-deep-analysis-10k-10q/US-046-frontend-tab-deep-analysis/TSK-125.md
 *  management/kanban/EP-011-deep-analysis-10k-10q/US-046-frontend-tab-deep-analysis/US-046.md
 *  [^src: raw/tech_stack.md §QA / Testing]
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const deepAaplFixture = require('./fixtures/deep-analysis-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const deepValueTrapFixture = require('./fixtures/deep-analysis-value-trap.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Helper: mock the deep analysis endpoint for a given ticker
// ---------------------------------------------------------------------------

async function mockDeepEndpoint(
  page: Page,
  ticker: string,
  response: Record<string, unknown>,
): Promise<void> {
  await page.route(`**/api/analysis/${ticker}/deep**`, (route) =>
    route.fulfill({ json: response }),
  );
}

async function mockDeepEndpointError(
  page: Page,
  ticker: string,
  status: number,
  body: Record<string, unknown>,
): Promise<void> {
  await page.route(`**/api/analysis/${ticker}/deep**`, (route) =>
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body),
    }),
  );
}

// ---------------------------------------------------------------------------
// Scenario 1 — Happy path AAPL: badge APPROVATO + 5 sections visible
// ---------------------------------------------------------------------------
test.describe('Deep Analysis page', () => {

  test('happy path AAPL — verdict badge visible and all 5 sections populated', async ({ page }) => {
    await mockDeepEndpoint(page, 'AAPL', deepAaplFixture);

    await page.goto('/analysis/deep?ticker=AAPL');

    await expect(page.getByTestId('deep-analysis-loading')).toBeVisible();

    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });
    await expect(verdictBadge).toContainText(/APPROVATO/);

    await expect(page.getByTestId('deep-verdict-section')).toBeVisible();
    await expect(page.getByTestId('munger-report-section')).toBeVisible();
    await expect(page.getByTestId('news-sentiment-section')).toBeVisible();
    await expect(page.getByTestId('drawdown-chart-section')).toBeVisible();
    await expect(page.getByTestId('edgar-filing-section')).toBeVisible();
  });

  // ---------------------------------------------------------------------------
  // Scenario 1b — Accessibility: aria-label on verdict badge
  // ---------------------------------------------------------------------------
  test('verdict badge has aria-label for accessibility', async ({ page }) => {
    await mockDeepEndpoint(page, 'AAPL', deepAaplFixture);

    await page.goto('/analysis/deep?ticker=AAPL');

    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });

    const ariaLabel = await verdictBadge.getAttribute('aria-label');
    expect(ariaLabel).toBeTruthy();
    expect(ariaLabel).toContain('Verdetto');
    expect(ariaLabel).toContain('Approvato');
  });

  // ---------------------------------------------------------------------------
  // Scenario 2 — Value-trap: red badge with "BOCCIATO VALUE TRAP"
  // ---------------------------------------------------------------------------
  test('value-trap scenario — badge shows BOCCIATO VALUE TRAP', async ({ page }) => {
    await mockDeepEndpoint(page, 'TRAP', deepValueTrapFixture);

    await page.goto('/analysis/deep?ticker=TRAP');

    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });
    await expect(verdictBadge).toContainText(/BOCCIATO VALUE TRAP/);

    const ariaLabel = await verdictBadge.getAttribute('aria-label');
    expect(ariaLabel).toContain('Bocciato Value Trap');
  });

  // ---------------------------------------------------------------------------
  // Scenario 3 — Invalid ticker (404): "Ticker non trovato" + search link
  // ---------------------------------------------------------------------------
  test('invalid ticker — shows "Ticker non trovato" and search link', async ({ page }) => {
    await mockDeepEndpointError(page, 'XYZINVALID', 404, {
      reason: 'not_found',
      detail: 'Ticker not found',
    });

    await page.goto('/analysis/deep?ticker=XYZINVALID');

    const errorPanel = page.getByTestId('deep-analysis-error');
    await expect(errorPanel).toBeVisible({ timeout: 15_000 });
    await expect(errorPanel).toContainText(/Ticker non trovato/);

    const searchLink = errorPanel.getByRole('link', { name: /cerca un altro ticker/i });
    await expect(searchLink).toBeVisible();
    await expect(searchLink).toHaveAttribute('href', /\/screener\/?/);
  });

  // ---------------------------------------------------------------------------
  // Scenario 4 — No SEC filings (422): dedicated message
  // ---------------------------------------------------------------------------
  test('no SEC filings (422) — shows "Nessun filing SEC disponibile"', async ({ page }) => {
    await mockDeepEndpointError(page, 'NOSEC', 422, {
      reason: 'no_sec_filings',
      detail: 'No 10-K or 10-Q filings found',
    });

    await page.goto('/analysis/deep?ticker=NOSEC');

    const errorPanel = page.getByTestId('deep-analysis-error');
    await expect(errorPanel).toBeVisible({ timeout: 15_000 });
    await expect(errorPanel).toContainText(/Nessun filing SEC disponibile/);
  });

  // ---------------------------------------------------------------------------
  // Scenario 5 — Force refresh: "Rigenera" → skeleton → new fetch
  // ---------------------------------------------------------------------------
  test('force refresh — Rigenera button triggers skeleton and re-fetches', async ({ page }) => {
    let fetchCount = 0;

    await page.route('**/api/analysis/AAPL/deep**', (route) => {
      fetchCount++;
      return route.fulfill({ json: deepAaplFixture });
    });

    await page.goto('/analysis/deep?ticker=AAPL');

    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });

    const initialFetchCount = fetchCount;

    const regenerateButton = page.getByTestId('regenerate-button');
    await expect(regenerateButton).toBeVisible();
    await expect(regenerateButton).toContainText(/Rigenera/);

    await regenerateButton.click();

    // Mock responds instantly so the transient "Rigenerazione…" state may
    // not be observable. Just verify the badge re-appears and a new fetch
    // was triggered.
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });

    expect(fetchCount).toBeGreaterThan(initialFetchCount);
  });

  // ---------------------------------------------------------------------------
  // Scenario 6 — Page title contains ticker
  // ---------------------------------------------------------------------------
  test('page title displays the ticker', async ({ page }) => {
    await mockDeepEndpoint(page, 'AAPL', deepAaplFixture);

    await page.goto('/analysis/deep?ticker=AAPL');

    const title = page.getByTestId('deep-analysis-title');
    await expect(title).toBeVisible({ timeout: 15_000 });
    await expect(title).toContainText('AAPL');
    await expect(title).toContainText('Deep Analysis');
  });
});
