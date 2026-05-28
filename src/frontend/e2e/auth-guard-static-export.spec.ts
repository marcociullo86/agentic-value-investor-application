/**
 * E2E suite — AuthGuard static-export (TSK-269 / US-087 / ADR-026).
 *
 * Validates ClientAuthGuard flows in a production-like environment:
 * Next.js static bundle served by a plain HTTP file server with NO
 * Next.js Edge/Node middleware running.
 *
 * Run configuration
 * -----------------
 * Use playwright.config.static.ts, NOT the default config:
 *   npm run build        # generate out/
 *   npx playwright test --config playwright.config.static.ts
 *
 * Environment
 * -----------
 * The webServer in playwright.config.static.ts starts `static-test-server.js`
 * serving out/ on port 4000.  No Next.js middleware runs.  Any redirect
 * observed from a protected route is definitively produced by ClientAuthGuard
 * executing in the browser — the sole auth actor in static-export production
 * (ADR-026 §Decisione).
 *
 * API mock strategy
 * -----------------
 * All /api/* requests are intercepted via page.route() — no real backend required.
 *
 * POST /api/auth/refresh response drives rehydration outcome:
 *   HTTP 500 → unauthenticated (catch-block: rehydrationStatus:'done', no token)
 *   HTTP 401 → session-expired  (interceptor: clearSession + setSessionExpired)
 *   HTTP 200 → authenticated    (store: accessToken set, user stays null → forbidden)
 *
 * No middleware cookie workaround needed (Finding 2 fix, TSK-269-iter-1)
 * -----------------------------------------------------------------------
 * The `setAuthenticatedCookie` workaround (needed in `next dev` to bypass the
 * dev-mode middleware before the page loads) is absent here.  The static file
 * server has no middleware: every test loads the HTML directly and lets
 * ClientAuthGuard perform its own evaluation without any server-side
 * auth pre-filter.
 *
 * Coverage
 * --------
 *   AC#1  /analysis, /analysis/deep?ticker=AAPL, /watchlist, /top-picks
 *         → unauthenticated → /login?returnUrl=<route>
 *   AC#2  /login?returnUrl=/analysis → login succeeds → redirect to /analysis
 *   AC#3  /admin authenticated (no admin role) → /403
 *   AC#4  /watchlist + /analysis with session-expired rehydration → /login?expired=true
 *   AC#5  Login page shows session-expired banner when navigated to with ?expired=true
 *
 * [^src: management/kanban/EP-017-protezione-rotte-sessione/US-087-authguard-client-side-static-export/TSK-269.md §Technical Specs]
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md §Decisione]
 */

import { expect, test, type Page } from '@playwright/test';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const MOCK_ACCESS_TOKEN = 'mock.jwt.token.header.payload.sig';
const STRONG_PASSWORD = 'e2e-guard-static-export-password-12345';

// ---------------------------------------------------------------------------
// Route-mock helpers
// ---------------------------------------------------------------------------

/**
 * Intercept all unhandled /api/* requests with a 200 empty JSON to prevent
 * network errors from secondary page-content API calls (e.g. /api/analysis)
 * from interfering with the URL assertions we care about.
 * Must be registered FIRST; specific mocks registered after override it (LIFO).
 */
async function mockUnhandledApis(page: Page): Promise<void> {
  await page.route('**/api/**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({}),
    }),
  );
}

/**
 * Mock POST /api/auth/refresh to return HTTP 500 (no session, no expiry flag).
 * The axios interceptor in client.ts only fires for 401; a 500 falls through
 * so the rehydrate() catch-block sets `rehydrationStatus: 'done'` and leaves
 * `sessionExpired` untouched (false). Guard evaluates to `unauthenticated`.
 */
async function mockRefreshNoSession(page: Page): Promise<void> {
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({ status: 500, body: 'Internal Server Error' }),
  );
}

/**
 * Mock POST /api/auth/refresh to return HTTP 401 (session expired).
 * The axios interceptor in client.ts calls clearSession() + setSessionExpired(true)
 * when the refresh endpoint returns 401, matching the production token-expiry path.
 * Guard evaluates to `session-expired`.
 */
async function mockRefreshExpired(page: Page): Promise<void> {
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Refresh token expired' }),
    }),
  );
}

/**
 * Mock POST /api/auth/refresh to return a valid access token (HTTP 200).
 * The store sets `accessToken` + `rehydrationStatus: 'done'`.
 * `user` stays null (rehydrate() only stores accessToken, not user profile),
 * so role-protected routes (e.g. /admin) evaluate as `forbidden`.
 */
async function mockRefreshSuccess(page: Page): Promise<void> {
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: MOCK_ACCESS_TOKEN,
        expiresInSeconds: 900,
      }),
    }),
  );
}

/**
 * Mock POST /api/auth/login to return a valid access token (HTTP 200).
 * The login page calls router.push(returnUrl) after a successful login.
 */
async function mockLoginSuccess(page: Page): Promise<void> {
  await page.route('**/api/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: MOCK_ACCESS_TOKEN,
        expiresInSeconds: 900,
        mfaRequired: false,
      }),
    }),
  );
}

