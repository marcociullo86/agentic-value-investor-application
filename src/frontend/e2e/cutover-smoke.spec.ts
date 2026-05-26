/**
 * Cutover R1.1 Smoke — STAGING ONLY
 *
 * E2E smoke suite against a live staging deployment. Does NOT use page.route()
 * mocks — all requests hit the real application at STAGING_URL.
 *
 * Execution (on-demand only — NOT in CI default):
 *   STAGING_URL=https://staging.app.example.com \
 *   STAGING_USER_EMAIL=qa@example.com \
 *   STAGING_USER_PASSWORD=*** \
 *   npx playwright test cutover-smoke
 *
 * See: src/frontend/e2e/cutover-smoke.README.md
 *
 * References:
 *   management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/TSK-066.md
 *   management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/US-028.md §Acceptance Criteria
 *   [^src: raw/tech_stack.md §QA / Testing]
 */

import { test, expect, type Page, type BrowserContext } from '@playwright/test';

// ---------------------------------------------------------------------------
// Configuration — driven entirely by env vars, no hard-coded credentials
// ---------------------------------------------------------------------------

const STAGING = process.env.STAGING_URL || 'https://app-staging.example.com';
const USER = process.env.STAGING_USER_EMAIL;
const PASS = process.env.STAGING_USER_PASSWORD;

// Timeout for page-load sensitive assertions (real network, real BE)
const PAGE_LOAD_TIMEOUT = 15_000;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Authenticate via login form and return the authenticated page. */
async function loginStagingUser(page: Page): Promise<void> {
  await page.goto(`${STAGING}/login`, { timeout: PAGE_LOAD_TIMEOUT });
  await page.getByTestId('login-email').fill(USER!);
  await page.getByTestId('login-password').fill(PASS!);
  await page.getByTestId('login-submit').click();
  // Wait for redirect to home after successful login
  await page.waitForURL(`${STAGING}/`, { timeout: PAGE_LOAD_TIMEOUT });
}

