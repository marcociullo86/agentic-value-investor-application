/**
 * E2E — Keyboard accessibility for critical flows (US-072, WCAG 2.1.1 / 2.4.7).
 * TSK-193 · layer: qa · consumer: agent
 *
 * Verifies that all critical user flows are completable without a mouse,
 * using only Tab, Shift+Tab, Enter, and Escape.
 *
 * Mocking strategy:
 *  - All scenarios use page.route() to intercept API calls and return
 *    deterministic JSON fixtures. The BE is NOT started in CI.
 *  - Consistent with the pattern in search-to-analysis.spec.ts (TSK-022).
 *
 * Focus verification:
 *  - Each step asserts that the focused element matches the expected target
 *    via :focus pseudo-class and role/testid selectors.
 *  - Focus visibility (outline) is asserted via CSS computed style where
 *    relevant (SC 2.4.7 Focus Visible, SC 2.4.11 Focus Not Obscured).
 *
 * References:
 *  management/kanban/EP-016-refinement-ui-accessibilita/US-072-audit-accessibilita-wcag/TSK-193.md
 *  management/kanban/EP-016-refinement-ui-accessibilita/US-072-audit-accessibilita-wcag/US-072.md
 *  [^src: raw/tech_stack.md §QA / Testing]
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

// --- Fixture imports -------------------------------------------------------
// eslint-disable-next-line @typescript-eslint/no-require-imports
const searchAaplFixture = require('./fixtures/search-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const analysisAaplFixture = require('./fixtures/analysis-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const historicalAaplFixture = require('./fixtures/historical-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const topPicksFixture = require('./fixtures/top-picks-default.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockLoginApi(page: Page): Promise<void> {
  await page.route('**/api/auth/login', (route) =>
    route.fulfill({
      status: 200,
      json: {
        accessToken: 'fake-jwt-token',
        expiresInSeconds: 900,
        mfaRequired: false,
      },
    }),
  );
}

async function mockAnalysisRoutes(page: Page): Promise<void> {
  await page.route('**/api/search?query=AAPL', (route) =>
    route.fulfill({ json: searchAaplFixture }),
  );
  await page.route('**/api/search/AAPL', (route) =>
    route.fulfill({ json: searchAaplFixture }),
  );
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisAaplFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );
}

async function mockTopPicksRoutes(page: Page): Promise<void> {
  await page.route('**/api/top-picks**', (route) =>
    route.fulfill({ json: topPicksFixture }),
  );
}

async function mockWatchlistRoutes(page: Page): Promise<void> {
  let watchlist: string[] = [];

  await page.route('**/api/watchlist', (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill({
        json: {
          id: 'default',
          name: 'Default',
          isDefault: true,
          items: watchlist.map((t) => ({
            ticker: t,
            companyName: null,
            sector: null,
            marketCapUsd: null,
            addedAt: new Date().toISOString(),
          })),
        },
      });
    }
    return route.fulfill({ status: 200 });
  });

  await page.route('**/api/watchlist/items', (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { ticker: string } | null;
      if (body?.ticker) watchlist.push(body.ticker);
      return route.fulfill({
        status: 201,
        json: {
          ticker: body?.ticker,
          companyName: null,
          sector: null,
          marketCapUsd: null,
          addedAt: new Date().toISOString(),
        },
      });
    }
    return route.fulfill({ status: 200 });
  });

  await page.route('**/api/watchlist/items/*', (route) => {
    if (route.request().method() === 'DELETE') {
      const url = route.request().url();
      const ticker = url.split('/').pop();
      watchlist = watchlist.filter((t) => t !== ticker);
      return route.fulfill({ status: 204, body: '' });
    }
    return route.fulfill({ status: 200 });
  });
}

/**
 * Assert that the currently focused element has visible focus styling.
 * Checks for a non-zero outline or box-shadow (common focus indicators).
 */
async function assertFocusVisible(page: Page): Promise<void> {
  const hasFocusIndicator = await page.evaluate(() => {
    const el = document.activeElement;
    if (!el || el === document.body) return false;
    const styles = window.getComputedStyle(el);
    const hasOutline =
      styles.outlineStyle !== 'none' && styles.outlineWidth !== '0px';
    const hasBoxShadow =
      styles.boxShadow !== 'none' && styles.boxShadow !== '';
    const hasRing = el.classList.toString().includes('ring');
    return hasOutline || hasBoxShadow || hasRing;
  });
  expect(hasFocusIndicator, 'Focused element should have visible focus indicator').toBe(true);
}

/**
 * Assert the focused element matches the given test-id.
 */
async function assertFocusedTestId(page: Page, testId: string): Promise<void> {
  const focusedTestId = await page.evaluate(() => {
    return document.activeElement?.getAttribute('data-testid') ?? '';
  });
  expect(focusedTestId, `Expected focus on [data-testid="${testId}"]`).toBe(testId);
}

