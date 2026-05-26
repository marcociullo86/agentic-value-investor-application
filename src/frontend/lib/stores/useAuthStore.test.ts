import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from './useAuthStore';

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

function mockDocumentCookie(): void {
  Object.defineProperty(document, 'cookie', {
    writable: true,
    value: '',
  });
}

describe('useAuthStore', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      expiresAt: null,
      user: null,
      rehydrationStatus: 'pending',
      sessionExpired: false,
    });
    mockedLogin.mockReset();
    mockedLogout.mockReset();
    mockedRefresh.mockReset();
    mockDocumentCookie();
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('starts with null token and user', () => {
    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.user).toBeNull();
  });

  it('login stores access token (no refreshToken in state)', async () => {
    mockedLogin.mockResolvedValueOnce({
      accessToken: 'access-1',
      expiresInSeconds: 900,
    });

    await useAuthStore.getState().login('alice@example.com', 'pw');

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe('access-1');
    expect(state.user?.email).toBe('alice@example.com');
    expect('refreshToken' in state).toBe(false);
  });

  it('login sets isAuthenticated cookie hint', async () => {
    mockedLogin.mockResolvedValueOnce({
      accessToken: 'access-1',
      expiresInSeconds: 900,
    });

    await useAuthStore.getState().login('alice@example.com', 'pw');

    expect(document.cookie).toContain('isAuthenticated=true');
  });

  it('login sets rehydrationStatus to done', async () => {
    mockedLogin.mockResolvedValueOnce({
      accessToken: 'access-1',
      expiresInSeconds: 900,
    });

    await useAuthStore.getState().login('alice@example.com', 'pw');

    expect(useAuthStore.getState().rehydrationStatus).toBe('done');
  });

  it('logout clears all session state', async () => {
    useAuthStore.setState({
      accessToken: 'access-1',
      user: { id: '1', email: 'a@b.c', displayName: null, createdAt: '' },
    });
    mockedLogout.mockResolvedValueOnce(undefined);

    await useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.user).toBeNull();
    expect(mockedLogout).toHaveBeenCalledWith();
  });

  it('logout clears isAuthenticated cookie hint', async () => {
    document.cookie = 'isAuthenticated=true; path=/; SameSite=Strict';
    useAuthStore.setState({ accessToken: 'a', user: null });
    mockedLogout.mockResolvedValueOnce(undefined);

    await useAuthStore.getState().logout();

    expect(document.cookie).toContain('max-age=0');
  });

  it('logout still clears state when backend logout call fails', async () => {
    useAuthStore.setState({
      accessToken: 'a',
      user: null,
    });
    mockedLogout.mockRejectedValueOnce(new Error('network'));

    await useAuthStore.getState().logout();

    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('refresh updates access token (cookie-based, no body)', async () => {
    useAuthStore.setState({
      accessToken: 'old',
      user: null,
    });
    mockedRefresh.mockResolvedValueOnce({
      accessToken: 'access-new',
      expiresInSeconds: 900,
    });

    await useAuthStore.getState().refresh();

    expect(useAuthStore.getState().accessToken).toBe('access-new');
    expect(mockedRefresh).toHaveBeenCalledWith();
  });

  it('starts with sessionExpired=false', () => {
    expect(useAuthStore.getState().sessionExpired).toBe(false);
  });

  it('setSessionExpired toggles the flag', () => {
    useAuthStore.getState().setSessionExpired(true);
    expect(useAuthStore.getState().sessionExpired).toBe(true);
    useAuthStore.getState().setSessionExpired(false);
    expect(useAuthStore.getState().sessionExpired).toBe(false);
  });

  it('clearSession wipes token and user but keeps sessionExpired untouched', () => {
    useAuthStore.setState({
      accessToken: 'a',
      user: { id: '1', email: 'a@b.c', displayName: null, createdAt: '' },
      sessionExpired: true,
    });

    useAuthStore.getState().clearSession();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.user).toBeNull();
    expect(state.sessionExpired).toBe(true);
  });

  it('clearSession clears isAuthenticated cookie hint', () => {
    document.cookie = 'isAuthenticated=true; path=/; SameSite=Strict';

    useAuthStore.getState().clearSession();

    expect(document.cookie).toContain('max-age=0');
  });

  it('login resets sessionExpired to false', async () => {
    useAuthStore.setState({ sessionExpired: true });
    mockedLogin.mockResolvedValueOnce({
      accessToken: 'access-2',
      expiresInSeconds: 900,
    });

    await useAuthStore.getState().login('bob@example.com', 'pw');

    expect(useAuthStore.getState().sessionExpired).toBe(false);
  });

  describe('rehydrate', () => {
    it('skips refresh if accessToken already present', async () => {
      useAuthStore.setState({ accessToken: 'existing', rehydrationStatus: 'pending' });

      await useAuthStore.getState().rehydrate();

      expect(mockedRefresh).not.toHaveBeenCalled();
      expect(useAuthStore.getState().rehydrationStatus).toBe('done');
    });

    it('calls refresh and restores accessToken on success', async () => {
      mockedRefresh.mockResolvedValueOnce({
        accessToken: 'rehydrated-token',
        expiresInSeconds: 900,
      });

      await useAuthStore.getState().rehydrate();

      expect(useAuthStore.getState().accessToken).toBe('rehydrated-token');
      expect(useAuthStore.getState().rehydrationStatus).toBe('done');
      expect(document.cookie).toContain('isAuthenticated=true');
    });

    it('clears state on refresh failure', async () => {
      mockedRefresh.mockRejectedValueOnce(new Error('401'));

      await useAuthStore.getState().rehydrate();

      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(useAuthStore.getState().user).toBeNull();
      expect(useAuthStore.getState().rehydrationStatus).toBe('done');
    });

    it('transitions through rehydrating status', async () => {
      let resolveRefresh: (value: unknown) => void;
      mockedRefresh.mockReturnValueOnce(
        new Promise((resolve) => {
          resolveRefresh = resolve;
        }),
      );

      const rehydratePromise = useAuthStore.getState().rehydrate();

      expect(useAuthStore.getState().rehydrationStatus).toBe('rehydrating');

      resolveRefresh!({ accessToken: 'tok', expiresInSeconds: 900 });
      await rehydratePromise;

      expect(useAuthStore.getState().rehydrationStatus).toBe('done');
    });
  });
});
