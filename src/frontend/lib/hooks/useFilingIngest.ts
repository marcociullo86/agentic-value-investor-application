'use client';

import { useCallback, useEffect, useRef } from 'react';
import useSWR from 'swr';
import { isAxiosError } from 'axios';
import {
  getLatestIngest,
  startIngest,
  type IngestRunDto,
  type IngestStatus,
  type IngestSummary,
  type LatestIngest,
} from '@/lib/api/deep-analysis';

/**
 * SWR hook for the asynchronous Filings Ingest flow.
 *
 * Backend contract:
 *  - POST /api/analysis/{ticker}/deep/ingest                → 202 IngestRunDto
 *  - GET  /api/analysis/{ticker}/deep/ingest/latest         → snapshot of the
 *    last known ingest run (status NONE | RUNNING | SUCCESS | FAILED).
 *
 * Behaviour: mirrors `useDeepAnalysis` 1:1:
 *  - On mount / navigate back fetches `ingest/latest` (no auto-rerun).
 *  - `runIngest()` POSTs a new ingest, marks the latest snapshot as RUNNING
 *    optimistically, then starts a 3-second polling loop on `ingest/latest`
 *    until the status leaves `RUNNING`. The interval is cleared on terminal
 *    status or on unmount.
 *  - The backend deduplicates concurrent POSTs; the UI keeps the button
 *    disabled while `isRunning` so the user simply cannot retrigger.
 */

const POLL_INTERVAL_MS = 3_000;

export interface IngestErrorView {
  readonly status: number | null;
  readonly reason: string | null;
  readonly message: string;
}

function fromHttpError(err: unknown): IngestErrorView {
  if (isAxiosError(err)) {
    const status = err.response?.status ?? null;
    const body = err.response?.data as
      | { reason?: string; detail?: string }
      | undefined;
    const reason = body?.reason ?? null;
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
  latest: LatestIngest,
): IngestErrorView | undefined {
  if (latest.status !== 'FAILED' || latest.error === null) return undefined;
  return {
    status: null,
    reason: latest.error.reason,
    message:
      latest.error.message ??
      "Indicizzazione fallita. Riprova ora oppure più tardi.",
  };
}

export interface UseFilingIngestResult {
  /** Status della latest snapshot ('NONE' quando non esiste alcun job). */
  readonly status: IngestStatus;
  /** Convenienza: `status === 'RUNNING'`. */
  readonly isRunning: boolean;
  /** Loading iniziale SWR (prima fetch `ingest/latest`). */
  readonly isLoading: boolean;
  /** Summary deterministico esposto solo su SUCCESS. */
  readonly summary: IngestSummary | null;
  /** ISO timestamp di inizio job (presente per RUNNING/SUCCESS/FAILED). */
  readonly requestedAt: string | null;
  /** ISO timestamp di fine job (presente per SUCCESS/FAILED). */
  readonly completedAt: string | null;
  /** Errore mappato (HTTP della GET oppure latest.error in caso di FAILED). */
  readonly error: IngestErrorView | undefined;
  /** POST /deep/ingest. Avvia poi il polling. */
  readonly runIngest: () => Promise<void>;
}

export function useFilingIngest(ticker: string): UseFilingIngestResult {
  const normalized = ticker.trim().toUpperCase();
  const key =
    normalized.length > 0
      ? (['/api/analysis', normalized, 'deep', 'ingest', 'latest'] as const)
      : null;

  const { data: latest, error: fetchError, isLoading, mutate } = useSWR<
    LatestIngest,
    unknown
  >(key, () => getLatestIngest(normalized), {
    revalidateOnFocus: false,
    revalidateOnReconnect: false,
    shouldRetryOnError: false,
  });

  const status: IngestStatus = latest?.status ?? 'NONE';
  const isRunning = status === 'RUNNING';

  /* ------------------ polling lifecycle ------------------ */
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Mirror of status accessible from inside the interval closure without
  // re-creating the timer on each re-render.
  const statusRef = useRef<IngestStatus>(status);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);

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
      if (statusRef.current !== 'RUNNING') {
        clearPolling();
        return;
      }
      // Fire-and-forget revalidation; errors handled by SWR.
      void mutate();
    }, POLL_INTERVAL_MS);
  }, [clearPolling, mutate]);

  // Auto-poll whenever the latest ingest snapshot is RUNNING — anche al first
  // paint (ingest avviato altrove): startPolling è idempotente, clearPolling
  // ferma su stato terminale/unmount.
  useEffect(() => {
    if (isRunning) startPolling();
    else clearPolling();
  }, [isRunning, startPolling, clearPolling]);

  // Cleanup on unmount or ticker change.
  useEffect(() => {
    return () => clearPolling();
  }, [clearPolling, normalized]);

  /* ------------------ run action ------------------ */
  const runIngest = useCallback(async (): Promise<void> => {
    if (normalized.length === 0) return;
    try {
      const dto: IngestRunDto = await startIngest(normalized);
      // Optimistic: mark as RUNNING immediately so the UI disables the button
      // before the next /latest poll resolves.
      await mutate(
        (current): LatestIngest => ({
          ticker: normalized,
          status: dto.status,
          runId: dto.runId,
          requestedAt: current?.requestedAt ?? new Date().toISOString(),
          completedAt: null,
          summary: current?.summary ?? null,
          error: null,
        }),
        { revalidate: false },
      );
      startPolling();
      // Kick off an immediate revalidation to capture the real requestedAt
      // / runId from the server.
      await mutate();
    } catch (err: unknown) {
      // Surface the POST error via SWR's error channel by triggering a
      // revalidation; SWR will set `error` if the next GET also fails.
      await mutate();
      throw err;
    }
  }, [mutate, normalized, startPolling]);

  /* ------------------ error mapping ------------------ */
  const httpError =
    fetchError !== undefined ? fromHttpError(fetchError) : undefined;
  const failureError =
    latest !== undefined ? fromLatestFailure(latest) : undefined;
  const parsedError = httpError ?? failureError;

  const summary =
    latest !== undefined && latest.status === 'SUCCESS'
      ? (latest.summary ?? null)
      : null;

  return {
    status,
    isRunning,
    isLoading,
    summary,
    requestedAt: latest?.requestedAt ?? null,
    completedAt: latest?.completedAt ?? null,
    error: parsedError,
    runIngest,
  };
}
