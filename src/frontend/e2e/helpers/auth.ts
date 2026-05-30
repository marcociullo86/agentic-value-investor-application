import type { Page } from '@playwright/test';

/**
 * Seed an authenticated session for fully-mocked E2E specs.
 *
 * Post EP-017 (TSK-211/266/267) protected routes (`/analysis`,
 * `/analysis/deep`, `/top-picks`, …) are gated by TWO layers in `next dev`
 * (the mocked job's webServer):
 *
 *  1. `proxy.ts` (server-side, dev-only) reads the non-httpOnly
 *     `isAuthenticated` cookie and redirects to `/login` when absent.
 *  2. `ClientAuthGuard` (client-side) reads the in-memory auth store, which
 *     is populated on bootstrap by `POST /api/auth/refresh`.
 *
 * The fully-mocked specs start no backend, so both layers must be satisfied
 * synthetically: set the cookie AND mock the refresh endpoint to return a
 * token. Without this every protected page redirects to `/login` and the
 * content never renders (toBeVisible timeouts).
 *
 * Call in a `beforeEach` BEFORE `page.goto(...)` for any spec landing on a
 * protected route.
 */
export async function mockAuthSession(page: Page): Promise<void> {
  // Layer 1 — dev proxy gate (cookie hint).
  await page.context().addCookies([
    {
      name: 'isAuthenticated',
      value: 'true',
      url: 'http://localhost:3000',
    },
  ]);

  // Layer 2 — client AuthGuard (rehydrate via refresh).
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'e2e-access-token',
        expiresInSeconds: 3600,
      }),
    }),
  );
}
