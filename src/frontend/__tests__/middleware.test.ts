import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Unit tests for the Next.js AuthGuard middleware (TSK-206).
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

describe('AuthGuard middleware', () => {
  beforeEach(() => {
    vi.resetModules();
    mockRedirect.mockClear();
    mockNext.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  async function importMiddleware() {
    const mod = await import('../middleware');
    return mod.middleware;
  }

  it('redirects unauthenticated request to /login with returnUrl (scenario 4)', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/watchlist', {});

    middleware(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/login');
    expect(redirectUrl.searchParams.get('returnUrl')).toBe('/watchlist');
  });

  it('redirects unauthenticated request preserving search params in returnUrl', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/watchlist', {}, '?sort=name');

    middleware(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.searchParams.get('returnUrl')).toBe('/watchlist?sort=name');
  });

  it('redirects authenticated user visiting /login to / (scenario 5)', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/login', { isAuthenticated: 'true' });

    middleware(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/');
  });

  it('redirects to /403 when user role does not match route requirement (scenario 6)', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/admin', {
      isAuthenticated: 'true',
      userRole: 'user',
    });

    middleware(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/403');
  });

  it('allows admin to access /admin route', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/admin', {
      isAuthenticated: 'true',
      userRole: 'admin',
    });

    middleware(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('passes through for public routes without auth (scenario 7)', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/', {});

    middleware(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('passes through for /analysis public route', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/analysis', {});

    middleware(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('handles session expired cookie: redirects to /login?expired=true (scenario 1)', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/watchlist', {
      isAuthenticated: 'true',
      sessionExpired: 'true',
    });

    const response = middleware(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/login');
    expect(redirectUrl.searchParams.get('expired')).toBe('true');
    expect(response.cookies.delete).toHaveBeenCalledWith('sessionExpired');
    expect(response.cookies.delete).toHaveBeenCalledWith('isAuthenticated');
  });

  it('passes through for unknown routes (fail-open on frontend)', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/some-unknown-route', {});

    middleware(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expect(mockRedirect).not.toHaveBeenCalled();
  });

  it('normalises trailing slash before route lookup', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/watchlist/', {});

    middleware(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    const redirectUrl: URL = mockRedirect.mock.calls[0][0];
    expect(redirectUrl.pathname).toBe('/login');
    expect(redirectUrl.searchParams.get('returnUrl')).toBe('/watchlist/');
  });
});

describe('CSP middleware (TSK-222)', () => {
  const fixedNonce = '00000000-0000-4000-8000-000000000001';

  beforeEach(() => {
    vi.resetModules();
    mockRedirect.mockClear();
    mockNext.mockClear();
    vi.stubGlobal('crypto', {
      randomUUID: () => fixedNonce,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function importMiddleware() {
    const mod = await import('../middleware');
    return mod.middleware;
  }

  function expectCspOnResponse(response: { headers: Headers }): void {
    const csp = response.headers.get('Content-Security-Policy');
    expect(csp).toBeTruthy();
    expect(csp).toContain(`'nonce-${fixedNonce}'`);
    expect(csp).not.toMatch(/script-src[^;]*unsafe-inline/);
  }

  it('adds Content-Security-Policy with nonce on pass-through responses', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/', {});

    const response = middleware(request as never);

    expect(mockNext).toHaveBeenCalledTimes(1);
    expectCspOnResponse(response);
    const nextInit = mockNext.mock.calls[0][0] as {
      request?: { headers?: Headers };
    };
    expect(nextInit.request?.headers?.get('x-nonce')).toBe(fixedNonce);
  });

  it('adds Content-Security-Policy with nonce on redirect responses', async () => {
    const middleware = await importMiddleware();
    const request = createMockRequest('/watchlist', {});

    const response = middleware(request as never);

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    expectCspOnResponse(response);
  });
});
