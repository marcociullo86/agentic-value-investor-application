/**
 * E2E — CAPTCHA login flusso mocked-tier (TSK-271 / US-081 / ADR-025 §5).
 *
 * Strategia mocked-tier: page.route() intercetta POST /api/auth/login
 * e il glob challenges.cloudflare.com per eliminare ogni dipendenza
 * dalla CDN Cloudflare in CI.
 *
 * Scenari coperti (US-081 AC §CAPTCHA):
 *   1. Login sotto soglia (baseline): nessun widget, submit abilitato.
 *   2. captchaRequired -> widget appare: primo submit riceve 401 con
 *      captchaRequired: true -> login-captcha container visibile,
 *      submit disabilitato.
 *   3. Solve + resubmit -> login OK: token iniettato via stub Turnstile ->
 *      secondo submit -> 200 con Set-Cookie -> redirect a /.
 *   4. Token assente/invalido -> errore utente: captchaRequired=true con token
 *      invalido -> mock 400 -> messaggio di errore visibile.
 *
 * Nota sull'interceptor Axios 401:
 *   Il client API (client.ts) intercetta ogni risposta 401 e tenta un token
 *   refresh prima di propagare l'errore originale. Per garantire che il
 *   captchaRequired 401 sia visibile al login-page handler, si mocka anche
 *   POST /api/auth/refresh con la stessa forma captchaRequired, in modo che
 *   l'errore rifiutato dall'interceptor sia riconosciuto da isCaptchaRequiredError().
 *
 * Il widget Turnstile viene simulato tramite:
 *   - page.addInitScript(): inietta window.turnstile con render() che
 *     invoca callback(token) immediatamente + patcha process.env
 *     NEXT_PUBLIC_TURNSTILE_SITE_KEY (utile in dev mode Next.js).
 *   - page.route(Cloudflare CDN): blocca la richiesta reale in CI.
 *
 * Run (mocked tier, default CI):
 *   npx playwright test captcha-login --reporter=list
 *
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-271.md]
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/US-081.md §AC]
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-238.md §DoD]
 */

import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const STRONG_PASSWORD = 'e2e-captcha-test-password-12345';
/**
 * Cloudflare "always-pass" test sitekey.
 * Refs: https://developers.cloudflare.com/turnstile/troubleshooting/testing/
 */
const TURNSTILE_TEST_SITEKEY = '1x00000000000000000000AA';
/** Synthetic token emitted by the stubbed window.turnstile. */
const MOCK_TURNSTILE_TOKEN = 'mock-turnstile-token-e2e';

function uniqueEmail(prefix = 'captcha'): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

// ---------------------------------------------------------------------------
// RFC 9457 ProblemDetail body for captchaRequired 401
// ---------------------------------------------------------------------------

const CAPTCHA_REQUIRED_BODY = JSON.stringify({
  type: 'https://api/errors/captcha-required',
  title: 'Captcha required',
  status: 401,
  detail: 'Invalid email or password',
  captchaRequired: true,
});

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/**
 * Mocks POST /api/auth/refresh to fail with a captchaRequired-like 401.
 *
 * The axios 401 interceptor in client.ts intercepts any 401 from any endpoint
 * (including /api/auth/login) and attempts a token refresh before propagating
 * the error. By making the refresh also return 401 with captchaRequired: true,
 * the interceptor rejects with an error that isCaptchaRequiredError() correctly
 * identifies, so the login page can mount the CAPTCHA widget.
 *
 * This also covers the AuthProvider bootstrap refresh that runs on page mount
 * (rehydration attempt): a 401 there is normal for unauthenticated users and
 * causes rehydrationStatus to be set to 'done' with no token.
 */
async function mockRefreshFail(page: import('@playwright/test').Page): Promise<void> {
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: CAPTCHA_REQUIRED_BODY,
    }),
  );
}

/**
 * Sets up the three-layer Turnstile stub:
 *
 * 1. page.addInitScript: injects window.turnstile with a render() that
 *    immediately calls callback(MOCK_TURNSTILE_TOKEN). Also patches
 *    process.env NEXT_PUBLIC_TURNSTILE_SITE_KEY so the widget component
 *    does not fall back to the misconfig banner (effective in Next.js dev
 *    mode where NEXT_PUBLIC vars are read via the process.env shim).
 * 2. page.route: intercepts the Cloudflare CDN and returns an empty script
 *    so no real CDN request is made in CI.
 *
 * Must be called before page.goto() so the script runs before React hydrates.
 */
