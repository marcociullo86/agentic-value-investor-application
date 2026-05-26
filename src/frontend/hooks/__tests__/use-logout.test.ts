import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockMutate = vi.fn();
vi.mock('swr', () => ({ mutate: mockMutate }));

vi.mock('@/lib/api/auth', () => ({
  logout: vi.fn(),
}));

const mockClearSession = vi.fn();

vi.mock('@/lib/stores/useAuthStore', () => {
  const useAuthStore = (selector?: (s: Record<string, unknown>) => unknown) => {
    if (selector) {
      return selector({ clearSession: mockClearSession });
    }
    return { clearSession: mockClearSession };
  };
  return { useAuthStore };
});

import { logout as apiLogout } from '@/lib/api/auth';

const mockedApiLogout = apiLogout as ReturnType<typeof vi.fn>;

describe('useLogout — revoca, pulizia, redirect (US-078 AC)', () => {
  let replaceStateSpy: ReturnType<typeof vi.spyOn>;
  let removeItemSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockedApiLogout.mockReset();
    mockClearSession.mockReset();
    mockMutate.mockReset().mockResolvedValue(undefined);
    mockPush.mockReset();

    replaceStateSpy = vi.spyOn(window.history, 'replaceState').mockImplementation(() => {});
    removeItemSpy = vi.spyOn(Storage.prototype, 'removeItem');
  });

  afterEach(() => {
    replaceStateSpy.mockRestore();
    removeItemSpy.mockRestore();
    vi.restoreAllMocks();
  });

  async function invokeLogout() {
    const { useLogout } = await import('@/hooks/use-logout');
    const { result } = renderHook(() => useLogout());
    await act(async () => {
      await result.current.logout();
    });
  }

  // --- AC: Revoca BE ---

  it('calls POST /api/auth/logout for BE revocation', async () => {
    mockedApiLogout.mockResolvedValueOnce(undefined);
    await invokeLogout();
    expect(mockedApiLogout).toHaveBeenCalledTimes(1);
  });

  // --- AC: Store vuoto ---

  it('clears Zustand auth store via clearSession()', async () => {
    mockedApiLogout.mockResolvedValueOnce(undefined);
    await invokeLogout();
    expect(mockClearSession).toHaveBeenCalledTimes(1);
  });

  // --- AC: SWR cache pulita ---

  it('invalidates SWR cache globally with revalidate=false', async () => {
    mockedApiLogout.mockResolvedValueOnce(undefined);
    await invokeLogout();

    expect(mockMutate).toHaveBeenCalledTimes(1);
    const [matcherFn, data, opts] = mockMutate.mock.calls[0];
    expect(typeof matcherFn).toBe('function');
    expect(matcherFn('any-key')).toBe(true);
    expect(data).toBeUndefined();
    expect(opts).toEqual({ revalidate: false });
  });

  // --- AC: sessionStorage pulito ---

  it('removes __idle_session_start from sessionStorage', async () => {
    sessionStorage.setItem('__idle_session_start', String(Date.now()));
    mockedApiLogout.mockResolvedValueOnce(undefined);
    await invokeLogout();
    expect(removeItemSpy).toHaveBeenCalledWith('__idle_session_start');
    expect(sessionStorage.getItem('__idle_session_start')).toBeNull();
  });

  // --- AC: Back button bloccato ---

  it('calls history.replaceState with /login to block back button', async () => {
    mockedApiLogout.mockResolvedValueOnce(undefined);
    await invokeLogout();
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', '/login');
  });

  it('pushes /login via Next.js router', async () => {
    mockedApiLogout.mockResolvedValueOnce(undefined);
    await invokeLogout();
    expect(mockPush).toHaveBeenCalledWith('/login');
  });

  // --- AC: Sequenza corretta ---

  it('executes cleanup in correct order: revoke → clear → mutate → storage → history → push', async () => {
    const order: string[] = [];

    mockedApiLogout.mockImplementation(async () => {
      order.push('apiLogout');
    });
    mockClearSession.mockImplementation(() => {
      order.push('clearSession');
    });
    mockMutate.mockImplementation(async () => {
      order.push('mutate');
    });
    replaceStateSpy.mockImplementation(() => {
      order.push('replaceState');
    });
    mockPush.mockImplementation(() => {
      order.push('push');
    });

    await invokeLogout();

    expect(order).toEqual([
      'apiLogout',
      'clearSession',
      'mutate',
      'replaceState',
      'push',
    ]);
  });
});

describe('useLogout — resilienza (US-078 AC: logout locale se revoca fallisce)', () => {
  let replaceStateSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockedApiLogout.mockReset();
    mockClearSession.mockReset();
    mockMutate.mockReset().mockResolvedValue(undefined);
    mockPush.mockReset();
    replaceStateSpy = vi.spyOn(window.history, 'replaceState').mockImplementation(() => {});
  });

  afterEach(() => {
    replaceStateSpy.mockRestore();
    vi.restoreAllMocks();
  });

  async function invokeLogout() {
    const { useLogout } = await import('@/hooks/use-logout');
    const { result } = renderHook(() => useLogout());
    await act(async () => {
      await result.current.logout();
    });
  }

  it('proceeds with local cleanup when BE revocation rejects (network error)', async () => {
    mockedApiLogout.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    await invokeLogout();

    expect(mockClearSession).toHaveBeenCalledTimes(1);
    expect(mockMutate).toHaveBeenCalledTimes(1);
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', '/login');
    expect(mockPush).toHaveBeenCalledWith('/login');
  });

  it('proceeds with local cleanup when BE returns 500', async () => {
    mockedApiLogout.mockRejectedValueOnce(
      Object.assign(new Error('Internal Server Error'), { response: { status: 500 } }),
    );

    await invokeLogout();

    expect(mockClearSession).toHaveBeenCalledTimes(1);
    expect(mockMutate).toHaveBeenCalledTimes(1);
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', '/login');
    expect(mockPush).toHaveBeenCalledWith('/login');
  });

  it('does not throw when revocation fails', async () => {
    mockedApiLogout.mockRejectedValueOnce(new Error('kaboom'));
    await expect(invokeLogout()).resolves.toBeUndefined();
  });
});
