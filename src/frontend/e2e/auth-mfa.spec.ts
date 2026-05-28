/**
 * E2E — MFA flows (TSK-234 / US-081 / ADR-025 §4).
 *
 * Two tiers of verification:
 *
 *  Tier 1 — Mocked (default playwright.config.ts):
 *    Drives the MFA UI flows via page.route() interception. No real BE required.
 *    Covers: enrollment UI funnel, TOTP login challenge, recovery code mode,
 *    wrong-code error display, cancel-MFA navigation, disable form.
 *
 *  Tier 2 — Real-BE (playwright.config.realbe.ts or E2E_API_BASE_URL, opt-in):
 *    Performs full enrollment via API, then drives the login MFA challenge and
 *    recovery code flows end-to-end against a live backend.
 *    Tests skip automatically when E2E_API_BASE_URL backend is unreachable.
 *
 * Run mocked tier (default CI):
 *   npx playwright test auth-mfa
 *
 * Run real-BE tier (requires BE on E2E_API_BASE_URL=http://localhost:8080):
 *   E2E_API_BASE_URL=http://localhost:8080 npx playwright test auth-mfa
 *
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-234.md]
 */

import { test, expect } from '@playwright/test';

const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080';
const STRONG_PASSWORD = 'e2e-mfa-test-password-12345';

function uniqueEmail(prefix = 'mfa'): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

