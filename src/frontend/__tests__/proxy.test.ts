import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Unit tests for the Next.js AuthGuard proxy (ex `middleware`, TSK-206 / TSK-298).
 * Mocks NextRequest/NextResponse to test the decision table in isolation.
 *
 * Reference: TSK-208 §Scenari AuthGuard, US-073 AC.
 */

const mockRedirect = vi.fn();
const mockNext = vi.fn();

vi.mock('next/server', () => {
  class MockNextResponse {
    headers = new Headers();
    cookies = {
      delete: vi.fn(),
    };
  }

  return {
    NextResponse: {
      redirect: (...args: unknown[]) => {
        const instance = new MockNextResponse();
        mockRedirect(...args);
        return instance;
      },
      next: (init?: { request?: { headers?: Headers } }) => {
        const instance = new MockNextResponse();
        if (init?.request?.headers) {
          const nonce = init.request.headers.get('x-nonce');
          if (nonce) {
            instance.headers.set('x-nonce', nonce);
          }
        }
        mockNext(init);
        return instance;
      },
    },
  };
});

interface CookieMap {
  [key: string]: string;
}

function createMockRequest(
  pathname: string,
  cookies: CookieMap = {},
  search = '',
): { nextUrl: { pathname: string; search: string }; url: string; cookies: { get: (name: string) => { value: string } | undefined } } {
  return {
    nextUrl: { pathname, search },
    url: `http://localhost:3000${pathname}${search}`,
    cookies: {
      get: (name: string) => {
        const value = cookies[name];
        return value !== undefined ? { value } : undefined;
      },
    },
  };
}

describe('AuthGuard proxy (dev runtime)', () => {
  beforeEach(() => {
    vi.resetModules();
    mockRedirect.mockClear();
    mockNext.mockClear();
    // Dev-only by design (TSK-268): the decision table only runs when
    // NODE_ENV === 'development'. Vitest sets NODE_ENV='test' by default,
    // so we stub it to exercise the in-dev branch.
    vi.stubEnv('NODE_ENV', 'development');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  async function importProxy() {
    const mod = await import('../proxy');
    return mod.proxy;
  }

  it('redirects unauthenticated request to /login with returnUrl (scenario 4)', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist', {});

    proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/login');
    expect(redirectUrl.searchParams.get('returnUrl')).toBe('/watchlist');
  });

  it('redirects unauthenticated request preserving search params in returnUrl', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist', {}, '?sort=name');

    proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.searchParams.get('returnUrl')).toBe('/watchlist?sort=name');
  });

  it('redirects authenticated user visiting /login to / (scenario 5)', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/login', { isAuthenticated: 'true' });

    proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/');
  });

  it('redirects to /403 when user role does not match route requirement (scenario 6)', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/admin', {
      isAuthenticated: 'true',
      userRole: 'user',
    });

    proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/403');
  });

  it('allows admin to access /admin route', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/admin', {
      isAuthenticated: 'true',
      userRole: 'admin',
    });

    proxy(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('passes through for public routes without auth (scenario 7)', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/', {});

    proxy(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('redirects unauthenticated /analysis request to /login (TSK-267 — newly protected)', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/analysis', {}, '?ticker=AAPL');

    proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl = mockRedirect.mock.calls[0]?.[0] as URL;
    expect(redirectUrl.pathname).toBe('/login');
    expect(redirectUrl.searchParams.get('returnUrl')).toBe('/analysis?ticker=AAPL');
  });

  it('passes through for /screener public route', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/screener', {});

    proxy(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('handles session expired cookie: redirects to /login?expired=true (scenario 1)', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist', {
      isAuthenticated: 'true',
      sessionExpired: 'true',
    });

    const response = proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/login');
    expect(redirectUrl.searchParams.get('expired')).toBe('true');
    expect(response.cookies.delete).toHaveBeenCalledWith('sessionExpired');
    expect(response.cookies.delete).toHaveBeenCalledWith('isAuthenticated');
  });

  it('passes through for unknown routes (fail-open on frontend)', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/some-unknown-route', {});

    proxy(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('normalises trailing slash before route lookup', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist/', {});

    proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/login');
    expect(redirectUrl.searchParams.get('returnUrl')).toBe('/watchlist/');
  });
});

