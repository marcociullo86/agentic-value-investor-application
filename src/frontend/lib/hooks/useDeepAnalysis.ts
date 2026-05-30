'use client';

import { useCallback, useEffect, useRef } from 'react';
import useSWR from 'swr';
import { isAxiosError } from 'axios';
import {
  getLatestDeepAnalysis,
  startDeepAnalysisRun,
  type DeepAnalysisResponse,
  type DeepAnalysisRunDto,
  type LatestDeepAnalysis,
  type LatestStatus,
} from '@/lib/api/deep-analysis';

/**
 * SWR hook for the asynchronous Deep Analysis flow.
 *
 * Backend contract:
 *  - POST /api/analysis/{ticker}/deep/runs?invoke_llm=… → 202 (fire-and-forget)
 *  - GET  /api/analysis/{ticker}/deep/latest             → snapshot of the
 *    last known run (status NONE | RUNNING | SUCCESS | FAILED).
 *
 * Behaviour:
 *  - On mount / navigate back the hook fetches `latest` and surfaces whatever
 *    the backend has on file. No auto-rerun.
 *  - `runNow()` / `runWithLlm()` POST a new run and start a 3-second polling
 *    loop on `latest` until the status leaves `RUNNING`. The interval is
 *    cleared on terminal status or on unmount.
 *  - The backend deduplicates concurrent POSTs; the UI keeps the buttons
 *    disabled while `isRunning` so the user simply cannot retrigger.
 */

const POLL_INTERVAL_MS = 3_000;

export interface DeepAnalysisError {
  readonly status: number | null;
  readonly reason: string | null;
  readonly message: string;
}

function fromHttpError(err: unknown): DeepAnalysisError {
  if (isAxiosError(err)) {
    const status = err.response?.status ?? null;
    const body = err.response?.data as
      | { reason?: string; detail?: string }
      | undefined;
    const reason = body?.reason ?? null;

    if (status === 404) {
      return {
        status,
        reason: reason ?? 'not_found',
        message: 'Ticker non trovato. Verifica il simbolo e riprova.',
      };
    }
    if (status === 422) {
      return {
        status,
        reason: reason ?? 'no_sec_filings',
        message:
          'Nessun filing SEC disponibile per questo ticker. La deep analysis richiede almeno un 10-K o 10-Q.',
      };
    }
    if (status === 503) {
      return {
        status,
        reason: reason ?? 'llm_unavailable',
        message:
          'Servizio analisi temporaneamente non disponibile. Riprova più tardi.',
      };
    }
    if (typeof status === 'number') {
      return {
        status,
        reason,
        message: `Errore server (${status}). Riprova.`,
      };
    }
  }
  return {
    status: null,
    reason: null,
    message: 'Errore di rete. Verifica la connessione.',
  };
}

function fromLatestFailure(
  latest: LatestDeepAnalysis,
): DeepAnalysisError | undefined {
  if (latest.status !== 'FAILED' || latest.error === null) return undefined;
  const reason = latest.error.reason;
  if (reason === 'NOT_FOUND' || reason === 'not_found') {
    return {
      status: 404,
      reason,
      message: 'Ticker non trovato. Verifica il simbolo e riprova.',
    };
  }
  if (reason === 'NO_SEC_FILINGS' || reason === 'no_sec_filings') {
    return {
      status: 422,
      reason,
      message:
        'Nessun filing SEC disponibile per questo ticker. La deep analysis richiede almeno un 10-K o 10-Q.',
    };
  }
  if (
    reason === 'LLM_FROZEN_BY_ADMIN' ||
    reason === 'LLM_UNAVAILABLE' ||
    reason === 'llm_unavailable'
  ) {
    return {
      status: 503,
      reason,
      message:
        latest.error.message ??
        'Servizio analisi temporaneamente non disponibile. Riprova più tardi.',
    };
  }
  if (reason === 'NOT_INDEXED' || reason === 'not_indexed') {
    return {
      status: 409,
      reason,
      message: 'Indicizza prima i filing per usare l’analisi LLM.',
    };
  }
  return {
    status: null,
    reason,
    message:
      latest.error.message ??
      'Esecuzione fallita. Riprova ora oppure più tardi.',
  };
}

export interface UseDeepAnalysisResult {
  /** Risultato deterministico/LLM dell'ultimo run SUCCESS. */
  readonly data: DeepAnalysisResponse | undefined;
  /** Status della latest snapshot ('NONE' quando non esiste alcun run). */
  readonly latestStatus: LatestStatus;
  /** Convenienza: `latestStatus === 'RUNNING'`. */
  readonly isRunning: boolean;
  /** Loading iniziale SWR (prima fetch `latest`). */
  readonly isLoading: boolean;
  /** Errore mappato (HTTP della GET oppure latest.error in caso di FAILED). */
  readonly error: DeepAnalysisError | undefined;
  /** Vero se l'errore è dovuto al freeze admin del budget LLM. */
  readonly isFrozenByAdmin: boolean;
  /** Vero se l'errore è dovuto a filing mai indicizzati per il ticker. */
  readonly isNotIndexed: boolean;
  /** ISO timestamp di inizio run (presente per RUNNING/SUCCESS/FAILED). */
  readonly requestedAt: string | null;
  /** ISO timestamp di fine run (presente per SUCCESS/FAILED). */
  readonly completedAt: string | null;
  /** POST runs invoke_llm=false. Avvia poi il polling. */
  readonly runNow: () => Promise<DeepAnalysisResponse | undefined>;
  /** POST runs invoke_llm=true. Avvia poi il polling. */
  readonly runWithLlm: () => Promise<DeepAnalysisResponse | undefined>;
}