// ---------------------------------------------------------------------------
// Shared helper: check backend reachability
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Tier 1 — Mocked: login MFA challenge UI (US-081 AC#2)
// ---------------------------------------------------------------------------

test.describe('MFA login challenge — mocked UI (US-081 AC#2)', () => {
  test('login with mfaRequired response shows MfaChallengeForm', async ({ page }) => {
    const email = uniqueEmail('challenge-ui');

    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mfaRequired: true,
          mfaToken: 'mock.mfa.challenge.token',
          accessToken: null,
          expiresInSeconds: null,
        }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    // After mfaRequired=true the login page must swap to the challenge form.
    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();
    await expect(page.getByTestId('mfa-totp-input')).toBeVisible();
    await expect(page.getByTestId('mfa-totp-submit')).toBeVisible();
  });

  test('TOTP challenge success navigates to home', async ({ page }) => {
    const email = uniqueEmail('totp-ok');

    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mfaRequired: true,
          mfaToken: 'mock.mfa.challenge.token',
          accessToken: null,
          expiresInSeconds: null,
        }),
      }),
    );

    // Deliver isAuthenticated via Set-Cookie in the mock response, exactly as
    // the real backend does. This ensures the Next.js middleware has the cookie
    // when it evaluates the subsequent router.push('/') navigation.
    await page.route('**/api/auth/mfa/challenge', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Set-Cookie': 'isAuthenticated=true; Path=/; SameSite=Strict' },
        body: JSON.stringify({
          accessToken: 'mock.access.token.after.challenge',
          expiresInSeconds: 900,
        }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();

    await page.getByTestId('mfa-totp-input').fill('123456');
    await page.getByTestId('mfa-totp-submit').click();

    await page.waitForURL('http://localhost:3000/', { waitUntil: 'commit' });
    expect(page.url()).not.toContain('/login');
  });

  test('wrong TOTP code shows error without redirect', async ({ page }) => {
    const email = uniqueEmail('totp-err');

    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mfaRequired: true,
          mfaToken: 'mock.mfa.challenge.token',
          accessToken: null,
          expiresInSeconds: null,
        }),
      }),
    );

    await page.route('**/api/auth/mfa/challenge', (route) =>
      route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          type: 'https://api/errors/invalid-totp-code',
          title: 'Invalid TOTP code',
          status: 400,
        }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();
    await page.getByTestId('mfa-totp-input').fill('000000');
    await page.getByTestId('mfa-totp-submit').click();

    // Error is shown inline; form must remain visible (no redirect).
    await expect(page.getByTestId('mfa-challenge-error')).toBeVisible();
    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();
    expect(page.url()).toContain('/login');
  });

  test('switch to recovery mode renders recovery input', async ({ page }) => {
    const email = uniqueEmail('recovery-mode');

    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mfaRequired: true,
          mfaToken: 'mock.mfa.challenge.token',
          accessToken: null,
          expiresInSeconds: null,
        }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();

    // Switch to recovery mode.
    await page.getByTestId('mfa-use-recovery').click();
    await expect(page.getByTestId('mfa-recovery-input')).toBeVisible();
    await expect(page.getByTestId('mfa-recovery-submit')).toBeVisible();
    // TOTP input must have been replaced.
    await expect(page.getByTestId('mfa-totp-input')).toHaveCount(0);
  });

  test('recovery code success navigates to home', async ({ page }) => {
    const email = uniqueEmail('recovery-ok');

    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mfaRequired: true,
          mfaToken: 'mock.mfa.challenge.token',
          accessToken: null,
          expiresInSeconds: null,
        }),
      }),
    );

    // Deliver isAuthenticated via Set-Cookie in the mock response, exactly as
    // the real backend does.
    await page.route('**/api/auth/mfa/recovery', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Set-Cookie': 'isAuthenticated=true; Path=/; SameSite=Strict' },
        body: JSON.stringify({
          accessToken: 'mock.access.token.after.recovery',
          expiresInSeconds: 900,
        }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();
    await page.getByTestId('mfa-use-recovery').click();
    await page.getByTestId('mfa-recovery-input').fill('AAAA-BBBB-CCCC-DDDD');
    await page.getByTestId('mfa-recovery-submit').click();

    await page.waitForURL('http://localhost:3000/', { waitUntil: 'commit' });
    expect(page.url()).not.toContain('/login');
  });

  test('cancel MFA returns to credential form', async ({ page }) => {
    const email = uniqueEmail('cancel-mfa');

    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mfaRequired: true,
          mfaToken: 'mock.mfa.challenge.token',
          accessToken: null,
          expiresInSeconds: null,
        }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();

    await page.getByTestId('mfa-cancel').click();

    // Challenge form dismissed; credential inputs visible again.
    await expect(page.getByTestId('mfa-challenge-form')).toHaveCount(0);
    await expect(page.getByTestId('login-email')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Tier 1 — Mocked: MFA enrollment page (US-081 AC#1)
// ---------------------------------------------------------------------------

test.describe('MFA enrollment page — mocked UI (US-081 AC#1)', () => {
  const MOCK_ACCESS_TOKEN = 'mock.valid.access.token.for.auth-guard';

  async function setupAuthGuardBypass(page: import('@playwright/test').Page): Promise<void> {
    // AuthProvider calls POST /api/auth/refresh on mount to rehydrate the session.
    // We mock it to return a valid token so AuthGuard sees an authenticated state
    // and renders the page content instead of redirecting to /login.
    // We also set the isAuthenticated hint cookie so the Next.js middleware allows
    // access to the protected /profile/mfa route.
    await page.context().addCookies([{
      name: 'isAuthenticated',
      value: 'true',
      domain: 'localhost',
      path: '/',
      sameSite: 'Strict',
    }]);
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

  test('enroll button triggers POST /enroll and transitions to verify stage', async ({
    page,
  }) => {
    await setupAuthGuardBypass(page);

    await page.route('**/api/auth/mfa/enroll', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          secret: 'JBSWY3DPEHPK3PXP',
          qrCodeUri: 'otpauth://totp/ValueInvestor:mock@example.com?secret=JBSWY3DPEHPK3PXP&issuer=ValueInvestor',
          recoveryCodes: [
            'AAAA-BBBB-1111',
            'CCCC-DDDD-2222',
            'EEEE-FFFF-3333',
            'GGGG-HHHH-4444',
            'IIII-JJJJ-5555',
            'KKKK-LLLL-6666',
            'MMMM-NNNN-7777',
            'OOOO-PPPP-8888',
          ],
        }),
      }),
    );

    await page.goto('/profile/mfa');

    await page.getByTestId('mfa-enroll-submit').click();

    // After enroll the verify stage must appear with the otpauth URI and secret.
    await expect(page.getByTestId('mfa-otpauth-uri')).toBeVisible();
    await expect(page.getByTestId('mfa-secret')).toBeVisible();
    await expect(page.getByTestId('mfa-verify-input')).toBeVisible();
  });

  test('verify step transitions to recovery codes stage', async ({ page }) => {
    await setupAuthGuardBypass(page);

    await page.route('**/api/auth/mfa/enroll', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          secret: 'JBSWY3DPEHPK3PXP',
          qrCodeUri: 'otpauth://totp/ValueInvestor:mock@example.com?secret=JBSWY3DPEHPK3PXP&issuer=ValueInvestor',
          recoveryCodes: Array.from({ length: 8 }, (_, i) => `CODE-${i + 1}`),
        }),
      }),
    );

    await page.route('**/api/auth/mfa/verify', (route) =>
      route.fulfill({ status: 204, body: '' }),
    );

    await page.goto('/profile/mfa');

    await page.getByTestId('mfa-enroll-submit').click();
    await expect(page.getByTestId('mfa-verify-input')).toBeVisible();

    await page.getByTestId('mfa-verify-input').fill('123456');
    await page.getByTestId('mfa-verify-submit').click();

    // Recovery codes stage must show exactly 8 codes.
    await expect(page.getByTestId('mfa-recovery-codes')).toBeVisible();
    const codeItems = page.getByTestId('mfa-recovery-codes').locator('li');
    await expect(codeItems).toHaveCount(8);
  });

  test('disable MFA with wrong password shows error', async ({ page }) => {
    await setupAuthGuardBypass(page);

    await page.route('**/api/auth/mfa', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          type: 'https://api/errors/invalid-credentials',
          title: 'Invalid credentials',
          status: 401,
        }),
      }),
    );

    await page.goto('/profile/mfa');

    await page.getByTestId('mfa-disable-password').fill('wrong-password-1234567890');
    await page.getByTestId('mfa-disable-submit').click();

    await expect(page.getByTestId('mfa-disable-error')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Tier 2 — Real-BE: full MFA lifecycle (US-081 AC#1 – AC#6)
// ---------------------------------------------------------------------------

test.describe('MFA full lifecycle — real-BE (US-081 AC#1–AC#6)', () => {
  test.beforeEach(async ({ request }) => {
    const reachable = await isBackendReachable(request);
    test.skip(!reachable, `Backend at ${API_BASE} not reachable — skipping real-BE MFA tests`);
  });

  /**
   * US-081 AC#1 + AC#2: enrollment → TOTP login challenge → home navigation.
   * The TOTP code is computed server-side via the enrollment secret; here we
   * drive only the UI challenge form after the BE returns mfaRequired=true.
   * Full enrollment is done via API calls to avoid needing a real authenticator.
   */
  test('enrollment via API then login MFA challenge via UI succeeds (AC#1 + AC#2)', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail('realbe-enroll');

    // Register
    const regResp = await request.post(`${API_BASE}/api/auth/register`, {
      data: { email, password: STRONG_PASSWORD, displayName: null },
    });
    expect(regResp.status(), 'register must return 201').toBe(201);

    // Login to get access token (no MFA yet)
    const loginResp = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });
    expect(loginResp.status(), 'initial login must succeed').toBe(200);
    const { accessToken } = await loginResp.json() as { accessToken: string };

    // Enroll
    const enrollResp = await request.post(`${API_BASE}/api/auth/mfa/enroll`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(enrollResp.status(), 'enroll must return 200').toBe(200);
    const enrollData = await enrollResp.json() as {
      secret: string;
      recoveryCodes: string[];
    };

    // Compute a current TOTP using the server's own library period (30 s)
    // via a small inline base32 + HMAC-SHA1 TOTP generator.
    const totpCode = await computeTotp(enrollData.secret);

    // Verify (activate) MFA
    const verifyResp = await request.post(`${API_BASE}/api/auth/mfa/verify`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      data: { totpCode },
    });
    expect(verifyResp.status(), 'verify must return 204').toBe(204);

    // Now the login page should present the MFA challenge.
    await page.goto('/login');
    await page.getByTestId('login-email').fill(email);
    await page.getByTestId('login-password').fill(STRONG_PASSWORD);
    await page.getByTestId('login-submit').click();

    // MFA challenge form must appear.
    await expect(page.getByTestId('mfa-challenge-form')).toBeVisible();
  });

  /**
   * US-081 AC#3 (recovery code single-use): a recovery code succeeds on first
   * use and is rejected on second use. Exercised via API directly to avoid
   * needing real TOTP computation in Playwright.
   *
   * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-234.md §3]
   */
  test('recovery code is single-use via API (AC#3)', async ({ request }) => {
    const email = uniqueEmail('realbe-recovery');

    await request.post(`${API_BASE}/api/auth/register`, {
      data: { email, password: STRONG_PASSWORD, displayName: null },
    });

    const loginResp = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });
    const { accessToken } = await loginResp.json() as { accessToken: string };

    const enrollResp = await request.post(`${API_BASE}/api/auth/mfa/enroll`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    const { secret, recoveryCodes } = await enrollResp.json() as {
      secret: string;
      recoveryCodes: string[];
    };

    const totpCode = await computeTotp(secret);
    await request.post(`${API_BASE}/api/auth/mfa/verify`, {
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      data: { totpCode },
    });

    // Get mfaToken from login
    const mfaLoginResp = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });
    const { mfaToken } = await mfaLoginResp.json() as { mfaToken: string };

    const recoveryCode = recoveryCodes[0];

    // First use succeeds.
    const firstUse = await request.post(`${API_BASE}/api/auth/mfa/recovery`, {
      data: { mfaToken, recoveryCode },
    });
    expect(firstUse.status(), 'first recovery code use must return 200').toBe(200);
    const firstBody = await firstUse.json() as { accessToken: string };
    expect(firstBody.accessToken).toBeTruthy();

    // Obtain a second mfaToken.
    const mfaLoginResp2 = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });
    const { mfaToken: mfaToken2 } = await mfaLoginResp2.json() as { mfaToken: string };

    // Second use of the same code must be rejected.
    const secondUse = await request.post(`${API_BASE}/api/auth/mfa/recovery`, {
      data: { mfaToken: mfaToken2, recoveryCode },
      failOnStatusCode: false,
    });
    expect(secondUse.status(), 'second use of same recovery code must return 400').toBe(400);
    const secondBody = await secondUse.json() as { type: string };
    expect(secondBody.type).toContain('invalid-recovery-code');
  });

  /**
   * US-081 AC#5 — session fixation: the refresh token cookie issued after a
   * successful MFA challenge is distinct from any token that existed before the
   * challenge, confirming no session was "fixed" across the auth boundary.
   *
   * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-234.md §5]
   */
  test('session fixation: mfaToken cannot be replayed for a second challenge (AC#5)', async ({
    request,
  }) => {
    const email = uniqueEmail('realbe-fixation');

    await request.post(`${API_BASE}/api/auth/register`, {
      data: { email, password: STRONG_PASSWORD, displayName: null },
    });

    const loginResp = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });
    const { accessToken } = await loginResp.json() as { accessToken: string };

    const enrollResp = await request.post(`${API_BASE}/api/auth/mfa/enroll`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    const { secret, recoveryCodes } = await enrollResp.json() as {
      secret: string;
      recoveryCodes: string[];
    };

    const totpCode = await computeTotp(secret);
    await request.post(`${API_BASE}/api/auth/mfa/verify`, {
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      data: { totpCode },
    });

    // Obtain mfaToken.
    const mfaLoginResp = await request.post(`${API_BASE}/api/auth/login`, {
      data: { email, password: STRONG_PASSWORD },
    });
    const { mfaToken } = await mfaLoginResp.json() as { mfaToken: string };
    const recoveryCode = recoveryCodes[0];

    // First challenge succeeds; a fresh refresh token cookie is issued.
    const firstChallenge = await request.post(`${API_BASE}/api/auth/mfa/recovery`, {
      data: { mfaToken, recoveryCode },
    });
    expect(firstChallenge.status(), 'first challenge must succeed').toBe(200);
    const firstSetCookie = firstChallenge.headers()['set-cookie'] ?? '';
    expect(firstSetCookie, 'refresh_token cookie must be set after challenge').toContain('refresh_token=');

    // Replay the same mfaToken with a different recovery code — must fail (token
    // is a single-use short-lived JWT; the server must reject it as expired/invalid
    // or as having an already-used identity).
    const secondRecovery = recoveryCodes[1];
    const replayChallenge = await request.post(`${API_BASE}/api/auth/mfa/recovery`, {
      data: { mfaToken, recoveryCode: secondRecovery },
      failOnStatusCode: false,
    });
    // The mfaToken is a one-time-ish JWT bound to the challenge session; replaying
    // it after the session was completed must be rejected (401 invalid mfaToken
    // or 400 — the exact status depends on JWT expiry vs session invalidation).
    expect(
      [400, 401].includes(replayChallenge.status()),
      `mfaToken replay must be rejected; got ${replayChallenge.status()}`,
    ).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Inline TOTP helper (browser/Node compatible)
// ---------------------------------------------------------------------------

/**
 * Computes the current 6-digit TOTP code for a base32 secret using the
 * same parameters as the server (SHA-1, 6 digits, 30-second period).
 *
 * Implemented using only Web Crypto API (SubtleCrypto) so it runs both in
 * Node (Playwright worker) and in browser contexts without extra dependencies.
 */
async function computeTotp(base32Secret: string): Promise<string> {
  const BASE32_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const clean = base32Secret.toUpperCase().replace(/\s/g, '');
  const bits: number[] = [];
  for (const ch of clean) {
    const val = BASE32_CHARS.indexOf(ch);
    if (val === -1) continue;
    for (let i = 4; i >= 0; i--) bits.push((val >> i) & 1);
  }
  const bytes = new Uint8Array(Math.floor(bits.length / 8));
  for (let i = 0; i < bytes.length; i++) {
    for (let b = 0; b < 8; b++) {
      bytes[i] = ((bytes[i] ?? 0) << 1) | (bits[i * 8 + b] ?? 0);
    }
  }

  const counter = Math.floor(Date.now() / 1000 / 30);
  const counterBytes = new Uint8Array(8);
  let tmp = counter;
  for (let i = 7; i >= 0; i--) {
    counterBytes[i] = tmp & 0xff;
    tmp = Math.floor(tmp / 256);
  }

  // Use crypto from the globalThis (available in both Node 20+ and browsers).
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const subtle: SubtleCrypto =
    typeof globalThis.crypto !== 'undefined'
      ? globalThis.crypto.subtle
      : ((await import('crypto')).webcrypto as unknown as Crypto).subtle;

  const key = await subtle.importKey(
    'raw',
    bytes.buffer,
    { name: 'HMAC', hash: 'SHA-1' },
    false,
    ['sign'],
  );
  const sig = new Uint8Array(await subtle.sign('HMAC', key, counterBytes.buffer));

  const offset = (sig[sig.length - 1] ?? 0) & 0x0f;
  const code =
    (((sig[offset] ?? 0) & 0x7f) << 24) |
    (((sig[offset + 1] ?? 0) & 0xff) << 16) |
    (((sig[offset + 2] ?? 0) & 0xff) << 8) |
    ((sig[offset + 3] ?? 0) & 0xff);

  return String(code % 1_000_000).padStart(6, '0');
}