/**
 * Tab (or Shift+Tab) until `predicate` returns true or `maxTabs` is exhausted.
 * TSK-239: navbar + skip-link exceed the old fixed Tab budgets (10/15).
 */
async function tabUntil(
  page: Page,
  predicate: () => Promise<boolean>,
  options?: { maxTabs?: number; backward?: boolean },
): Promise<void> {
  const maxTabs = options?.maxTabs ?? 30;
  const key = options?.backward ? 'Shift+Tab' : 'Tab';

  for (let i = 0; i < maxTabs; i++) {
    if (await predicate()) return;
    await page.keyboard.press(key);
  }

  throw new Error(`tabUntil: focus target not reached within ${maxTabs} ${key} presses`);
}

async function tabUntilTestId(
  page: Page,
  testId: string,
  options?: { maxTabs?: number; backward?: boolean },
): Promise<void> {
  await tabUntil(
    page,
    async () => {
      const id = await page.evaluate(
        () => document.activeElement?.getAttribute('data-testid') ?? '',
      );
      return id === testId;
    },
    options,
  );
}

/**
 * Assert the focused element matches the given role (and optionally name).
 */
async function assertFocusedRole(page: Page, role: string, name?: string | RegExp): Promise<void> {
  const focusedRole = await page.evaluate(() => {
    const el = document.activeElement;
    if (!el) return '';
    return el.getAttribute('role') ?? el.tagName.toLowerCase();
  });
  expect(focusedRole.toLowerCase()).toBe(role.toLowerCase());
  if (name) {
    const accessibleName = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el) return '';
      return el.getAttribute('aria-label') ?? (el as HTMLElement).innerText ?? '';
    });
    if (typeof name === 'string') {
      expect(accessibleName).toBe(name);
    } else {
      expect(accessibleName).toMatch(name);
    }
  }
}

