import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/lib/api/auth', () => ({
  refreshTokens: vi.fn(),
}));

vi.mock('@/lib/stores/useAuthStore', () => {
  const state: Record<string, unknown> = {
    accessToken: null,
    expiresAt: null,
    user: null,
    rehydrationStatus: 'done',
    sessionExpired: false,
    clearSession: vi.fn(),
    setSessionExpired: vi.fn(),
  };

  const store = {
    getState: () => state,
    setState: (partial: Record<string, unknown>) => Object.assign(state, partial),
    subscribe: vi.fn(),
  };

  return { useAuthStore: Object.assign(vi.fn(() => state), store) };
});

import { refreshTokens } from '@/lib/api/auth';
import { useAuthStore } from '@/lib/stores/useAuthStore';

const mockedRefresh = refreshTokens as ReturnType<typeof vi.fn>;
const clearSession = useAuthStore.getState().clearSession as ReturnType<typeof vi.fn>;
const setSessionExpired = useAuthStore.getState().setSessionExpired as ReturnType<typeof vi.fn>;

async function freshModule() {
  vi.resetModules();

  vi.doMock('@/lib/api/auth', () => ({
    refreshTokens: mockedRefresh,
  }));

  vi.doMock('@/lib/stores/useAuthStore', () => ({
    useAuthStore: Object.assign(vi.fn(() => useAuthStore.getState()), {
      getState: useAuthStore.getState,
      setState: useAuthStore.setState,
      subscribe: vi.fn(),
    }),
  }));

  return await import('../token-refresh-mutex');
}

describe('token-refresh-mutex — concurrency (AC: no doppio refresh)', () => {
  beforeEach(() => {
    mockedRefresh.mockReset();
    clearSession.mockReset();
    setSessionExpired.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('two concurrent acquireFreshToken() calls trigger only one POST /refresh', async () => {
    const { acquireFreshToken } = await freshModule();

    let resolveRefresh!: (v: { accessToken: string; expiresInSeconds: number }) => void;
    mockedRefresh.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveRefresh = resolve;
      }),
    );

    const p1 = acquireFreshToken();
    const p2 = acquireFreshToken();

    resolveRefresh({ accessToken: 'new-token-1', expiresInSeconds: 900 });

    const [t1, t2] = await Promise.all([p1, p2]);

    expect(mockedRefresh).toHaveBeenCalledTimes(1);
    expect(t1).toBe('new-token-1');
    expect(t2).toBe('new-token-1');
  });

  it('both concurrent callers receive the same new token', async () => {
    const { acquireFreshToken } = await freshModule();

    mockedRefresh.mockResolvedValueOnce({
      accessToken: 'shared-token',
      expiresInSeconds: 600,
    });

    const results = await Promise.all([acquireFreshToken(), acquireFreshToken()]);

    expect(results[0]).toBe('shared-token');
    expect(results[1]).toBe('shared-token');
  });

  it('after mutex release a new call triggers a new refresh', async () => {
    const { acquireFreshToken } = await freshModule();

    mockedRefresh
      .mockResolvedValueOnce({ accessToken: 'first', expiresInSeconds: 900 })
      .mockResolvedValueOnce({ accessToken: 'second', expiresInSeconds: 900 });

    const t1 = await acquireFreshToken();
    expect(t1).toBe('first');

    const t2 = await acquireFreshToken();
    expect(t2).toBe('second');

    expect(mockedRefresh).toHaveBeenCalledTimes(2);
  });

  it('isRefreshing() returns true while refresh is in-flight', async () => {
    const { acquireFreshToken, isRefreshing } = await freshModule();

    let resolveRefresh!: (v: { accessToken: string; expiresInSeconds: number }) => void;
    mockedRefresh.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveRefresh = resolve;
      }),
    );

    const p = acquireFreshToken();
    expect(isRefreshing()).toBe(true);

    resolveRefresh({ accessToken: 'tok', expiresInSeconds: 900 });
    await p;

    expect(isRefreshing()).toBe(false);
  });
});

describe('token-refresh-mutex — store update on success', () => {
  beforeEach(() => {
    mockedRefresh.mockReset();
    clearSession.mockReset();
    setSessionExpired.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('updates accessToken and expiresAt in the auth store on success', async () => {
    const { acquireFreshToken } = await freshModule();

    const nowMs = Date.now();
    vi.spyOn(Date, 'now').mockReturnValue(nowMs);

    mockedRefresh.mockResolvedValueOnce({
      accessToken: 'fresh-access',
      expiresInSeconds: 900,
    });

    await acquireFreshToken();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe('fresh-access');
    expect(state.expiresAt).toBe(nowMs + 900_000);
  });
});

describe('token-refresh-mutex — fallback on failure (AC: redirect /login)', () => {
  beforeEach(() => {
    mockedRefresh.mockReset();
    clearSession.mockReset();
    setSessionExpired.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('calls clearSession() when refresh fails', async () => {
    const { acquireFreshToken } = await freshModule();

    mockedRefresh.mockRejectedValueOnce(new Error('401 Unauthorized'));

    await expect(acquireFreshToken()).rejects.toThrow('401 Unauthorized');
    expect(clearSession).toHaveBeenCalledOnce();
  });

  it('sets sessionExpired=true when refresh fails', async () => {
    const { acquireFreshToken } = await freshModule();

    mockedRefresh.mockRejectedValueOnce(new Error('token expired'));

    await expect(acquireFreshToken()).rejects.toThrow();
    expect(setSessionExpired).toHaveBeenCalledWith(true);
  });

  it('concurrent callers all reject when refresh fails', async () => {
    const { acquireFreshToken } = await freshModule();

    mockedRefresh.mockRejectedValueOnce(new Error('server down'));

    const results = await Promise.allSettled([
      acquireFreshToken(),
      acquireFreshToken(),
    ]);

    expect(results[0]!.status).toBe('rejected');
    expect(results[1]!.status).toBe('rejected');
    expect(mockedRefresh).toHaveBeenCalledTimes(1);
  });

  it('mutex is released after failure so the next call can retry', async () => {
    const { acquireFreshToken, isRefreshing } = await freshModule();

    mockedRefresh
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce({ accessToken: 'recovered', expiresInSeconds: 900 });

    await expect(acquireFreshToken()).rejects.toThrow();
    expect(isRefreshing()).toBe(false);

    const token = await acquireFreshToken();
    expect(token).toBe('recovered');
  });
});
