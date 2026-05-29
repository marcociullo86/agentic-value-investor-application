import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { SWRConfig } from 'swr';
import type { ReactElement, ReactNode } from 'react';

/**
 * Unit tests for `useDeepAnalysis` over the async run/latest flow.
 *
 * Strategy:
 *  - Mock `@/lib/api/deep-analysis` to control responses deterministically.
 *  - Wrap the hook in a fresh `SWRConfig` per test (Map cache) to prevent
 *    state bleeding between tests.
 *  - Use real timers + `waitFor` for polling; the 3s interval combined with
 *    `act()` would be brittle with fake timers + setInterval here, so we
 *    sequence mock responses and assert via waitFor.
 */

vi.mock('@/lib/api/deep-analysis', async () => {
  const actual = await vi.importActual<
    typeof import('@/lib/api/deep-analysis')
  >('@/lib/api/deep-analysis');
  return {
    ...actual,
    getLatestDeepAnalysis: vi.fn(),
    startDeepAnalysisRun: vi.fn(),
  };
});

import {
  getLatestDeepAnalysis,
  startDeepAnalysisRun,
  type LatestDeepAnalysis,
  type DeepAnalysisResponse,
} from '@/lib/api/deep-analysis';
import { useDeepAnalysis } from './useDeepAnalysis';

const mockedGetLatest = getLatestDeepAnalysis as ReturnType<typeof vi.fn>;
const mockedStartRun = startDeepAnalysisRun as ReturnType<typeof vi.fn>;

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

const minimalResult: DeepAnalysisResponse = {
  ticker: 'AAPL',
  generatedAt: '2026-05-22T10:00:00Z',
  roe: {
    fiveYearAvg: 0.4,
    tenYearAvg: null,
    fiveYearDataPoints: 5,
    tenYearDataPoints: 0,
  },
  priceAction: {
    priceNow: 100,
    max52w: 120,
    min52w: 80,
    drawdownPct: -0.1,
    trend3mPct: 0.05,
    ma50: 95,
    ma200: 90,
    panicDiscount: false,
    deteriorationWarning: false,
    seriesDays: 252,
  },
  ruleEngineResults: [],
  verdict: {
    verdettoClasse: 'APPROVATO',
    positionSizePct: 4,
    partialBasis: false,
    motivazioneAggregata: 'ok',
    ruleCountGreen: 1,
    ruleCountYellow: 0,
    ruleCountRed: 0,
    livelloRischio: 'RISCHIO_BASSO',
    newsSentimentDominante: 'NEUTRAL',
  },
  positionSize: null,
  filingsUsed: [],
  mungerReport: null,
  newsSentiment: null,
  llmStatus: 'NOT_INVOKED',
  llmCalls: 0,
  totalDurationMs: 1500,
  llmCostEstimateUsd: null,
};

function nonePayload(): LatestDeepAnalysis {
  return {
    ticker: 'AAPL',
    status: 'NONE',
    runId: null,
    invokeLlm: false,
    requestedAt: null,
    completedAt: null,
    result: null,
    error: null,
  };
}

function runningPayload(): LatestDeepAnalysis {
  return {
    ticker: 'AAPL',
    status: 'RUNNING',
    runId: 'r-1',
    invokeLlm: false,
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: null,
    result: null,
    error: null,
  };
}

function successPayload(): LatestDeepAnalysis {
  return {
    ticker: 'AAPL',
    status: 'SUCCESS',
    runId: 'r-1',
    invokeLlm: false,
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: '2026-05-22T10:00:30Z',
    result: minimalResult,
    error: null,
  };
}

function failedPayload(
  reason: string,
  message: string | null = null,
): LatestDeepAnalysis {
  return {
    ticker: 'AAPL',
    status: 'FAILED',
    runId: 'r-1',
    invokeLlm: false,
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: '2026-05-22T10:00:05Z',
    result: null,
    error: { reason, message },
  };
}