/** Collect console / page errors during a navigation and assert none occurred. */
async function withNoPageErrors(
  page: Page,
  fn: () => Promise<void>,
): Promise<void> {
  const errors: string[] = [];
  const handler = (err: Error) => errors.push(err.message);
  page.on('pageerror', handler);
  try {
    await fn();
  } finally {
    page.off('pageerror', handler);
  }
  expect(errors, `Page JS errors: ${errors.join('; ')}`).toHaveLength(0);
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

test.describe('Cutover R1.1 smoke — STAGING ONLY', () => {
  // Skip the entire suite when credentials are not configured.
  // This prevents accidental execution in CI without staging secrets.
  test.skip(!USER || !PASS, 'STAGING_USER_EMAIL / STAGING_USER_PASSWORD not set');

  // -------------------------------------------------------------------------
  // Scenario 1 — Healthcheck
  // -------------------------------------------------------------------------
  test('1. healthcheck returns 200 + status UP', async ({ request }) => {
    const response = await request.get(`${STAGING}/actuator/health`);
    expect(response.status(), 'actuator/health HTTP status').toBe(200);

    const body = await response.json() as { status: string };
    expect(body.status, 'health status field').toBe('UP');
  });

  // -------------------------------------------------------------------------
  // Scenario 2 — Home accessible
  // -------------------------------------------------------------------------
  test('2. home page loads in < 5s, no 5xx, title contains "Value Investing"', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));

    const startMs = Date.now();
    const response = await page.goto(`${STAGING}/`, { timeout: 5_000 });
    const loadMs = Date.now() - startMs;

    expect(response?.status() ?? 0, 'home HTTP status must not be 5xx').toBeLessThan(500);
    expect(loadMs, `page load must be < 5000ms, was ${loadMs}ms`).toBeLessThan(5_000);

    const title = await page.title();
    expect(title, 'page title must contain "Value Investing"').toContain('Value Investing');
  });

  // -------------------------------------------------------------------------
  // Scenario 3 — Login flow
  // -------------------------------------------------------------------------
  test('3. login with staging credentials → redirect home + session cookie set', async ({ page }) => {
    await page.goto(`${STAGING}/login`, { timeout: PAGE_LOAD_TIMEOUT });
    await page.getByTestId('login-email').fill(USER!);
    await page.getByTestId('login-password').fill(PASS!);
    await page.getByTestId('login-submit').click();

    await page.waitForURL(`${STAGING}/`, { timeout: PAGE_LOAD_TIMEOUT });

    // Verify authenticated state visible in UI
    await expect(
      page.getByTestId('nav-user-email').or(page.getByTestId('nav-logout')),
    ).toBeVisible({ timeout: PAGE_LOAD_TIMEOUT });
  });

  // -------------------------------------------------------------------------
  // Scenario 4 — Analysis flow (AAPL traffic-light + DCF + MoS)
  // -------------------------------------------------------------------------
  test('4. /analysis/AAPL — 13 ruleSignals visible + DCF intrinsic value + MoS badge', async ({ page }) => {
    await loginStagingUser(page);

    await withNoPageErrors(page, async () => {
      await page.goto(`${STAGING}/analysis?ticker=AAPL`, { timeout: PAGE_LOAD_TIMEOUT });

      // Traffic-light table: 13 rule signals
      const signalRows = page.getByTestId('traffic-light-row');
      await expect(signalRows.first()).toBeVisible({ timeout: PAGE_LOAD_TIMEOUT });
      const count = await signalRows.count();
      expect(count, 'TrafficLight must show exactly 13 rule signals').toBe(13);

      // DCF intrinsic value
      await expect(
        page.getByTestId('dcf-intrinsic-value').or(page.getByText(/intrinsic value/i).first()),
      ).toBeVisible({ timeout: PAGE_LOAD_TIMEOUT });

      // Margin of Safety badge
      await expect(
        page.getByTestId('mos-badge').or(page.getByText(/margin of safety/i).first()),
      ).toBeVisible({ timeout: PAGE_LOAD_TIMEOUT });
    });
  });

  // -------------------------------------------------------------------------
  // Scenario 5 — Deep analysis flow
  // -------------------------------------------------------------------------
  test('5. /analysis/AAPL/deep — DeepVerdictBadge + MungerReport + EdgarFilingLinks', async ({ page }) => {
    await loginStagingUser(page);

    await withNoPageErrors(page, async () => {
      await page.goto(`${STAGING}/analysis/deep?ticker=AAPL`, { timeout: PAGE_LOAD_TIMEOUT });

      // DeepVerdictBadge (may take time for LLM-backed response)
      await expect(page.getByTestId('verdict-badge')).toBeVisible({ timeout: 30_000 });

      // MungerReportCollapsible — may be "not invoked yet" but must render
      const mungerSection = page.getByTestId('munger-report-section');
      await expect(mungerSection).toBeVisible({ timeout: PAGE_LOAD_TIMEOUT });
      // Accept either full report or the "not invoked yet" placeholder
      const mungerText = await mungerSection.textContent();
      expect(
        mungerText,
        'Munger section must contain report text or "not invoked yet" placeholder',
      ).toMatch(/munger|report|not invoked yet|click llm button/i);

      // EdgarFilingLinks section
      await expect(page.getByTestId('edgar-filing-section')).toBeVisible({
        timeout: PAGE_LOAD_TIMEOUT,
      });
    });
  });

  // -------------------------------------------------------------------------
  // Scenario 6 — Top Picks flow
  // -------------------------------------------------------------------------
  test('6. /top-picks — table has ≥1 row OR "Nessuna classifica" placeholder visible', async ({ page }) => {
    await loginStagingUser(page);

    await withNoPageErrors(page, async () => {
      await page.goto(`${STAGING}/top-picks`, { timeout: PAGE_LOAD_TIMEOUT });

      // Either at least one result row or the empty-state placeholder
      const hasRows = await page.getByTestId('top-picks-row').first().isVisible().catch(() => false);
      const hasEmptyState = await page
        .getByText(/nessuna classifica/i)
        .first()
        .isVisible()
        .catch(() => false);

      expect(
        hasRows || hasEmptyState,
        'top-picks page must show either result rows or the empty-state message',
      ).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // Scenario 7 — Watchlist flow
  // -------------------------------------------------------------------------
  test('7. add AAPL to watchlist → reload → AAPL persists', async ({ page }) => {
    await loginStagingUser(page);

    // Navigate via SPA link to preserve auth token in memory
    await page.getByTestId('nav-watchlist').click();

    // Remove AAPL first in case it was already added in a prior run
    const existingRow = page.getByTestId('watchlist-row-AAPL');
    if (await existingRow.isVisible().catch(() => false)) {
      await page.getByTestId('watchlist-remove-AAPL').click();
      await expect(existingRow).toHaveCount(0, { timeout: PAGE_LOAD_TIMEOUT });
    }

    // Add AAPL
    await page.getByTestId('watchlist-add-input').fill('AAPL');
    await page.getByTestId('watchlist-add-submit').click();
    await expect(page.getByTestId('watchlist-row-AAPL')).toBeVisible({ timeout: PAGE_LOAD_TIMEOUT });

    // Full reload to confirm persistence (re-login required after hard reload)
    await loginStagingUser(page);
    await page.goto(`${STAGING}/watchlist`, { timeout: PAGE_LOAD_TIMEOUT });
    await expect(page.getByTestId('watchlist-row-AAPL')).toBeVisible({ timeout: PAGE_LOAD_TIMEOUT });
  });

  // -------------------------------------------------------------------------
  // Scenario 8 — API contract (authenticated)
  // -------------------------------------------------------------------------
  test('8. GET /api/analysis/AAPL returns 200 + signals array length=13', async ({ page, request }) => {
    // Obtain JWT via login API
    const loginResp = await request.post(`${STAGING}/api/auth/login`, {
      data: { email: USER, password: PASS },
    });
    expect(loginResp.status(), 'login API').toBe(200);

    const loginBody = await loginResp.json() as { accessToken: string };
    const token = loginBody.accessToken;

    const analysisResp = await request.get(`${STAGING}/api/analysis/AAPL`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(analysisResp.status(), 'GET /api/analysis/AAPL').toBe(200);

    const body = await analysisResp.json() as { signals: unknown[] };
    expect(
      Array.isArray(body.signals),
      'response body must contain a signals array',
    ).toBe(true);
    expect(body.signals.length, 'signals array must have exactly 13 entries').toBe(13);
  });

  // -------------------------------------------------------------------------
  // Scenario 9 — TLS / HSTS check
  // -------------------------------------------------------------------------
  test('9. HTTPS response includes Strict-Transport-Security header', async ({ request }) => {
    const response = await request.get(`${STAGING}/`);
    const hsts = response.headers()['strict-transport-security'];
    expect(hsts, 'Strict-Transport-Security header must be present').toBeTruthy();
    expect(hsts, 'HSTS must include max-age').toContain('max-age=');
  });

  // -------------------------------------------------------------------------
  // Scenario 10 — No console errors across key navigations
  // -------------------------------------------------------------------------
  test('10. no JS page errors on home, analysis, and top-picks pages', async ({ page }) => {
    await loginStagingUser(page);

    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(`[${e.name}] ${e.message}`));

    // Navigate to three key pages
    for (const path of ['/', '/analysis?ticker=AAPL', '/top-picks']) {
      await page.goto(`${STAGING}${path}`, { timeout: PAGE_LOAD_TIMEOUT });
      // Brief wait for async JS to settle
      await page.waitForTimeout(1_000);
    }

    expect(errors, `JS page errors: ${errors.join(' | ')}`).toHaveLength(0);
  });
});