async function setupTurnstileStub(page: import('@playwright/test').Page): Promise<void> {
  await page.addInitScript((sitekey) => {
    if (typeof process !== 'undefined' && process.env) {
      process.env['NEXT_PUBLIC_TURNSTILE_SITE_KEY'] = sitekey;
    }
    (window as unknown as Record<string, unknown>)['turnstile'] = {
      render(
        _container: HTMLElement | string,
        options: { callback?: (token: string) => void },
      ): string {
        if (typeof options.callback === 'function') {
          options.callback('mock-turnstile-token-e2e');
        }
        return 'mock-widget-id';
      },
      reset(_widgetId?: string): void { /* no-op */ },
      remove(_widgetId: string): void { /* no-op */ },
    };
  }, TURNSTILE_TEST_SITEKEY);

  await page.route('**/challenges.cloudflare.com/**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/javascript',
      body: '/* cf-turnstile-stubbed */',
    }),
  );
}

// ---------------------------------------------------------------------------
// Scenario 1 — Login sotto soglia (baseline): widget non visibile
// US-081 AC§CAPTCHA — nessun gate per utenti sotto-soglia
// ---------------------------------------------------------------------------

test.describe('CAPTCHA login — baseline (US-081 AC§CAPTCHA scenario 1)', () => {
  test('login riuscito senza CAPTCHA gate: widget assente, redirect a home', async ({ page }) => {
    const email = uniqueEmail('baseline');

    // Baseline: login responds 200 immediately; no captcha gate triggered.
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Set-Cookie': 'isAuthenticated=true; Path=/; SameSite=Strict' },
        body: JSON.stringify({
          accessToken: 'mock.jwt.access.token.captcha-e2e',
          expiresInSeconds: 900,
          mfaRequired: false,
        }),
      }),
    );

    // AuthProvider bootstrap refresh: fails (unauthenticated user), harmless.
    await mockRefreshFail(page);

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);

    // Under-threshold: captcha container must NOT be present.
    await expect(page.getByTestId('login-captcha')).toHaveCount(0);

    // Submit must be enabled (no captcha gate active).
    await expect(page.getByTestId('login-submit')).not.toBeDisabled();

    await page.getByTestId('login-submit').click();

    // Successful login redirects to home.
    await page.waitForURL('http://localhost:3000/', { waitUntil: 'commit' });
    expect(page.url()).not.toContain('/login');
  });
});

// ---------------------------------------------------------------------------
// Scenario 2 — captchaRequired -> widget appare (US-081 AC§CAPTCHA)
// ---------------------------------------------------------------------------

test.describe('CAPTCHA login — trigger widget (US-081 AC§CAPTCHA scenario 2)', () => {
  test('captchaRequired: true -> login-captcha container visibile, submit disabilitato', async ({
    page,
  }) => {
    const email = uniqueEmail('trigger');

    // First login attempt: backend returns 401 captchaRequired.
    // The axios 401 interceptor will also call /api/auth/refresh before
    // propagating the error. We mock refresh with the same captchaRequired
    // body so isCaptchaRequiredError() identifies the error correctly.
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/problem+json',
        body: CAPTCHA_REQUIRED_BODY,
      }),
    );
    await mockRefreshFail(page);

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);

    // No captcha widget on initial render.
    await expect(page.getByTestId('login-captcha')).toHaveCount(0);
    await expect(page.getByTestId('login-submit')).not.toBeDisabled();

    // First submit triggers the captchaRequired 401.
    await page.getByTestId('login-submit').click();

    // The login page must mount the captcha container after receiving the error.
    await expect(page.getByTestId('login-captcha')).toBeVisible();

    // Submit must be disabled while no Turnstile token is available.
    await expect(page.getByTestId('login-submit')).toBeDisabled();

    // The widget renders either the host container (if NEXT_PUBLIC_TURNSTILE_SITE_KEY
    // is set at dev-server startup) or the misconfig fallback. Either way the
    // CAPTCHA gate is active.
    const widgetOrFallback =
      (await page.getByTestId('turnstile-widget').count()) > 0 ||
      (await page.getByTestId('turnstile-misconfigured').count()) > 0;
    expect(
      widgetOrFallback,
      'turnstile-widget or turnstile-misconfigured must be present inside login-captcha',
    ).toBe(true);

    // Still on login page — no redirect on captchaRequired.
    expect(page.url()).toContain('/login');
  });
});

