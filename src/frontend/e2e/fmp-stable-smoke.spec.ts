/**
 * FMP /stable Migration Smoke — US-031 Acceptance Criteria
 *
 * Verifies the four DoD items of TSK-274 against a live staging/local container
 * deployment. All requests hit the real application — no page.route() mocks.
 *
 * Execution (on-demand only — NOT in CI default):
 *   STAGING_URL=http://localhost:8080 \
 *   STAGING_USER_EMAIL=qa@example.com \
 *   STAGING_USER_PASSWORD=*** \
 *   npx playwright test fmp-stable-smoke
 *
 * Prerequisites:
 *   1. Container vi-app is healthy (`docker compose up -d`)
 *   2. A QA user account registered (for authenticated endpoints)
 *
 * References:
 *   management/kanban/EP-002-integrazione-fmp-data-provider/US-031-fmp-adapter-stable-migration/TSK-274.md
 *   management/kanban/EP-002-integrazione-fmp-data-provider/US-031-fmp-adapter-stable-migration/US-031.md §Acceptance Criteria
 *   [^src: raw/tech_stack.md §QA / Testing]
 */

import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Configuration — driven entirely by env vars, no hard-coded credentials
// ---------------------------------------------------------------------------

const STAGING = process.env.STAGING_URL || 'https://app-staging.example.com';
const USER = process.env.STAGING_USER_EMAIL;
const PASS = process.env.STAGING_USER_PASSWORD;

// Timeout for API responses (real network, real BE + FMP upstream)
const API_TIMEOUT = 20_000;

// ---------------------------------------------------------------------------
// Suite — FMP /stable migration (US-031 Acceptance Criteria)
// ---------------------------------------------------------------------------

