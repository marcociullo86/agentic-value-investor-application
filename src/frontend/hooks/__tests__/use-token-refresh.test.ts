import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';

vi.mock('@/lib/stores/useAuthStore', () => {
  const state: Record<string, unknown> = {
    accessToken: null,
    expiresAt: null,
  };

  const listeners = new Set<() => void>();

  const store = {
    getState: () => state,
    setState: (partial: Record<string, unknown>) => {
      Object.assign(state, partial);
      listeners.forEach((fn) => fn());
    },
    subscribe: (fn: () => void) => {
      listeners.add(fn);
      return () => listeners.delete(fn);
    },
    getInitialState: () => state,
    destroy: vi.fn(),
  };

  const useAuthStore = Object.assign(
    (selector?: (s: typeof state) => unknown) => {
      if (selector) return selector(state);
      return state;
    },
    store,
  );

  return { useAuthStore };
});

vi.mock('@/lib/api/token-refresh-mutex', () => ({
  acquireFreshToken: vi.fn(),
}));

import { useAuthStore } from '@/lib/stores/useAuthStore';
import { acquireFreshToken } from '@/lib/api/token-refresh-mutex';

const mockedAcquire = acquireFreshToken as ReturnType<typeof vi.fn>;

describe('useTokenRefresh — timer pre-expiry (AC: sessione 20+ min)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockedAcquire.mockReset();
    useAuthStore.setState({ accessToken: null, expiresAt: null });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('schedules refresh 60s before expiry (120s token → fires at ~60s)', async () => {
    const { useTokenRefresh } = await import('@/hooks/use-token-refresh');

    mockedAcquire.mockResolvedValue('new-token');

    const now = Date.now();
    useAuthStore.setState({
      accessToken: 'tok-1',
      expiresAt: now + 120_000,
    });

    renderHook(() => useTokenRefresh());

    expect(mockedAcquire).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(60_000);

    expect(mockedAcquire).toHaveBeenCalledTimes(1);
  });

  it('clamps delay to MIN_TIMER_MS when expiry is very close', async () => {
    const { useTokenRefresh } = await import('@/hooks/use-token-refresh');

    mockedAcquire.mockResolvedValue('tok');

    const now = Date.now();
    useAuthStore.setState({
      accessToken: 'tok-1',
      expiresAt: now + 10_000,
    });

    renderHook(() => useTokenRefresh());

    await vi.advanceTimersByTimeAsync(5_000);

    expect(mockedAcquire).toHaveBeenCalledTimes(1);
  });

  it('does not schedule refresh when no token is present', async () => {
    const { useTokenRefresh } = await import('@/hooks/use-token-refresh');

    useAuthStore.setState({ accessToken: null, expiresAt: null });

    renderHook(() => useTokenRefresh());

    await vi.advanceTimersByTimeAsync(120_000);

    expect(mockedAcquire).not.toHaveBeenCalled();
  });

  it('cancels previous timer when expiresAt changes', async () => {
    const { useTokenRefresh } = await import('@/hooks/use-token-refresh');

    mockedAcquire.mockResolvedValue('tok');

    const now = Date.now();
    useAuthStore.setState({
      accessToken: 'tok-1',
      expiresAt: now + 120_000,
    });

    const { rerender } = renderHook(() => useTokenRefresh());

    await vi.advanceTimersByTimeAsync(30_000);
    expect(mockedAcquire).not.toHaveBeenCalled();

    useAuthStore.setState({
      accessToken: 'tok-2',
      expiresAt: now + 240_000,
    });
    rerender();

    await vi.advanceTimersByTimeAsync(30_000);
    expect(mockedAcquire).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(150_000);
    expect(mockedAcquire).toHaveBeenCalledTimes(1);
  });

  it('clears timer on unmount', async () => {
    const { useTokenRefresh } = await import('@/hooks/use-token-refresh');

    mockedAcquire.mockResolvedValue('tok');

    const now = Date.now();
    useAuthStore.setState({
      accessToken: 'tok-1',
      expiresAt: now + 120_000,
    });

    const { unmount } = renderHook(() => useTokenRefresh());
    unmount();

    await vi.advanceTimersByTimeAsync(120_000);

    expect(mockedAcquire).not.toHaveBeenCalled();
  });
});

describe('useTokenRefresh — long session 3-cycle simulation (AC: 20+ min OK)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockedAcquire.mockReset();
    useAuthStore.setState({ accessToken: null, expiresAt: null });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('survives 3 consecutive refresh cycles without errors', async () => {
    const { useTokenRefresh } = await import('@/hooks/use-token-refresh');

    const TOKEN_LIFETIME_MS = 120_000;
    const BUFFER_MS = 60_000;

    useAuthStore.setState({
      accessToken: 'tok-cycle-0',
      expiresAt: Date.now() + TOKEN_LIFETIME_MS,
    });

    const { rerender } = renderHook(() => useTokenRefresh());

    for (let cycle = 1; cycle <= 3; cycle++) {
      mockedAcquire.mockResolvedValueOnce(`tok-cycle-${cycle}`);

      await vi.advanceTimersByTimeAsync(TOKEN_LIFETIME_MS - BUFFER_MS);

      expect(mockedAcquire).toHaveBeenCalledTimes(cycle);

      useAuthStore.setState({
        accessToken: `tok-cycle-${cycle}`,
        expiresAt: Date.now() + TOKEN_LIFETIME_MS,
      });
      rerender();
    }

    expect(mockedAcquire).toHaveBeenCalledTimes(3);
  });
});

describe('useTokenRefresh — fallback on failure (AC: redirect /login)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockedAcquire.mockReset();
    useAuthStore.setState({ accessToken: null, expiresAt: null });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('swallows the error when acquireFreshToken rejects (mutex handles logout)', async () => {
    const { useTokenRefresh } = await import('@/hooks/use-token-refresh');

    mockedAcquire.mockRejectedValueOnce(new Error('refresh failed'));

    const now = Date.now();
    useAuthStore.setState({
      accessToken: 'tok-1',
      expiresAt: now + 120_000,
    });

    renderHook(() => useTokenRefresh());

    await vi.advanceTimersByTimeAsync(60_000);

    expect(mockedAcquire).toHaveBeenCalledTimes(1);
  });
});