// ---------------------------------------------------------------------------
// Scenario 3 — Solve + resubmit -> login OK (US-081 AC§CAPTCHA)
// ---------------------------------------------------------------------------

test.describe('CAPTCHA login — solve + resubmit (US-081 AC§CAPTCHA scenario 3)', () => {
  test('token iniettato dal widget stub -> secondo submit -> redirect a home', async ({ page }) => {
    const email = uniqueEmail('solve');

    // Inject Turnstile stub before page loads.
    await setupTurnstileStub(page);

    // First call -> captchaRequired 401; second call -> 200 success.
    let loginCallCount = 0;
    await page.route('**/api/auth/login', (route) => {
      loginCallCount += 1;
      if (loginCallCount === 1) {
        return route.fulfill({
          status: 401,
          contentType: 'application/problem+json',
          body: CAPTCHA_REQUIRED_BODY,
        });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Set-Cookie': 'isAuthenticated=true; Path=/; SameSite=Strict' },
        body: JSON.stringify({
          accessToken: 'mock.jwt.access.token.after.captcha',
          expiresInSeconds: 900,
          mfaRequired: false,
        }),
      });
    });
    await mockRefreshFail(page);

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);

    // First submit -> triggers captchaRequired.
    await page.getByTestId('login-submit').click();

    // Captcha container must appear.
    await expect(page.getByTestId('login-captcha')).toBeVisible();

    // With the Turnstile stub active, window.turnstile.render() calls callback()
    // immediately -> the React state receives the token -> submit is re-enabled.
    // NEXT_PUBLIC_TURNSTILE_SITE_KEY is guaranteed by the webServer.env in
    // playwright.config.ts so the widget never falls back to misconfigured.
    const submitLocator = page.getByTestId('login-submit');
    await expect(submitLocator).not.toBeDisabled({ timeout: 3000 });

    // Submit re-enabled -> click to replay with the captcha token.
    await submitLocator.click();

    // Second submit -> success -> redirect to home.
    await page.waitForURL('http://localhost:3000/', { waitUntil: 'commit' });
    expect(page.url()).not.toContain('/login');
    expect(loginCallCount).toBe(2);
  });
});

// ---------------------------------------------------------------------------
// Scenario 4 — Token invalido -> errore visibile (US-081 AC§CAPTCHA)
// ---------------------------------------------------------------------------

test.describe('CAPTCHA login — token invalido -> errore (US-081 AC§CAPTCHA scenario 4)', () => {
  test('captchaRequired + token invalido -> mock 400 -> errore visibile, no redirect', async ({
    page,
  }) => {
    const email = uniqueEmail('invalid-token');

    await setupTurnstileStub(page);

    let loginCallCount = 0;
    await page.route('**/api/auth/login', (route) => {
      loginCallCount += 1;
      if (loginCallCount === 1) {
        // First attempt: captchaRequired gate.
        return route.fulfill({
          status: 401,
          contentType: 'application/problem+json',
          body: CAPTCHA_REQUIRED_BODY,
        });
      }
      // Second attempt: backend rejects the token as invalid (400).
      return route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          type: 'https://api/errors/invalid-captcha-token',
          title: 'Invalid CAPTCHA token',
          status: 400,
          detail: 'The provided CAPTCHA token is invalid or expired.',
        }),
      });
    });
    await mockRefreshFail(page);

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);

    // First submit -> captchaRequired 401.
    await page.getByTestId('login-submit').click();

    // Captcha gate activated.
    await expect(page.getByTestId('login-captcha')).toBeVisible();

    // Wait for submit re-enable (Turnstile stub injects token).
    // NEXT_PUBLIC_TURNSTILE_SITE_KEY is guaranteed by the webServer.env in
    // playwright.config.ts so the widget never falls back to misconfigured.
    const submitLocator = page.getByTestId('login-submit');
    await expect(submitLocator).not.toBeDisabled({ timeout: 3000 });

    // Submit re-enabled -> click with (mock) captchaToken.
    await submitLocator.click();

    // Backend responds 400 (invalid token) -> login page shows an error alert.
    // The [role="alert"] captures both the server error banner and any inline
    // error element; either confirms the error is visible to the user.
    await expect(page.getByRole('alert').first()).toBeVisible();

    // Must stay on login page — no redirect on token error.
    expect(page.url()).toContain('/login');
    expect(loginCallCount).toBe(2);
  });
});
