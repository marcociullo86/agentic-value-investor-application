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

describe('useAuthStore', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      user: null,
    });
    mockedLogin.mockReset();
    mockedLogout.mockReset();
    mockedRefresh.mockReset();
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('starts with null tokens and user', () => {
    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.user).toBeNull();
  });

  it('login stores both access and refresh tokens', async () => {
    mockedLogin.mockResolvedValueOnce({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      expiresInSeconds: 900,
    });

    await useAuthStore.getState().login('alice@example.com', 'pw');

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe('access-1');
    expect(state.refreshToken).toBe('refresh-1');
    expect(state.user?.email).toBe('alice@example.com');
  });

  it('logout clears all session state', async () => {
    useAuthStore.setState({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      user: { id: '1', email: 'a@b.c', displayName: null, createdAt: '' },
    });
    mockedLogout.mockResolvedValueOnce(undefined);

    await useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.user).toBeNull();
    expect(mockedLogout).toHaveBeenCalledWith('refresh-1');
  });

  it('logout still clears state when backend logout call fails', async () => {
    useAuthStore.setState({
      accessToken: 'a',
      refreshToken: 'r',
      user: null,
    });
    mockedLogout.mockRejectedValueOnce(new Error('network'));

    await useAuthStore.getState().logout();

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().refreshToken).toBeNull();
  });

  it('refresh swaps the token pair', async () => {
    useAuthStore.setState({
      accessToken: 'old',
      refreshToken: 'r-old',
      user: null,
    });
    mockedRefresh.mockResolvedValueOnce({
      accessToken: 'access-new',
      refreshToken: 'refresh-new',
      expiresInSeconds: 900,
    });

    await useAuthStore.getState().refresh();

    expect(useAuthStore.getState().accessToken).toBe('access-new');
    expect(useAuthStore.getState().refreshToken).toBe('refresh-new');
  });

  it('refresh throws when no refresh token is held', async () => {
    await expect(useAuthStore.getState().refresh()).rejects.toThrow(
      'No refresh token available',
    );
  });
});