// ---------------------------------------------------------------------------
// URL helpers
// ---------------------------------------------------------------------------

/** Extract and decode the `returnUrl` query parameter from a /login URL. */
function parseReturnUrl(rawUrl: string): string | null {
  try {
    return new URL(rawUrl).searchParams.get('returnUrl');
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

test.describe('AuthGuard — static-export (TSK-269, US-087)', () => {
  test.beforeEach(async ({ context }) => {
    // Start each test with a clean cookie jar to prevent state leakage.
    await context.clearCookies();
  });

  // -------------------------------------------------------------------------
  // AC#1 — Unauthenticated user on a protected route → /login?returnUrl=<route>
  //
  // No cookie setup needed — static file server has no middleware.
  // Mock refresh to 500 so rehydration ends unauthenticated.  ClientAuthGuard
  // handles the redirect entirely client-side.
  // -------------------------------------------------------------------------
  test.describe('AC#1 — unauth on protected route → /login?returnUrl', () => {
    const cases: Array<{ path: string; expectedReturnUrlContains: string }> = [
      { path: '/analysis',                  expectedReturnUrlContains: '/analysis' },
      { path: '/analysis/deep?ticker=AAPL', expectedReturnUrlContains: '/analysis/deep' },
      { path: '/watchlist',                 expectedReturnUrlContains: '/watchlist' },
      { path: '/top-picks',                 expectedReturnUrlContains: '/top-picks' },
    ];

    for (const { path, expectedReturnUrlContains } of cases) {
      test(`redirect to /login?returnUrl for ${path}`, async ({ page }) => {
        await mockUnhandledApis(page);
        await mockRefreshNoSession(page);

        await page.goto(path);

        // ClientAuthGuard calls router.replace('/login?returnUrl=...') once
        // rehydrationStatus becomes 'done' with no accessToken.
        await page.waitForURL(/\/login/, { waitUntil: 'commit', timeout: 12_000 });

        const returnUrl = parseReturnUrl(page.url());
        expect(
          returnUrl,
          `returnUrl must contain "${expectedReturnUrlContains}" for path "${path}"`,
        ).toContain(expectedReturnUrlContains);

        // Login form should be rendered (not an error/blank page).
        await expect(page.getByTestId('login-email')).toBeVisible({ timeout: 5_000 });
      });
    }
  });

  // -------------------------------------------------------------------------
  // AC#2 — Post-login redirect uses returnUrl
  // -------------------------------------------------------------------------
  test.describe('AC#2 — post-login redirect uses returnUrl', () => {
    test('/login?returnUrl=%2Fanalysis redirects to /analysis after successful login', async ({
      page,
    }) => {
      // No session initially — refresh fails (500); guard does NOT fire on the
      // login page (public route), so this just prevents background noise.
      await mockUnhandledApis(page);
      await mockRefreshNoSession(page);
      await mockLoginSuccess(page);

      // Navigate directly to /login with a returnUrl.
      // waitUntil:'commit' resolves before React hydrates; the login form
      // renders after rehydration completes (spinner → form), so we allow
      // a generous 10s for the element to appear.
      await page.goto('/login?returnUrl=%2Fanalysis', { waitUntil: 'commit' });
      await expect(page.getByTestId('login-email')).toBeVisible({ timeout: 10_000 });

      const email = `e2e-guard-${Date.now()}@example.com`;
      await page.getByTestId('login-email').fill(email);
      await page.getByTestId('login-password').fill(STRONG_PASSWORD);
      await page.getByTestId('login-submit').click();

      // Login stores accessToken in Zustand + calls router.push(returnUrl='/analysis').
      await page.waitForURL(/\/analysis/, { waitUntil: 'commit', timeout: 12_000 });

      expect(page.url()).toContain('/analysis');
      expect(page.url()).not.toContain('/login');
    });
  });

  // -------------------------------------------------------------------------
  // AC#3 — Forbidden: authenticated user without admin role on /admin → /403
  //
  // No cookie setup needed — static file server has no middleware.
  // Mock refresh to 200 so the client has an accessToken but user=null
  // (rehydrate() only stores the token, not the user profile).
  // Route /admin requires roles:["admin"]; null role → forbidden.
  // -------------------------------------------------------------------------
  test.describe('AC#3 — forbidden user on role-protected route → /403', () => {
    test('/admin with authenticated non-admin user redirects to /403', async ({
      page,
    }) => {
      await mockUnhandledApis(page);
      await mockRefreshSuccess(page);

      await page.goto('/admin');

      // ClientAuthGuard is the sole redirect actor (no middleware in static export).
      await page.waitForURL(/\/403/, { waitUntil: 'commit', timeout: 12_000 });
      expect(page.url()).toContain('/403');
    });
  });

  // -------------------------------------------------------------------------
  // AC#4 — Session expired during rehydration → /login?expired=true&returnUrl=…
  //
  // No cookie setup needed — static file server has no middleware.
  // Mock refresh to 401 → axios interceptor calls clearSession() +
  // setSessionExpired(true) → client guard detects session-expired and
  // redirects with expired=true flag.
  // -------------------------------------------------------------------------
  test.describe('AC#4 — session expired → /login?expired=true', () => {
    test('/watchlist with expired session → /login?expired=true with returnUrl', async ({
      page,
    }) => {
      await mockUnhandledApis(page);
      await mockRefreshExpired(page);

      await page.goto('/watchlist');

      // ClientAuthGuard: session-expired decision → router.replace('/login?expired=true&returnUrl=...')
      await page.waitForURL(/\/login/, { waitUntil: 'commit', timeout: 12_000 });

      const finalUrl = new URL(page.url());
      expect(
        finalUrl.searchParams.get('expired'),
        'expired=true must be present',
      ).toBe('true');
      expect(
        finalUrl.searchParams.get('returnUrl'),
        'returnUrl must reference /watchlist',
      ).toContain('/watchlist');

      // Login page must render the session-expired alert banner.
      await expect(page.getByTestId('session-expired-alert')).toBeVisible({
        timeout: 5_000,
      });
    });

    test('/analysis with expired session → /login?expired=true', async ({
      page,
    }) => {
      await mockUnhandledApis(page);
      await mockRefreshExpired(page);

      await page.goto('/analysis');

      await page.waitForURL(/\/login/, { waitUntil: 'commit', timeout: 12_000 });

      const finalUrl = new URL(page.url());
      expect(finalUrl.searchParams.get('expired')).toBe('true');
      expect(finalUrl.searchParams.get('returnUrl')).toContain('/analysis');
    });
  });

  // -------------------------------------------------------------------------
  // AC#5 — Login page shows session-expired banner (direct navigation)
  //
  // Validates the login page's ?expired=true → visible alert contract,
  // decoupled from the guard redirect that sets the URL parameter.
  // -------------------------------------------------------------------------
  test.describe('AC#5 — login page session-expired banner', () => {
    test('shows session-expired alert when accessed with ?expired=true', async ({
      page,
    }) => {
      await mockUnhandledApis(page);
      await mockRefreshNoSession(page);

      await page.goto('/login?expired=true');

      await expect(page.getByTestId('session-expired-alert')).toBeVisible({
        timeout: 5_000,
      });
    });

    test('does NOT show session-expired alert on plain /login', async ({
      page,
    }) => {
      await mockUnhandledApis(page);
      await mockRefreshNoSession(page);

      await page.goto('/login');

      await expect(page.getByTestId('session-expired-alert')).not.toBeVisible();
    });
  });

  // -------------------------------------------------------------------------
  // ClientAuthGuard-only redirect — no middleware present in static export
  //
  // Verifies that the redirect from /top-picks to /login was produced by
  // ClientAuthGuard (client-side router.replace) and NOT by a server-side
  // HTTP 302 (as would happen with active Next.js middleware).
  //
  // Distinguishing mechanism (Finding 3 fix, TSK-269-iter-1)
  // ---------------------------------------------------------
  // With `next dev` + active middleware: GET /top-picks returns a server-side
  // HTTP 302 to /login?returnUrl=...; the HTML page never loads.
  //
  // With a static file server (no middleware): GET /top-picks returns HTTP 200
  // (or 301 → trailing-slash redirect) serving the HTML page.  Only AFTER the
  // HTML is committed and JavaScript runs does ClientAuthGuard call router.replace.
  //
  // `page.goto(path, { waitUntil: 'commit' })` resolves when the first HTTP
  // response is committed — BEFORE JavaScript executes.  At that point:
  //   • Static server:       page.url() is /top-picks (or /top-picks/ after 301)
  //   • next dev middleware: page.url() is already /login?returnUrl=...
  // The assertion `not.toContain('/login')` cleanly distinguishes the two.
  // -------------------------------------------------------------------------
  test.describe('ClientAuthGuard-only redirect — no middleware in static export', () => {
    test('ClientAuthGuard redirects /top-picks unauthenticated user — static export', async ({
      page,
    }) => {
      await mockUnhandledApis(page);
      await mockRefreshNoSession(page);

      // waitUntil:'commit' resolves when the HTTP response is committed and the
      // document starts loading — before any JavaScript has executed.
      await page.goto('/top-picks', { waitUntil: 'commit' });
      const urlAfterServerResponse = page.url();

      // In static export: URL is /top-picks (or /top-picks/ after trailing-slash
      // redirect) — the static HTML was served and JS hasn't run yet.
      // In next dev with middleware: URL would already be /login?returnUrl=...
      // (server-side 302).  The assertion below would fail in that case, correctly
      // surfacing a middleware dependency that violates the DoD contract.
      expect(
        urlAfterServerResponse,
        'static file server must serve /top-picks before ClientAuthGuard redirects — URL must not be /login yet',
      ).not.toContain('/login');
      expect(urlAfterServerResponse).toContain('top-picks');

      // Now wait for ClientAuthGuard to perform its client-side redirect.
      await page.waitForURL(/\/login/, { waitUntil: 'commit', timeout: 12_000 });
      expect(page.url()).toContain('/login');

      const returnUrl = parseReturnUrl(page.url());
      expect(returnUrl, 'ClientAuthGuard must include /top-picks in returnUrl').toContain('/top-picks');
    });
  });
});
