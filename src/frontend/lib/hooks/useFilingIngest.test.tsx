import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { SWRConfig } from 'swr';
import type { ReactElement, ReactNode } from 'react';

/**
 * Unit tests for `useFilingIngest` over the async ingest/latest flow.
 *
 * Mirrors the strategy of useDeepAnalysis.test.tsx: mock the API module, wrap
 * the hook in a fresh SWRConfig per test, use real timers + waitFor for the
 * 3s polling loop.
 */

vi.mock('@/lib/api/deep-analysis', async () => {
  const actual = await vi.importActual<
    typeof import('@/lib/api/deep-analysis')
  >('@/lib/api/deep-analysis');
  return {
    ...actual,
    getLatestIngest: vi.fn(),
    startIngest: vi.fn(),
  };
});

import {
  getLatestIngest,
  startIngest,
  type LatestIngest,
} from '@/lib/api/deep-analysis';
import { useFilingIngest } from './useFilingIngest';

const mockedGetLatest = getLatestIngest as ReturnType<typeof vi.fn>;
const mockedStart = startIngest as ReturnType<typeof vi.fn>;

function wrapper({ children }: { children: ReactNode }): ReactElement {
  return (
    <SWRConfig
      value={{
        provider: () => new Map(),
        dedupingInterval: 0,
        revalidateOnFocus: false,
        revalidateOnReconnect: false,
        shouldRetryOnError: false,
      }}
    >
      {children}
    </SWRConfig>
  );
}

function nonePayload(): LatestIngest {
  return {
    ticker: 'AAPL',
    status: 'NONE',
    runId: null,
    requestedAt: null,
    completedAt: null,
    summary: null,
    error: null,
  };
}

function runningPayload(): LatestIngest {
  return {
    ticker: 'AAPL',
    status: 'RUNNING',
    runId: 'ing-1',
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: null,
    summary: null,
    error: null,
  };
}

function successPayload(): LatestIngest {
  return {
    ticker: 'AAPL',
    status: 'SUCCESS',
    runId: 'ing-1',
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: '2026-05-22T10:00:30Z',
    summary: {
      filingsTotal: 12,
      chunksIndexed: 480,
      chunksSkipped: 3,
      indexedAt: '2026-05-22T10:00:30Z',
    },
    error: null,
  };
}

function failedPayload(reason: string, message: string | null): LatestIngest {
  return {
    ticker: 'AAPL',
    status: 'FAILED',
    runId: 'ing-1',
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: '2026-05-22T10:00:05Z',
    summary: null,
    error: { reason, message },
  };
}

describe('useFilingIngest — async ingest/latest flow', () => {
  beforeEach(() => {
    mockedGetLatest.mockReset();
    mockedStart.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns NONE status and no summary on empty ticker history', async () => {
    mockedGetLatest.mockResolvedValueOnce(nonePayload());

    const { result } = renderHook(() => useFilingIngest('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.status).toBe('NONE');
    });
    expect(result.current.summary).toBeNull();
    expect(result.current.isRunning).toBe(false);
    expect(result.current.error).toBeUndefined();
    expect(mockedStart).not.toHaveBeenCalled();
  });

  it('exposes summary when latest is SUCCESS', async () => {
    mockedGetLatest.mockResolvedValueOnce(successPayload());

    const { result } = renderHook(() => useFilingIngest('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.status).toBe('SUCCESS');
    });
    expect(result.current.summary?.filingsTotal).toBe(12);
    expect(result.current.summary?.chunksIndexed).toBe(480);
    expect(result.current.summary?.indexedAt).toBe('2026-05-22T10:00:30Z');
    expect(result.current.completedAt).toBe('2026-05-22T10:00:30Z');
    expect(result.current.error).toBeUndefined();
  });

  it('isRunning reflects RUNNING status from latest', async () => {
    mockedGetLatest.mockResolvedValueOnce(runningPayload());

    const { result } = renderHook(() => useFilingIngest('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.isRunning).toBe(true);
    });
    expect(result.current.status).toBe('RUNNING');
    expect(result.current.requestedAt).toBe('2026-05-22T09:59:55Z');
    expect(result.current.summary).toBeNull();
  });

  it('runIngest POSTs ingest then polls until SUCCESS', async () => {
    mockedGetLatest
      .mockResolvedValueOnce(nonePayload())
      .mockResolvedValueOnce(runningPayload())
      .mockResolvedValue(successPayload());
    mockedStart.mockResolvedValueOnce({
      runId: 'ing-1',
      ticker: 'AAPL',
      status: 'RUNNING',
      invokeLlm: false,
      kind: 'INGEST',
    });

    const { result } = renderHook(() => useFilingIngest('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.status).toBe('NONE');
    });

    await act(async () => {
      await result.current.runIngest();
    });

    expect(mockedStart).toHaveBeenCalledWith('AAPL');

    await waitFor(
      () => {
        expect(result.current.status).toBe('SUCCESS');
      },
      { timeout: 10_000 },
    );
    expect(result.current.summary?.filingsTotal).toBe(12);
  });

  it('maps FAILED to a user-facing error message', async () => {
    mockedGetLatest.mockResolvedValueOnce(
      failedPayload('SEC_UNREACHABLE', 'EDGAR offline'),
    );

    const { result } = renderHook(() => useFilingIngest('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.status).toBe('FAILED');
    });
    expect(result.current.error?.reason).toBe('SEC_UNREACHABLE');
    expect(result.current.error?.message).toBe('EDGAR offline');
  });

  it('cleans up polling timer on unmount (no errors after teardown)', async () => {
    mockedGetLatest.mockResolvedValue(runningPayload());

    const { result, unmount } = renderHook(() => useFilingIngest('AAPL'), {
      wrapper,
    });

    await waitFor(() => {
      expect(result.current.isRunning).toBe(true);
    });

    unmount();
    const callsAtUnmount = mockedGetLatest.mock.calls.length;
    await new Promise((res) => setTimeout(res, 50));
    expect(mockedGetLatest.mock.calls.length).toBe(callsAtUnmount);
  });
});