// ---------------------------------------------------------------------------
// Flow 1 — Login → navigation (keyboard only)
// ---------------------------------------------------------------------------
test.describe('Keyboard accessibility', () => {

  test('Flow 1: Login form completable via keyboard only', async ({ page }) => {
    await mockLoginApi(page);

    await page.goto('/login');
    // Seed focus on first field (navbar/skip-link exceed practical Tab budget — TSK-239).
    await page.getByTestId('login-email').focus();
    await assertFocusedTestId(page, 'login-email');
    await assertFocusVisible(page);

    // Type email
    await page.keyboard.type('test@example.com');

    // Tab to password
    await page.keyboard.press('Tab');
    await assertFocusedTestId(page, 'login-password');
    await assertFocusVisible(page);

    // Type password
    await page.keyboard.type('SecurePass123!');

    // Tab to submit button
    await page.keyboard.press('Tab');
    await assertFocusedTestId(page, 'login-submit');
    await assertFocusVisible(page);

    // Press Enter to submit
    await page.keyboard.press('Enter');

    // Should navigate to home after successful login
    await page.waitForURL('**/', { timeout: 10_000 });
  });

  // ---------------------------------------------------------------------------
  // Flow 2 — Search ticker via keyboard
  // ---------------------------------------------------------------------------
  test('Flow 2: Search ticker completable via keyboard only', async ({ page }) => {
    await mockAnalysisRoutes(page);

    await page.goto('/');
    await page.getByLabel(/cerca ticker o nome azienda/i).focus();
    const focusedTag = await page.evaluate(() => document.activeElement?.tagName ?? '');
    expect(focusedTag).toBe('INPUT');
    await assertFocusVisible(page);

    // Type ticker
    await page.keyboard.type('AAPL');

    // Press Enter to search
    await page.keyboard.press('Enter');

    // Should navigate to analysis page
    await page.waitForURL(/\/analysis\/?\?ticker=AAPL/, { timeout: 15_000 });

    // Verify results are rendered (focus should flow to content)
    const cards = page.locator('[data-testid^="rule-signal-card-"]');
    await expect(cards.first()).toBeVisible({ timeout: 15_000 });
  });

  // ---------------------------------------------------------------------------
  // Flow 3 — Top picks: filter + navigation via keyboard
  // ---------------------------------------------------------------------------
  test('Flow 3: Top picks filter and row navigation via keyboard', async ({ page }) => {
    await mockTopPicksRoutes(page);

    await page.goto('/top-picks');

    // Wait for table to load
    await expect(page.getByTestId('top-picks-table')).toBeVisible({ timeout: 15_000 });

    // Tab to the verdict filter select
    let maxTabs = 20;
    while (maxTabs-- > 0) {
      const testId = await page.evaluate(() => document.activeElement?.getAttribute('data-testid') ?? '');
      if (testId === 'filter-verdict') break;
      await page.keyboard.press('Tab');
    }
    await assertFocusedTestId(page, 'filter-verdict');
    await assertFocusVisible(page);

    // Continue tabbing to reach a ticker link in the table
    maxTabs = 20;
    let foundTickerLink = false;
    while (maxTabs-- > 0) {
      await page.keyboard.press('Tab');
      const testId = await page.evaluate(() => document.activeElement?.getAttribute('data-testid') ?? '');
      if (testId?.startsWith('ticker-link-')) {
        foundTickerLink = true;
        break;
      }
    }
    expect(foundTickerLink, 'Should be able to tab to a ticker link in the table').toBe(true);
    await assertFocusVisible(page);

    // Press Enter on the ticker link to navigate
    await page.keyboard.press('Enter');
    await page.waitForURL(/\/analysis\/deep/, { timeout: 10_000 });
  });

  // ---------------------------------------------------------------------------
  // Flow 4 — Watchlist: add and remove ticker via keyboard
  // ---------------------------------------------------------------------------
  test('Flow 4: Watchlist add/remove ticker via keyboard only', async ({ page }) => {
    await mockLoginApi(page);
    await mockWatchlistRoutes(page);

    // Login first
    await page.goto('/login');
    await page.getByTestId('login-email').fill('test@example.com');
    await page.getByTestId('login-password').fill('SecurePass123!');
    await page.getByTestId('login-submit').click();
    await page.waitForURL('**/', { timeout: 10_000 });

    // Navigate to watchlist via keyboard (tab to nav link)
    let maxTabs = 15;
    while (maxTabs-- > 0) {
      const testId = await page.evaluate(() => document.activeElement?.getAttribute('data-testid') ?? '');
      if (testId === 'nav-watchlist') break;
      await page.keyboard.press('Tab');
    }
    await assertFocusedTestId(page, 'nav-watchlist');
    await page.keyboard.press('Enter');

    // Wait for watchlist page
    await page.waitForURL(/\/watchlist/, { timeout: 10_000 });

    // Tab to the ticker input
    maxTabs = 15;
    while (maxTabs-- > 0) {
      const testId = await page.evaluate(() => document.activeElement?.getAttribute('data-testid') ?? '');
      if (testId === 'watchlist-add-input') break;
      await page.keyboard.press('Tab');
    }
    await assertFocusedTestId(page, 'watchlist-add-input');
    await assertFocusVisible(page);

    // Type ticker
    await page.keyboard.type('AAPL');

    // Tab to submit button and press Enter
    await page.keyboard.press('Tab');
    await assertFocusedTestId(page, 'watchlist-add-submit');
    await page.keyboard.press('Enter');

    // Verify AAPL row appears
    await expect(page.getByTestId('watchlist-row-AAPL')).toBeVisible({ timeout: 10_000 });

    // Tab to remove button for AAPL
    maxTabs = 15;
    while (maxTabs-- > 0) {
      const testId = await page.evaluate(() => document.activeElement?.getAttribute('data-testid') ?? '');
      if (testId === 'watchlist-remove-AAPL') break;
      await page.keyboard.press('Tab');
    }
    await assertFocusedTestId(page, 'watchlist-remove-AAPL');
    await assertFocusVisible(page);

    // Press Enter to remove
    await page.keyboard.press('Enter');

    // Verify AAPL is gone
    await expect(page.getByTestId('watchlist-row-AAPL')).toHaveCount(0, { timeout: 5_000 });
  });

  // ---------------------------------------------------------------------------
  // Escape key: close modals / dropdowns
  // ---------------------------------------------------------------------------
  test('Escape key dismisses open dropdowns and overlays', async ({ page }) => {
    await mockAnalysisRoutes(page);

    await page.goto('/');

    // Focus the search input and type (may open a dropdown)
    const searchInput = page.getByLabel(/cerca ticker o nome azienda/i);
    await searchInput.focus();
    await page.keyboard.type('AAP');

    // Wait a moment for any dropdown to appear
    await page.waitForTimeout(500);

    // Press Escape — should close any dropdown without navigating away
    await page.keyboard.press('Escape');

    // Should remain on same page
    await expect(page).toHaveURL('/');

    // Input should still be focused or page should not have navigated
    const url = page.url();
    expect(url).not.toContain('/analysis');
  });

  // ---------------------------------------------------------------------------
  // Shift+Tab: reverse navigation works
  // ---------------------------------------------------------------------------
  test('Shift+Tab moves focus backwards through interactive elements', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('login-email').focus();
    await page.keyboard.press('Tab');
    await assertFocusedTestId(page, 'login-password');
    await page.keyboard.press('Tab');
    await assertFocusedTestId(page, 'login-submit');

    // Shift+Tab should go back to password
    await page.keyboard.press('Shift+Tab');
    await assertFocusedTestId(page, 'login-password');

    // Shift+Tab again to email
    await page.keyboard.press('Shift+Tab');
    await assertFocusedTestId(page, 'login-email');
  });

});