export function useDeepAnalysis(ticker: string): UseDeepAnalysisResult {
  const normalized = ticker.trim().toUpperCase();
  const key =
    normalized.length > 0
      ? (['/api/analysis', normalized, 'deep', 'latest'] as const)
      : null;

  const { data: latest, error: fetchError, isLoading, mutate } = useSWR<
    LatestDeepAnalysis,
    unknown
  >(key, () => getLatestDeepAnalysis(normalized), {
    revalidateOnFocus: false,
    revalidateOnReconnect: false,
    shouldRetryOnError: false,
  });

  const latestStatus: LatestStatus = latest?.status ?? 'NONE';
  const isRunning = latestStatus === 'RUNNING';

  /* ------------------ polling lifecycle ------------------ */
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Mirror of latestStatus accessible from inside the interval closure without
  // re-creating the timer on each re-render.
  const latestStatusRef = useRef<LatestStatus>(latestStatus);
  useEffect(() => {
    latestStatusRef.current = latestStatus;
  }, [latestStatus]);

  const clearPolling = useCallback((): void => {
    if (pollTimerRef.current !== null) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  const startPolling = useCallback((): void => {
    // If a timer is already running, leave it alone (idempotent).
    if (pollTimerRef.current !== null) return;
    pollTimerRef.current = setInterval(() => {
      // Stop polling as soon as the latest snapshot leaves RUNNING.
      if (latestStatusRef.current !== 'RUNNING') {
        clearPolling();
        return;
      }
      // Fire-and-forget revalidation; errors handled by SWR.
      void mutate();
    }, POLL_INTERVAL_MS);
  }, [clearPolling, mutate]);

  // Auto-poll whenever the latest snapshot is RUNNING — anche al first paint
  // (es. una run avviata altrove o in una sessione precedente): senza questo,
  // il polling partiva solo da runNow/runWithLlm e una pagina aperta su uno
  // stato RUNNING non si aggiornava mai a SUCCESS. startPolling è idempotente;
  // clearPolling ferma il timer su stato terminale/unmount.
  useEffect(() => {
    if (isRunning) startPolling();
    else clearPolling();
  }, [isRunning, startPolling, clearPolling]);

  // Cleanup on unmount or ticker change.
  useEffect(() => {
    return () => clearPolling();
  }, [clearPolling, normalized]);

  /* ------------------ run actions ------------------ */
  const run = useCallback(
    async (invokeLlm: boolean): Promise<DeepAnalysisResponse | undefined> => {
      if (normalized.length === 0) return undefined;
      try {
        const dto: DeepAnalysisRunDto = await startDeepAnalysisRun(
          normalized,
          invokeLlm,
        );
        // Optimistic: mark as RUNNING immediately so the UI disables buttons
        // before the next /latest poll resolves.
        await mutate(
          (current): LatestDeepAnalysis => ({
            ticker: normalized,
            status: dto.status,
            runId: dto.runId,
            invokeLlm: dto.invokeLlm,
            requestedAt: current?.requestedAt ?? new Date().toISOString(),
            completedAt: null,
            result: current?.result ?? null,
            error: null,
          }),
          { revalidate: false },
        );
        startPolling();
        // Kick off an immediate revalidation to capture the real requestedAt
        // / runId from the server.
        const updated = await mutate();
        return updated?.status === 'SUCCESS'
          ? (updated.result ?? undefined)
          : undefined;
      } catch (err: unknown) {
        // Surface the POST error via SWR's error channel by triggering a
        // revalidation; SWR will set `error` if the next GET also fails.
        await mutate();
        throw err;
      }
    },
    [mutate, normalized, startPolling],
  );

  const runNow = useCallback(
    (): Promise<DeepAnalysisResponse | undefined> => run(false),
    [run],
  );
  const runWithLlm = useCallback(
    (): Promise<DeepAnalysisResponse | undefined> => run(true),
    [run],
  );

  /* ------------------ error mapping ------------------ */
  const httpError =
    fetchError !== undefined ? fromHttpError(fetchError) : undefined;
  const failureError =
    latest !== undefined ? fromLatestFailure(latest) : undefined;
  const parsedError = httpError ?? failureError;

  const isFrozenByAdmin: boolean =
    parsedError?.reason === 'LLM_FROZEN_BY_ADMIN';
  const isNotIndexed: boolean =
    parsedError?.reason === 'NOT_INDEXED' ||
    parsedError?.reason === 'not_indexed';

  const data =
    latest !== undefined && latest.status === 'SUCCESS'
      ? (latest.result ?? undefined)
      : undefined;

  return {
    data,
    latestStatus,
    isRunning,
    isLoading,
    error: parsedError,
    isFrozenByAdmin,
    isNotIndexed,
    requestedAt: latest?.requestedAt ?? null,
    completedAt: latest?.completedAt ?? null,
    runNow,
    runWithLlm,
  };
}