describe('CSP proxy (TSK-222) — dev runtime', () => {
  const fixedNonce = '00000000-0000-4000-8000-000000000001';

  beforeEach(() => {
    vi.resetModules();
    mockRedirect.mockClear();
    mockNext.mockClear();
    vi.stubEnv('NODE_ENV', 'development');
    vi.stubGlobal('crypto', {
      randomUUID: () => fixedNonce,
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function importProxy() {
    const mod = await import('../proxy');
    return mod.proxy;
  }

  function expectCspOnResponse(response: { headers: Headers }): void {
    const csp = response.headers.get('Content-Security-Policy');
    expect(csp).toBeTruthy();
    // Dev mode CSP always includes per-request nonce + `'unsafe-inline'`
    // for HMR (TSK-222 §dev relaxation). Production CSP — without
    // `'unsafe-inline'` — is emitted by the backend `SecurityHeadersConfig`
    // (TSK-221), never by this proxy (dev-only per TSK-268).
    expect(csp).toContain(`'nonce-${fixedNonce}'`);
    expect(csp).toMatch(/script-src[^;]*'unsafe-inline'/);
    expect(csp).toMatch(/connect-src[^;]*ws:/);
  }

  it('adds Content-Security-Policy with nonce on pass-through responses', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/', {});

    const response = proxy(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expectCspOnResponse(response);
    const nextInit = mockNext.mock.calls[0][0] as {
      request?: { headers?: Headers };
    };
    expect(nextInit.request?.headers?.get('x-nonce')).toBe(fixedNonce);
  });

  it('adds Content-Security-Policy with nonce on redirect responses', async () => {
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist', {});

    const response = proxy(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    expectCspOnResponse(response);
  });
});

/**
 * Dev-only guard-rail (TSK-268 / ADR-026).
 *
 * `proxy.ts` is intentionally limited to `next dev` because the
 * production bundle is `output: 'export'` and never executes the proxy.
 * If a future runtime accidentally invokes this code path with a
 * non-development `NODE_ENV`, it must short-circuit to pass-through
 * without redirects, CSP headers, or any other side-effect — production
 * auth lives in `ClientAuthGuard` (UX) and on the backend (security).
 */
describe('proxy dev-only guard-rail (TSK-268)', () => {
  beforeEach(() => {
    vi.resetModules();
    mockRedirect.mockClear();
    mockNext.mockClear();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  async function importProxy() {
    const mod = await import('../proxy');
    return mod.proxy;
  }

  it('passes through without redirect when NODE_ENV is "production"', async () => {
    vi.stubEnv('NODE_ENV', 'production');
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist', {});

    proxy(request as never);

    expect(mockRedirect).not.toHaveBeenCalled();
    expect(mockNext).toHaveBeenCalledTimes(1);
  });

  it('emits no Content-Security-Policy header outside dev', async () => {
    vi.stubEnv('NODE_ENV', 'production');
    const proxy = await importProxy();
    const request = createMockRequest('/', {});

    const response = proxy(request as never);

    expect(response.headers.get('Content-Security-Policy')).toBeNull();
  });

  it('passes through admin route with non-admin role when NODE_ENV !== "development"', async () => {
    vi.stubEnv('NODE_ENV', 'production');
    const proxy = await importProxy();
    const request = createMockRequest('/admin', {
      isAuthenticated: 'true',
      userRole: 'user',
    });

    proxy(request as never);

    expect(mockRedirect).not.toHaveBeenCalled();
    expect(mockNext).toHaveBeenCalledTimes(1);
  });

  it('ignores sessionExpired cookie outside dev (no redirect, no cookie clearing)', async () => {
    vi.stubEnv('NODE_ENV', 'production');
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist', {
      isAuthenticated: 'true',
      sessionExpired: 'true',
    });

    const response = proxy(request as never);

    expect(mockRedirect).not.toHaveBeenCalled();
    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(response.cookies.delete).not.toHaveBeenCalled();
  });

  it('passes through with vitest default NODE_ENV ("test") — guard-rail is strict', async () => {
    // No stubEnv: vitest default is NODE_ENV='test'. The proxy must
    // bail to `NextResponse.next()` since 'test' !== 'development'.
    const proxy = await importProxy();
    const request = createMockRequest('/watchlist', {});

    proxy(request as never);

    expect(mockRedirect).not.toHaveBeenCalled();
    expect(mockNext).toHaveBeenCalledTimes(1);
  });
});
