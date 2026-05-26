/**
 * TSK-212 — Security-focused tests for auth storage migration (US-075).
 *
 * Verifies AC:
 *  - No token/credential in localStorage after login
 *  - No refreshToken in Zustand state (cookie-only)
 *  - Rehydration via cookie-based refresh (success + failure)
 *  - isAuthenticated cookie hint lifecycle (login / logout)
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../useAuthStore';

vi.mock('@/lib/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  refreshTokens: vi.fn(),
  register: vi.fn(),
}));

import * as authApi from '@/lib/api/auth';

const mockedLogin = authApi.login as ReturnType<typeof vi.fn>;
const mockedLogout = authApi.logout as ReturnType<typeof vi.fn>;
const mockedRefresh = authApi.refreshTokens as ReturnType<typeof vi.fn>;

function resetDocumentCookie(): void {
  Object.defineProperty(document, 'cookie', {
    writable: true,
    value: '',
  });
}

describe('TSK-212 — Auth storage security', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      user: null,
      rehydrationStatus: 'pending',
      sessionExpired: false,
    });
    mockedLogin.mockReset();
    mockedLogout.mockReset();
    mockedRefresh.mockReset();
    resetDocumentCookie();
    localStorage.clear();
  });

  afterEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
  });

  describe('localStorage audit (US-075 AC#1)', () => {
    it('no refreshToken in localStorage after login', async () => {
      mockedLogin.mockResolvedValueOnce({
        accessToken: 'access-jwt',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().login('user@example.com', 'password');

      expect(localStorage.getItem('refreshToken')).toBeNull();
    });

    it('no accessToken in localStorage after login', async () => {
      mockedLogin.mockResolvedValueOnce({
        accessToken: 'access-jwt',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().login('user@example.com', 'password');

      expect(localStorage.getItem('accessToken')).toBeNull();
    });

    it('no token or session keys in localStorage after full lifecycle', async () => {
      mockedLogin.mockResolvedValueOnce({
        accessToken: 'access-jwt',
        expiresInSeconds: 900,
      });
      mockedLogout.mockResolvedValueOnce(undefined);

      await useAuthStore.getState().login('user@example.com', 'password');
      await useAuthStore.getState().logout();

      const sensitiveKeys = [
        'refreshToken',
        'accessToken',
        'token',
        'jwt',
        'session',
        'sessionId',
        'auth',
      ];
      for (const key of sensitiveKeys) {
        expect(localStorage.getItem(key)).toBeNull();
      }
    });
  });

  describe('Zustand state shape (US-075 AC#2)', () => {
    it('store has no refreshToken field after login', async () => {
      mockedLogin.mockResolvedValueOnce({
        accessToken: 'access-jwt',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().login('user@example.com', 'password');

      const state = useAuthStore.getState();
      expect(state.accessToken).toBe('access-jwt');
      expect('refreshToken' in state).toBe(false);
    });
  });

  describe('Rehydration (US-075 AC#5)', () => {
    it('successful refresh populates accessToken in store', async () => {
      mockedRefresh.mockResolvedValueOnce({
        accessToken: 'rehydrated-access',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().rehydrate();

      const state = useAuthStore.getState();
      expect(state.accessToken).toBe('rehydrated-access');
      expect(state.rehydrationStatus).toBe('done');
    });

    it('failed refresh leaves store unauthenticated', async () => {
      mockedRefresh.mockRejectedValueOnce(new Error('401 Unauthorized'));

      await useAuthStore.getState().rehydrate();

      const state = useAuthStore.getState();
      expect(state.accessToken).toBeNull();
      expect(state.user).toBeNull();
      expect(state.rehydrationStatus).toBe('done');
    });

    it('rehydrate does not touch localStorage', async () => {
      mockedRefresh.mockResolvedValueOnce({
        accessToken: 'rehydrated-access',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().rehydrate();

      expect(localStorage.getItem('accessToken')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
    });
  });

  describe('Cookie hint lifecycle (US-075 middleware hint)', () => {
    it('login sets isAuthenticated=true cookie', async () => {
      mockedLogin.mockResolvedValueOnce({
        accessToken: 'access-jwt',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().login('user@example.com', 'password');

      expect(document.cookie).toContain('isAuthenticated=true');
    });

    it('logout clears isAuthenticated cookie', async () => {
      mockedLogin.mockResolvedValueOnce({
        accessToken: 'access-jwt',
        expiresInSeconds: 900,
      });
      mockedLogout.mockResolvedValueOnce(undefined);

      await useAuthStore.getState().login('user@example.com', 'password');
      await useAuthStore.getState().logout();

      expect(document.cookie).toContain('max-age=0');
    });

    it('successful rehydration sets isAuthenticated cookie', async () => {
      mockedRefresh.mockResolvedValueOnce({
        accessToken: 'rehydrated-access',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().rehydrate();

      expect(document.cookie).toContain('isAuthenticated=true');
    });

    it('failed rehydration clears isAuthenticated cookie', async () => {
      resetDocumentCookie();
      document.cookie = 'isAuthenticated=true; path=/; SameSite=Strict';
      mockedRefresh.mockRejectedValueOnce(new Error('401'));

      await useAuthStore.getState().rehydrate();

      expect(document.cookie).toContain('max-age=0');
    });
  });
});
