import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';

/**
 * Unit tests for `useAuthGuard` (TSK-266 / US-087).
 *
 * The hook glues:
 *   - `evaluateAuthGuard` (pure decision, covered in `auth-guard-decision.test.ts`)
 *   - Zustand auth store selectors
 *   - Next.js `useRouter` / `usePathname` / `useSearchParams`
 *
 * These tests cover the redirect side-effects + de-duplication guard.
 */

const replaceMock = vi.fn();
let pathnameMock = '/watchlist';
let searchParamsMock = '';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => pathnameMock,
  useSearchParams: () => new URLSearchParams(searchParamsMock),
}));

interface AuthStoreShape {
  accessToken: string | null;
  rehydrationStatus: 'pending' | 'rehydrating' | 'done';
  user: { role?: string } | null;
  sessionExpired: boolean;
  setSessionExpired: (v: boolean) => void;
  clearSession: () => void;
}

const storeState: AuthStoreShape = {
  accessToken: null,
  rehydrationStatus: 'done',
  user: null,
  sessionExpired: false,
  setSessionExpired: vi.fn(),
  clearSession: vi.fn(),
};

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: AuthStoreShape) => unknown) => selector(storeState),
}));

import { useAuthGuard } from '@/hooks/use-auth-guard';

function setStore(partial: Partial<AuthStoreShape>): void {
  Object.assign(storeState, partial);
}

describe('useAuthGuard — redirect side-effects', () => {
  beforeEach(() => {
    replaceMock.mockReset();
    storeState.setSessionExpired = vi.fn();
    storeState.clearSession = vi.fn();
    setStore({
      accessToken: null,
      rehydrationStatus: 'done',
      user: null,
      sessionExpired: false,
    });
    pathnameMock = '/watchlist';
    searchParamsMock = '';
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns loading while rehydration is in flight (no redirect)', () => {
    setStore({ rehydrationStatus: 'rehydrating' });
    const { result } = renderHook(() => useAuthGuard());
    expect(result.current.type).toBe('loading');
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it('redirects unauthenticated user to /login with returnUrl', () => {
    setStore({ rehydrationStatus: 'done', accessToken: null });
    pathnameMock = '/watchlist';
    searchParamsMock = 'sort=name';
    const { result } = renderHook(() => useAuthGuard());
    expect(result.current.type).toBe('unauthenticated');
    expect(replaceMock).toHaveBeenCalledTimes(1);
    const url = replaceMock.mock.calls[0]?.[0] as string;
    expect(url).toMatch(/^\/login\?/);
    const params = new URLSearchParams(url.split('?')[1] ?? '');
    expect(params.get('returnUrl')).toBe('/watchlist?sort=name');
    expect(params.get('expired')).toBeNull();
  });

  it('redirects to /403 when role does not match', () => {
    setStore({
      rehydrationStatus: 'done',
      accessToken: 'token',
      user: { role: 'USER' },
    });
    pathnameMock = '/admin';
    renderHook(() => useAuthGuard());
    expect(replaceMock).toHaveBeenCalledWith('/403');
  });

  it('handles session-expired with silent logout + login redirect', () => {
    const clearSession = vi.fn();
    const setSessionExpired = vi.fn();
    setStore({
      rehydrationStatus: 'done',
      accessToken: 'stale',
      sessionExpired: true,
      clearSession,
      setSessionExpired,
    });
    pathnameMock = '/watchlist';
    searchParamsMock = '';
    renderHook(() => useAuthGuard());

    expect(clearSession).toHaveBeenCalledTimes(1);
    expect(setSessionExpired).toHaveBeenCalledWith(false);
    expect(replaceMock).toHaveBeenCalledTimes(1);
    const url = replaceMock.mock.calls[0]?.[0] as string;
    const params = new URLSearchParams(url.split('?')[1] ?? '');
    expect(params.get('expired')).toBe('true');
    expect(params.get('returnUrl')).toBe('/watchlist');
  });

  it('does not redirect on public routes (no regression on /, /login, /register)', () => {
    setStore({
      rehydrationStatus: 'done',
      accessToken: null,
      sessionExpired: false,
    });
    for (const p of ['/', '/login', '/register']) {
      replaceMock.mockReset();
      pathnameMock = p;
      const { result } = renderHook(() => useAuthGuard());
      expect(result.current.type).toBe('allow');
      expect(replaceMock).not.toHaveBeenCalled();
    }
  });

  it('does not redirect when access is allowed', () => {
    setStore({
      rehydrationStatus: 'done',
      accessToken: 'token',
      user: { role: 'USER' },
    });
    pathnameMock = '/watchlist';
    const { result } = renderHook(() => useAuthGuard());
    expect(result.current.type).toBe('allow');
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it('deduplicates redirects across re-renders for the same decision', () => {
    setStore({ rehydrationStatus: 'done', accessToken: null });
    pathnameMock = '/watchlist';
    const { rerender } = renderHook(() => useAuthGuard());
    rerender();
    rerender();
    expect(replaceMock).toHaveBeenCalledTimes(1);
  });
});
