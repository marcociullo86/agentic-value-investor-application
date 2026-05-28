import { describe, expect, it } from 'vitest';

import {
  getRequiredRoles,
  getRouteConfig,
  isProtectedRoute,
} from '../route-config';

/**
 * Unit tests for route-config.ts (TSK-205).
 * Validates the declarative route map lookup used by AuthGuard middleware.
 *
 * Reference: TSK-208 §Scenari route map, US-073 AC, US-074 AC.
 */

describe('route-config', () => {
  describe('getRouteConfig', () => {
    it('returns exact match for known routes', () => {
      const config = getRouteConfig('/watchlist');
      expect(config).toBeDefined();
      expect(config!.path).toBe('/watchlist');
      expect(config!.requiresAuth).toBe(true);
    });

    it('returns undefined for unknown routes (fail-open)', () => {
      expect(getRouteConfig('/unknown-path')).toBeUndefined();
    });

    it('matches sub-path via longest-prefix', () => {
      const config = getRouteConfig('/admin/users');
      expect(config).toBeDefined();
      expect(config!.path).toBe('/admin');
      expect(config!.requiresAuth).toBe(true);
    });
  });

  describe('isProtectedRoute', () => {
    it.each([
      ['/', false],
      ['/login', false],
      ['/register', false],
      ['/screener', false],
    ])('public route %s returns false', (path, expected) => {
      expect(isProtectedRoute(path)).toBe(expected);
    });

    it.each([
      ['/analysis', true],
      ['/analysis/deep', true],
      ['/top-picks', true],
      ['/watchlist', true],
      ['/moat', true],
      ['/profile', true],
      ['/admin', true],
    ])('protected route %s returns true', (path, expected) => {
      expect(isProtectedRoute(path)).toBe(expected);
    });

    it('unknown route defaults to false (fail-open)', () => {
      expect(isProtectedRoute('/completely-unknown')).toBe(false);
    });
  });

  describe('getRequiredRoles', () => {
    it('returns empty array for routes without role restrictions', () => {
      expect(getRequiredRoles('/watchlist')).toEqual([]);
      expect(getRequiredRoles('/moat')).toEqual([]);
      expect(getRequiredRoles('/')).toEqual([]);
    });

    it('returns ["admin"] for /admin route', () => {
      expect(getRequiredRoles('/admin')).toEqual(['admin']);
    });

    it('returns empty array for unknown routes', () => {
      expect(getRequiredRoles('/nonexistent')).toEqual([]);
    });

    it('sub-route /admin/settings inherits admin role requirement', () => {
      expect(getRequiredRoles('/admin/settings')).toEqual(['admin']);
    });
  });

  describe('declarative route map extensibility (US-074)', () => {
    it('adding a route to ROUTE_MAP makes it discoverable without code changes to AuthGuard', () => {
      const protectedRoutes = [
        '/analysis',
        '/analysis/deep',
        '/top-picks',
        '/watchlist',
        '/moat',
        '/profile',
        '/admin',
      ];
      for (const route of protectedRoutes) {
        const config = getRouteConfig(route);
        expect(config).toBeDefined();
        expect(config!.requiresAuth).toBe(true);
      }
    });
  });
});