describe('useDeepAnalysis — async run/latest flow', () => {
  beforeEach(() => {
    mockedGetLatest.mockReset();
    mockedStartRun.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns NONE status and no data on empty ticker history', async () => {
    mockedGetLatest.mockResolvedValueOnce(nonePayload());

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.latestStatus).toBe('NONE');
    });
    expect(result.current.data).toBeUndefined();
    expect(result.current.isRunning).toBe(false);
    expect(result.current.error).toBeUndefined();
    expect(mockedStartRun).not.toHaveBeenCalled();
  });

  it('exposes the success result when latest is SUCCESS', async () => {
    mockedGetLatest.mockResolvedValueOnce(successPayload());

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.data?.verdict.verdettoClasse).toBe('APPROVATO');
    });
    expect(result.current.latestStatus).toBe('SUCCESS');
    expect(result.current.completedAt).toBe('2026-05-22T10:00:30Z');
    expect(result.current.error).toBeUndefined();
  });

  it('maps FAILED reason NOT_FOUND to 404 user-facing message', async () => {
    mockedGetLatest.mockResolvedValueOnce(failedPayload('NOT_FOUND'));

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.error?.status).toBe(404);
    });
    expect(result.current.error?.message).toMatch(/Ticker non trovato/i);
    expect(result.current.data).toBeUndefined();
  });

  it('maps FAILED reason NO_SEC_FILINGS to 422 message', async () => {
    mockedGetLatest.mockResolvedValueOnce(failedPayload('NO_SEC_FILINGS'));

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.error?.status).toBe(422);
    });
    expect(result.current.error?.message).toMatch(/Nessun filing SEC/i);
  });

  it('flags isFrozenByAdmin when reason is LLM_FROZEN_BY_ADMIN', async () => {
    mockedGetLatest.mockResolvedValueOnce(
      failedPayload('LLM_FROZEN_BY_ADMIN', 'Budget esaurito'),
    );

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.isFrozenByAdmin).toBe(true);
    });
    expect(result.current.error?.status).toBe(503);
  });

  it('runNow POSTs invoke_llm=false then polls until SUCCESS', async () => {
    // 1st GET (initial mount) = NONE
    // After POST: optimistic RUNNING, then SWR revalidates → RUNNING → SUCCESS
    mockedGetLatest
      .mockResolvedValueOnce(nonePayload()) // initial
      .mockResolvedValueOnce(runningPayload()) // immediate revalidation after run()
      .mockResolvedValue(successPayload()); // any subsequent poll
    mockedStartRun.mockResolvedValueOnce({
      runId: 'r-1',
      ticker: 'AAPL',
      status: 'RUNNING',
      invokeLlm: false,
    });

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.latestStatus).toBe('NONE');
    });

    await act(async () => {
      await result.current.runNow();
    });

    expect(mockedStartRun).toHaveBeenCalledWith('AAPL', false);

    await waitFor(
      () => {
        expect(result.current.latestStatus).toBe('SUCCESS');
      },
      { timeout: 10_000 },
    );
    expect(result.current.data?.verdict.verdettoClasse).toBe('APPROVATO');
  });

  it('runWithLlm POSTs invoke_llm=true', async () => {
    mockedGetLatest
      .mockResolvedValueOnce(nonePayload())
      .mockResolvedValue(successPayload());
    mockedStartRun.mockResolvedValueOnce({
      runId: 'r-2',
      ticker: 'AAPL',
      status: 'RUNNING',
      invokeLlm: true,
    });

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.latestStatus).toBe('NONE');
    });

    await act(async () => {
      await result.current.runWithLlm();
    });

    expect(mockedStartRun).toHaveBeenCalledWith('AAPL', true);
  });

  it('isRunning reflects RUNNING status from latest', async () => {
    mockedGetLatest.mockResolvedValueOnce(runningPayload());

    const { result } = renderHook(() => useDeepAnalysis('AAPL'), { wrapper });

    await waitFor(() => {
      expect(result.current.isRunning).toBe(true);
    });
    expect(result.current.latestStatus).toBe('RUNNING');
    expect(result.current.requestedAt).toBe('2026-05-22T09:59:55Z');
  });

  it('cleans up polling timer on unmount (no errors after teardown)', async () => {
    mockedGetLatest.mockResolvedValue(runningPayload());

    const { result, unmount } = renderHook(() => useDeepAnalysis('AAPL'), {
      wrapper,
    });

    await waitFor(() => {
      expect(result.current.isRunning).toBe(true);
    });

    // If the interval keeps firing after unmount, mockedGetLatest would keep
    // accumulating calls. We capture the count immediately and ensure it does
    // not grow after a small wait window.
    unmount();
    const callsAtUnmount = mockedGetLatest.mock.calls.length;
    await new Promise((res) => setTimeout(res, 50));
    expect(mockedGetLatest.mock.calls.length).toBe(callsAtUnmount);
  });
});
