import { expect, test } from '@playwright/test';

/**
 * E2E suite — auth + watchlist flow (TSK-036).
 *
 * Covers the 5 scenarios from the TSK spec:
 *  1. Register a new user → auto-login → land on home.
 *  2. From the watchlist page, add AAPL → AAPL appears in the table.
 *  3. Remove AAPL → it disappears.
 *  4. Click on the ticker → navigates to /analysis/AAPL.
 *  5. Direct access to /watchlist without login → redirected to /login.
 *
 * Brief deviation: the spec asks to add from the analysis dashboard. The
 * dashboard belongs to Track A (TSK-021) and is not yet on master at Track B
 * branch time. The watchlist page mounts an inline add-by-ticker form
 * (TSK-035) so the flow is exercised end-to-end without that dependency.
 *
 * Reference: management/kanban/EP-006-watchlist-utente/US-017-gestione-watchlist/TSK-036.md.
 */

const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080';

function uniqueEmail(prefix = 'e2e'): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

const STRONG_PASSWORD = 'e2e-test-password-12345';

async function registerViaApi(
  request: import('@playwright/test').APIRequestContext,
  email: string,
): Promise<void> {
  const response = await request.post(`${API_BASE}/api/auth/register`, {
    data: { email, password: STRONG_PASSWORD, displayName: null },
  });
  expect(response.status(), 'register API call').toBe(201);
}

test.describe('auth + watchlist', () => {
  test('1. registrazione + auto-login redirige alla home', async ({ page }) => {
    const email = uniqueEmail('reg');
    await page.goto('/register');
    await page.getByTestId('register-email').fill(email);
    await page.getByTestId('register-password').fill(STRONG_PASSWORD);
    await page.getByTestId('register-displayname').fill('E2E User');
    await page.getByTestId('register-submit').click();

    await page.waitForURL('**/');
    await expect(page.getByTestId('nav-user-email')).toBeVisible();
    await expect(page.getByTestId('nav-logout')).toBeVisible();
  });

  test('2. login + aggiunta ticker AAPL → appare in /watchlist', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail('add');
    await registerViaApi(request, email);

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();
    await page.waitForURL('**/');

    // SPA nav via Navbar link — preserves the in-memory Zustand access token.
    // `page.goto('/watchlist')` is a full reload that clears Zustand state,
    // making AuthGuard kick in and redirect to /login. No explicit
    // waitForURL: the default waitUntil:'load' never fires on Next.js soft
    // navigation; the subsequent getByTestId(...).fill auto-waits anyway.
    await page.getByTestId('nav-watchlist').click();
    await page.getByTestId('watchlist-add-input').fill('AAPL');
    await page.getByTestId('watchlist-add-submit').click();

    await expect(page.getByTestId('watchlist-row-AAPL')).toBeVisible();
  });

  test('3. rimozione di AAPL dalla watchlist', async ({ page, request }) => {
    const email = uniqueEmail('rm');
    await registerViaApi(request, email);

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();
    await page.waitForURL('**/');

    // SPA nav via Navbar link — preserves the in-memory Zustand access token.
    // `page.goto('/watchlist')` is a full reload that clears Zustand state,
    // making AuthGuard kick in and redirect to /login. No explicit
    // waitForURL: the default waitUntil:'load' never fires on Next.js soft
    // navigation; the subsequent getByTestId(...).fill auto-waits anyway.
    await page.getByTestId('nav-watchlist').click();
    await page.getByTestId('watchlist-add-input').fill('AAPL');
    await page.getByTestId('watchlist-add-submit').click();
    await expect(page.getByTestId('watchlist-row-AAPL')).toBeVisible();

    await page.getByTestId('watchlist-remove-AAPL').click();
    await expect(page.getByTestId('watchlist-row-AAPL')).toHaveCount(0);
    await expect(page.getByTestId('watchlist-empty')).toBeVisible();
  });

  test('4. click sul ticker nella watchlist apre /analysis/{ticker}', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail('nav');
    await registerViaApi(request, email);

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();
    await page.waitForURL('**/');

    // SPA nav via Navbar link — preserves the in-memory Zustand access token.
    // `page.goto('/watchlist')` is a full reload that clears Zustand state,
    // making AuthGuard kick in and redirect to /login. No explicit
    // waitForURL: the default waitUntil:'load' never fires on Next.js soft
    // navigation; the subsequent getByTestId(...).fill auto-waits anyway.
    await page.getByTestId('nav-watchlist').click();
    await page.getByTestId('watchlist-add-input').fill('MSFT');
    await page.getByTestId('watchlist-add-submit').click();
    await expect(page.getByTestId('watchlist-row-MSFT')).toBeVisible();

    await page.getByTestId('watchlist-link-MSFT').click();
    // commit waitUntil: SPA navigation in Next.js doesn't fire the `load`
    // event, so the default waitUntil:'load' here would time out even
    // though the URL has already changed.
    await page.waitForURL(/\/analysis\/MSFT/, { waitUntil: 'commit' });
    expect(page.url()).toContain('/analysis/MSFT');
  });

  test('5. /watchlist senza login redirige a /login', async ({ page }) => {
    await page.goto('/watchlist');
    // AuthGuard performs router.replace('/login') from a useEffect, which is
    // a Next.js soft navigation — same waitUntil:'commit' rationale as above.
    await page.waitForURL(/\/login/, { waitUntil: 'commit' });
    expect(page.url()).toContain('/login');
  });
});
