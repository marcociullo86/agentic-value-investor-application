/**
 * E2E — CSP + CSRF smoke (US-080, TSK-224).
 *
 * Two tiers of verification:
 *
 *  Tier 1 — Mocked (default playwright.config.ts):
 *    Verifies the login flow completes without CSP-blocked script errors.
 *    Uses page.route() to intercept API calls; no real BE required.
 *
 *  Tier 2 — Real-BE (playwright.config.realbe.ts, opt-in):
 *    Verifies CSP header is present on /api/auth/login and /api/auth/register
 *    responses, and that POST /api/auth/refresh without CSRF token → 403.
 *    Tests skip automatically when E2E_API_BASE_URL backend is unreachable.
 *
 * Run mocked tier (default CI):
 *   npx playwright test auth-csp-csrf
 *
 * Run real-BE tier (requires BE on E2E_API_BASE_URL=http://localhost:8080):
 *   E2E_API_BASE_URL=http://localhost:8080 npx playwright test auth-csp-csrf
 *
 * FE dangerouslySetInnerHTML audit (US-080 AC — escape by default):
 *   Grep of src/frontend/ for dangerouslySetInnerHTML returned 0 matches.
 *   React's default JSX escaping covers all user-originated string rendering.
 *   No raw HTML injection sites found. Audit date: 2026-05-28.
 *
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/US-080.md §AC]
 */

import { test, expect } from '@playwright/test';

const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080';
const STRONG_PASSWORD = 'e2e-csp-test-password-12345';

function uniqueEmail(prefix = 'csp'): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

// ---------------------------------------------------------------------------
// Tier 1 — Mocked: login flow does not produce CSP-blocked script errors
// ---------------------------------------------------------------------------

test.describe('CSP regression — login flow (mocked, US-080 AC#5)', () => {
  test('login completes without CSP-blocked script errors', async ({ page }) => {
    const email = uniqueEmail('mocked');
    const pageErrors: string[] = [];

    page.on('pageerror', (err) => pageErrors.push(err.message));

    // Mock register (not strictly needed for login page, but avoids 401 noise)
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        headers: {
          'Content-Security-Policy':
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self'; font-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self'",
        },
        body: JSON.stringify({ id: 'mock-id-1', email, displayName: null }),
      }),
    );

    // Mock login — include CSP header as the real backend would
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: {
          'Content-Security-Policy':
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self'; font-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self'",
        },
        body: JSON.stringify({ accessToken: 'mock.jwt.token.header.payload.sig', expiresInSeconds: 900 }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    // Successful login redirects to home
    await page.waitForURL('**/', { waitUntil: 'commit' });

    // No CSP-related JS errors should have been thrown
    const cspErrors = pageErrors.filter((e) =>
      /refused to (execute|load|connect)|content security policy/i.test(e),
    );
    expect(
      cspErrors,
      `CSP-blocked script errors during login: ${cspErrors.join(' | ')}`,
    ).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// Tier 2 — Real-BE: API header + CSRF assertions (skip if BE unavailable)
// ---------------------------------------------------------------------------

/**
 * Checks that the backend is reachable by hitting /actuator/health.
 * Returns true when the backend responds with HTTP 200.
 */
async function isBackendReachable(
  request: import('@playwright/test').APIRequestContext,
): Promise<boolean> {
  try {
    const resp = await request.get(`${API_BASE}/actuator/health`, {
      failOnStatusCode: false,
      timeout: 5_000,
    });
    return resp.status() === 200;
  } catch {
    return false;
  }
}

test.describe('CSP header on API responses (real-BE, US-080 AC#1 + AC#6)', () => {
  test('POST /api/auth/register response includes Content-Security-Policy header', async ({
    request,
  }) => {
    const reachable = await isBackendReachable(request);
    test.skip(!reachable, `Backend at ${API_BASE} not reachable — skipping real-BE CSP test`);

    const email = uniqueEmail('csp-reg');
    const resp = await request.post(`${API_BASE}/api/auth/register`, {
      data: { email, password: STRONG_PASSWORD, displayName: null },
    });

    expect(resp.status(), 'register must return 201').toBe(201);

    const csp = resp.headers()['content-security-policy'];
    expect(csp, 'Content-Security-Policy header must be present on /api/auth/register').toBeTruthy();
    expect(csp, 'CSP must include default-src').toContain('default-src');
    expect(csp, 'CSP must restrict frame-src').toContain("frame-src 'none'");
    expect(csp, 'CSP must restrict object-src').toContain("object-src 'none'");
  });

  test('POST /api/auth/login response includes Content-Security-Policy header', async ({
    request,
  }) => {
    const reachable = await isBackendReachable(request);
    test.skip(!reachable, `Backend at ${API_BASE} not reachable — skipping real-BE CSP test`);

    const email = uniqueEmail('csp-login');

    // Register first
    await request.post(`${API_BASE}/api/auth/register`, {
      data: { email, password: STRONG_PASSWORD, displayName: null },
    });

    const resp = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });

    expect(resp.status(), 'login must return 200').toBe(200);

    const csp = resp.headers()['content-security-policy'];
    expect(csp, 'Content-Security-Policy header must be present on /api/auth/login').toBeTruthy();
    expect(csp, 'CSP must include default-src').toContain('default-src');
    expect(csp, 'CSP must restrict frame-src').toContain("frame-src 'none'");
  });
});

test.describe('CSRF protection on refresh (real-BE, US-080 AC#3)', () => {
  test('POST /api/auth/refresh without X-CSRF-Token returns 403', async ({ request }) => {
    const reachable = await isBackendReachable(request);
    test.skip(!reachable, `Backend at ${API_BASE} not reachable — skipping real-BE CSRF test`);

    const email = uniqueEmail('csrf-refresh');

    // Register + login to obtain a valid refresh_token cookie
    await request.post(`${API_BASE}/api/auth/register`, {
      data: { email, password: STRONG_PASSWORD, displayName: null },
    });

    const loginResp = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });
    expect(loginResp.status(), 'login must succeed before CSRF test').toBe(200);

    // Extract refresh_token from Set-Cookie
    const rawSetCookie = loginResp.headers()['set-cookie'] ?? '';
    const refreshTokenMatch = rawSetCookie.match(/refresh_token=([^;]+)/);
    expect(
      refreshTokenMatch,
      'refresh_token cookie must be present in login Set-Cookie',
    ).not.toBeNull();

    const refreshToken = refreshTokenMatch![1];

    // Attempt refresh WITHOUT X-CSRF-Token header — must be rejected with 403
    const refreshResp = await request.post(`${API_BASE}/api/auth/refresh`, {
      headers: { Cookie: `refresh_token=${refreshToken}` },
      failOnStatusCode: false,
    });

    expect(
      refreshResp.status(),
      'POST /api/auth/refresh without X-CSRF-Token must return 403',
    ).toBe(403);
  });
});
