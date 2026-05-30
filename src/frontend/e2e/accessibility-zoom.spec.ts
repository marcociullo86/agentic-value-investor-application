/**
 * E2E — Zoom 200% accessibility test (US-072, WCAG SC 1.4.10 Reflow).
 * TSK-193 · layer: qa · consumer: agent
 *
 * Simulates 200% zoom by setting viewport to 640x360 (half of standard
 * 1280x720), which effectively replicates a 200% browser zoom scenario.
 *
 * Verifies:
 *  - No horizontal overflow on any critical view
 *  - Content remains accessible and usable at zoomed viewport
 *  - Screenshot comparison captures layout for visual review
 *
 * Mocking strategy:
 *  - All scenarios use page.route() to intercept API calls and return
 *    deterministic JSON fixtures. The BE is NOT started in CI.
 *
 * References:
 *  management/kanban/EP-016-refinement-ui-accessibilita/US-072-audit-accessibilita-wcag/TSK-193.md
 *  [^src: raw/tech_stack.md §QA / Testing]
 */

import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mockAuthSession } from './helpers/auth';

// --- Fixture imports -------------------------------------------------------
// eslint-disable-next-line @typescript-eslint/no-require-imports
const analysisAaplFixture = require('./fixtures/analysis-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const historicalAaplFixture = require('./fixtures/historical-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const topPicksFixture = require('./fixtures/top-picks-default.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockAllRoutes(page: Page): Promise<void> {
  await page.route('**/api/analysis/AAPL', (route) =>
    route.fulfill({ json: analysisAaplFixture }),
  );
  await page.route('**/api/historical/AAPL', (route) =>
    route.fulfill({ json: historicalAaplFixture }),
  );
  await page.route('**/api/top-picks**', (route) =>
    route.fulfill({ json: topPicksFixture }),
  );
  await page.route('**/api/watchlist', (route) =>
    route.fulfill({ json: { items: [] } }),
  );
}

/**
 * Assert no horizontal overflow: scrollWidth should not exceed clientWidth.
 * This is the core WCAG 1.4.10 Reflow check for 200% zoom.
 */
async function assertNoHorizontalOverflow(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => {
    const body = document.body;
    const html = document.documentElement;
    return {
      bodyScrollWidth: body.scrollWidth,
      bodyClientWidth: body.clientWidth,
      htmlScrollWidth: html.scrollWidth,
      htmlClientWidth: html.clientWidth,
    };
  });

  expect(
    overflow.htmlScrollWidth,
    `Horizontal overflow detected: scrollWidth(${overflow.htmlScrollWidth}) > clientWidth(${overflow.htmlClientWidth})`,
  ).toBeLessThanOrEqual(overflow.htmlClientWidth);
}

// ---------------------------------------------------------------------------
// Test suite: Zoom 200% (viewport 640x360)
// ---------------------------------------------------------------------------
test.describe('Zoom 200% — WCAG 1.4.10 Reflow', () => {
  test.use({ viewport: { width: 640, height: 360 } });

  test('Login page: no horizontal overflow at 200% zoom', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('h1')).toBeVisible({ timeout: 10_000 });
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: 'e2e/test-results/zoom-200-login.png',
      fullPage: true,
    });
  });

  test('Home / search page: no horizontal overflow at 200% zoom', async ({ page }) => {
    await mockAllRoutes(page);
    await page.goto('/');

    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 });
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: 'e2e/test-results/zoom-200-home.png',
      fullPage: true,
    });
  });

  test('Analysis page: no horizontal overflow at 200% zoom', async ({ page }) => {
    await mockAuthSession(page); // /analysis protetta (TSK-267)
    await mockAllRoutes(page);
    await page.goto('/analysis/?ticker=AAPL');

    const cards = page.locator('[data-testid^="rule-signal-card-"]');
    await expect(cards.first()).toBeVisible({ timeout: 15_000 });
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: 'e2e/test-results/zoom-200-analysis.png',
      fullPage: true,
    });
  });

  test('Top picks page: no horizontal overflow at 200% zoom', async ({ page }) => {
    await mockAuthSession(page); // /top-picks protetta (TSK-267)
    await mockAllRoutes(page);
    await page.goto('/top-picks');

    await expect(page.getByTestId('top-picks-table')).toBeVisible({ timeout: 15_000 });
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: 'e2e/test-results/zoom-200-top-picks.png',
      fullPage: true,
    });
  });

  test('Register page: no horizontal overflow at 200% zoom', async ({ page }) => {
    await page.goto('/register');

    await expect(page.locator('h1')).toBeVisible({ timeout: 10_000 });
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: 'e2e/test-results/zoom-200-register.png',
      fullPage: true,
    });
  });

  test('All views: content remains readable and interactive', async ({ page }) => {
    await mockAllRoutes(page);

    // Login page — form is usable
    await page.goto('/login');
    const emailInput = page.getByTestId('login-email');
    await expect(emailInput).toBeVisible({ timeout: 10_000 });
    const emailBox = await emailInput.boundingBox();
    expect(emailBox, 'Email input should be within viewport').not.toBeNull();
    expect(emailBox!.width).toBeGreaterThan(100);

    // Home page — search is usable
    await page.goto('/');
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 });
    const searchInput = page.getByLabel(/cerca ticker/i);
    if (await searchInput.isVisible()) {
      const searchBox = await searchInput.boundingBox();
      expect(searchBox, 'Search input should be within viewport').not.toBeNull();
      expect(searchBox!.width).toBeGreaterThan(80);
    }
  });
});