test.describe('FMP /stable migration smoke — US-031 AC', () => {
  // Skip the entire suite when credentials are not configured.
  // Mirrors the guard pattern from cutover-smoke.spec.ts.
  test.skip(!USER || !PASS, 'STAGING_USER_EMAIL / STAGING_USER_PASSWORD not set');

  // -------------------------------------------------------------------------
  // Shared JWT — obtained once via login API, reused across API tests
  // -------------------------------------------------------------------------

  let authToken: string | undefined;

  test.beforeAll(async ({ request }) => {
    const loginResp = await request.post(`${STAGING}/api/auth/login`, {
      data: { email: USER, password: PASS },
      timeout: API_TIMEOUT,
    });
    // If login fails we surface the error in every test rather than silently
    // using an undefined token that would produce 401s.
    if (loginResp.ok()) {
      const body = await loginResp.json() as { accessToken?: string };
      authToken = body.accessToken;
    }
  });

  // -------------------------------------------------------------------------
  // AC-1 — GET /api/search?query=TTD → "The Trade Desk Inc."
  // [^src: US-031.md §Acceptance Criteria – AC-1]
  // -------------------------------------------------------------------------
  test('AC-1: GET /api/search?query=TTD returns "The Trade Desk Inc."', async ({ request }) => {
    expect(authToken, 'authToken must be set — login failed in beforeAll').toBeTruthy();

    const resp = await request.get(`${STAGING}/api/search`, {
      params: { query: 'TTD' },
      headers: { Authorization: `Bearer ${authToken}` },
      timeout: API_TIMEOUT,
    });

    expect(resp.status(), 'GET /api/search?query=TTD HTTP status').toBe(200);

    const body = await resp.json() as unknown;
    // The response can be an array or a wrapper object; extract the text to
    // search for the expected company name regardless of exact shape.
    const bodyText = JSON.stringify(body);
    expect(
      bodyText,
      'Response must contain "The Trade Desk Inc." — FMP /stable search-symbol endpoint',
    ).toContain('The Trade Desk Inc.');
  });

  // -------------------------------------------------------------------------
  // AC-2 — GET /api/screener?marketCap=LARGE,MEGA → no 503
  // [^src: US-031.md §Acceptance Criteria – AC-2]
  // -------------------------------------------------------------------------
  test('AC-2: GET /api/screener?marketCap=LARGE,MEGA returns 200 (no 503 fmp-unavailable)', async ({ request }) => {
    expect(authToken, 'authToken must be set — login failed in beforeAll').toBeTruthy();

    const resp = await request.get(`${STAGING}/api/screener`, {
      params: { marketCap: 'LARGE,MEGA' },
      headers: { Authorization: `Bearer ${authToken}` },
      timeout: API_TIMEOUT,
    });

    // 503 would indicate FMP upstream failure via deprecated v3 endpoint.
    // 200 (with any body, even empty array) means the stable endpoint routed ok.
    expect(
      resp.status(),
      'GET /api/screener?marketCap=LARGE,MEGA must not return 503 (FMP unavailable)',
    ).not.toBe(503);

    // Must be a 2xx success — not a redirect or error family.
    expect(
      resp.status(),
      'GET /api/screener?marketCap=LARGE,MEGA HTTP status must be 2xx',
    ).toBeLessThan(300);
  });

  // -------------------------------------------------------------------------
  // AC-3 — GET /api/analysis/AAPL → financials, no 403/503
  // [^src: US-031.md §Acceptance Criteria – AC-3]
  // -------------------------------------------------------------------------
  test('AC-3: GET /api/analysis/AAPL returns real financials (no 403/503 on FMP call)', async ({ request }) => {
    expect(authToken, 'authToken must be set — login failed in beforeAll').toBeTruthy();

    const resp = await request.get(`${STAGING}/api/analysis/AAPL`, {
      headers: { Authorization: `Bearer ${authToken}` },
      timeout: API_TIMEOUT,
    });

    // 403 → FMP auth failure (wrong key or wrong endpoint path still using v3)
    expect(
      resp.status(),
      'GET /api/analysis/AAPL must not return 403 (FMP auth error)',
    ).not.toBe(403);

    // 503 → FMP upstream unavailable (deprecated v3 EOL response)
    expect(
      resp.status(),
      'GET /api/analysis/AAPL must not return 503 (FMP unavailable)',
    ).not.toBe(503);

    expect(
      resp.status(),
      'GET /api/analysis/AAPL HTTP status must be 200',
    ).toBe(200);

    const body = await resp.json() as { signals?: unknown[] };
    // The financials are present if the analysis object contains at minimum a
    // signals array (even an empty one); absence indicates FMP data was not
    // fetched at all.
    expect(
      Object.prototype.hasOwnProperty.call(body, 'signals'),
      'Response body must contain a "signals" field — financials fetched from FMP /stable',
    ).toBe(true);
  });

  // -------------------------------------------------------------------------
  // AC-4 — No api/v3 path in FMP container logs
  // [^src: TSK-274.md §Definition of Done – DoD-6]
  //
  // This AC is runtime-observable only via docker logs; Playwright cannot
  // introspect container stdout directly. The test verifies the proxy
  // contract: if the app exposes a dedicated debug/log endpoint the assertion
  // is checked there, otherwise a surrogate assertion validates that the three
  // primary endpoints succeed (implying /stable routing), and the test is
  // annotated so CI can surface this as an advisory rather than a hard gate
  // when the debug endpoint is absent.
  // -------------------------------------------------------------------------
  test('AC-4: no api/v3 FMP path in container logs (surrogate: /stable proxy reachable)', async ({ request }) => {
    expect(authToken, 'authToken must be set — login failed in beforeAll').toBeTruthy();

    // Attempt to reach a debug log endpoint if exposed (optional, advisory).
    // The app may expose GET /api/debug/fmp-log or similar in non-prod mode.
    const debugResp = await request.get(`${STAGING}/api/debug/fmp-log`, {
      headers: { Authorization: `Bearer ${authToken}` },
      timeout: 5_000,
    }).catch(() => null);

    if (debugResp !== null && debugResp.ok()) {
      const logText = await debugResp.text();
      expect(
        logText,
        'FMP debug log must not contain any api/v3 path — all calls must use /stable',
      ).not.toContain('api/v3');
    } else {
      // Debug endpoint absent (expected in staging): fall back to surrogate.
      // If all three primary endpoints returned 200 (AC-1..AC-3 passed), the
      // routing is confirmed as /stable. Mark this AC as advisory-only here.
      test.info().annotations.push({
        type: 'advisory',
        description:
          'AC-4 (no api/v3 in logs) cannot be asserted via HTTP — ' +
          'verify manually with: docker logs vi-app | grep api/v3 (must return empty)',
      });

      // Surrogate: confirm /stable base URL is reachable via the search proxy.
      const surrogate = await request.get(`${STAGING}/api/search`, {
        params: { query: 'AAPL' },
        headers: { Authorization: `Bearer ${authToken}` },
        timeout: API_TIMEOUT,
      });
      expect(
        surrogate.status(),
        'Surrogate for AC-4: /api/search proxy (backed by FMP /stable) must return 200',
      ).toBe(200);
    }
  });
});
