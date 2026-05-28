import { describe, expect, it } from 'vitest';

import {
  buildLoginUrl,
  evaluateAuthGuard,
  type AuthGuardInput,
} from '../auth-guard-decision';

/**
 * Unit tests for the pure AuthGuard decision matrix (TSK-266 / US-087).
 *
 * Reference: ADR-026 §Decisione + US-087 §AC.
 */

function input(overrides: Partial<AuthGuardInput> = {}): AuthGuardInput {
  return {
    pathname: '/watchlist',
    currentUrl: '/watchlist',
    rehydrationStatus: 'done',
    accessToken: 'token',
    userRole: null,
    sessionExpired: false,
    ...overrides,
  };
}

describe('evaluateAuthGuard — TSK-266 decision matrix', () => {
  describe('loading branch', () => {
    it.each(['pending', 'rehydrating'] as const)(
      'returns loading while rehydrationStatus=%s',
      (status) => {
        const decision = evaluateAuthGuard(input({ rehydrationStatus: status }));
        expect(decision.type).toBe('loading');
      },
    );
  });

  describe('public/unknown routes (no regression on public routes)', () => {
    it.each([
      '/',
      '/login',
      '/register',
      '/screener',
    ])('passes through public route %s even without token', (pathname) => {
      const decision = evaluateAuthGuard(
        input({ pathname, currentUrl: pathname, accessToken: null }),
      );
      expect(decision.type).toBe('allow');
    });

    it('passes through unknown routes (fail-open)', () => {
      const decision = evaluateAuthGuard(
        input({ pathname: '/totally-unknown', currentUrl: '/totally-unknown', accessToken: null }),
      );
      expect(decision.type).toBe('allow');
    });

    it('does not redirect even when sessionExpired is true on a public route', () => {
      const decision = evaluateAuthGuard(
        input({
          pathname: '/login',
          currentUrl: '/login?expired=true',
          accessToken: null,
          sessionExpired: true,
        }),
      );
      expect(decision.type).toBe('allow');
    });
  });

  describe('unauthenticated branch', () => {
    it('returns unauthenticated on a protected route without token', () => {
      const decision = evaluateAuthGuard(
        input({ pathname: '/watchlist', currentUrl: '/watchlist', accessToken: null }),
      );
      expect(decision).toEqual({ type: 'unauthenticated', returnUrl: '/watchlist' });
    });

    it('preserves search params in the returnUrl', () => {
      const decision = evaluateAuthGuard(
        input({
          pathname: '/moat',
          currentUrl: '/moat?ticker=AAPL',
          accessToken: null,
        }),
      );
      expect(decision).toEqual({
        type: 'unauthenticated',
        returnUrl: '/moat?ticker=AAPL',
      });
    });

    it.each([
      ['/analysis', '/analysis?ticker=AAPL'],
      ['/analysis/deep', '/analysis/deep?ticker=AAPL'],
      ['/top-picks', '/top-picks?sector=Tech&min_mos=20'],
    ])(
      'protects newly-guarded route %s with full path+query in returnUrl (TSK-267)',
      (pathname, currentUrl) => {
        const decision = evaluateAuthGuard(
          input({ pathname, currentUrl, accessToken: null }),
        );
        expect(decision).toEqual({ type: 'unauthenticated', returnUrl: currentUrl });
      },
    );
  });

  describe('session-expired branch', () => {
    it('takes precedence over unauthenticated on a protected route', () => {
      const decision = evaluateAuthGuard(
        input({
          pathname: '/watchlist',
          currentUrl: '/watchlist',
          accessToken: null,
          sessionExpired: true,
        }),
      );
      expect(decision).toEqual({
        type: 'session-expired',
        returnUrl: '/watchlist',
      });
    });

    it('triggers even when token is still in memory but flag is set', () => {
      const decision = evaluateAuthGuard(
        input({
          pathname: '/profile',
          currentUrl: '/profile',
          accessToken: 'stale',
          sessionExpired: true,
        }),
      );
      expect(decision.type).toBe('session-expired');
    });
  });

  describe('forbidden branch (role check)', () => {
    it('redirects authenticated user to /403 when role is missing', () => {
      const decision = evaluateAuthGuard(
        input({ pathname: '/admin', currentUrl: '/admin', userRole: 'USER' }),
      );
      expect(decision.type).toBe('forbidden');
    });

    it('matches roles case-insensitively (route map "admin" vs BE "ADMIN")', () => {
      const decision = evaluateAuthGuard(
        input({ pathname: '/admin', currentUrl: '/admin', userRole: 'ADMIN' }),
      );
      expect(decision.type).toBe('allow');
    });

    it('returns forbidden when user has no role and admin route requires one', () => {
      const decision = evaluateAuthGuard(
        input({ pathname: '/admin', currentUrl: '/admin', userRole: null }),
      );
      expect(decision.type).toBe('forbidden');
    });

    it('inherits role requirement on sub-paths via longest-prefix match', () => {
      const decision = evaluateAuthGuard(
        input({
          pathname: '/admin/llm-budget',
          currentUrl: '/admin/llm-budget',
          userRole: 'USER',
        }),
      );
      expect(decision.type).toBe('forbidden');
    });
  });

  describe('allow branch (authenticated, no role requirement)', () => {
    it.each(['/watchlist', '/moat', '/profile', '/profile/mfa'])(
      'allows authenticated user to access %s',
      (pathname) => {
        const decision = evaluateAuthGuard(
          input({ pathname, currentUrl: pathname, userRole: 'USER' }),
        );
        expect(decision.type).toBe('allow');
      },
    );
  });
});

describe('buildLoginUrl', () => {
  it('returns plain /login when returnUrl is empty', () => {
    expect(buildLoginUrl('')).toBe('/login');
  });

  it('omits returnUrl when it points back to /login', () => {
    expect(buildLoginUrl('/login')).toBe('/login');
    expect(buildLoginUrl('/login?expired=true')).toBe('/login');
  });

  it('encodes returnUrl as query param', () => {
    const url = buildLoginUrl('/watchlist?sort=name');
    expect(url).toMatch(/^\/login\?/);
    const params = new URLSearchParams(url.split('?')[1]);
    expect(params.get('returnUrl')).toBe('/watchlist?sort=name');
  });

  it('adds expired=true marker when requested', () => {
    const url = buildLoginUrl('/watchlist', { expired: true });
    const params = new URLSearchParams(url.split('?')[1]);
    expect(params.get('expired')).toBe('true');
    expect(params.get('returnUrl')).toBe('/watchlist');
  });

  it('emits only expired marker when returnUrl is empty', () => {
    expect(buildLoginUrl('', { expired: true })).toBe('/login?expired=true');
  });
});
