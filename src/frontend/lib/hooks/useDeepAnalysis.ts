'use client';

import useSWR from 'swr';
import { isAxiosError } from 'axios';
import {
  getDeepAnalysis,
  type DeepAnalysisResponse,
} from '@/lib/api/deep-analysis';

/**
 * SWR hook for GET /api/analysis/{ticker}/deep (TSK-122 — US-046).
 *
 * Returns deterministic-only data on first load (invoke_llm=false).
 * The page can later trigger the LLM pipeline via `invokeLlm()`.
 *
 * Error handling surfaces user-facing messages for 404, 422, 503
 * matching the OpenAPI error contract.
 */

export interface DeepAnalysisError {
  readonly status: number | null;
  readonly reason: string | null;
  readonly message: string;
}

function toDeepAnalysisError(err: unknown): DeepAnalysisError {
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

export interface UseDeepAnalysisResult {
  readonly data: DeepAnalysisResponse | undefined;
  readonly error: DeepAnalysisError | undefined;
  readonly isLoading: boolean;
  readonly isValidating: boolean;
  readonly isFrozenByAdmin: boolean;
  readonly invokeLlm: () => Promise<DeepAnalysisResponse | undefined>;
  readonly refresh: () => Promise<DeepAnalysisResponse | undefined>;
}

export function useDeepAnalysis(ticker: string): UseDeepAnalysisResult {
  const normalized = ticker.trim().toUpperCase();
  const key =
    normalized.length > 0
      ? (['/api/analysis', normalized, 'deep'] as const)
      : null;

  const { data, error, isLoading, isValidating, mutate } = useSWR<
    DeepAnalysisResponse,
    unknown
  >(
    key,
    () => getDeepAnalysis(normalized, false),
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      shouldRetryOnError: false,
    },
  );

  const parsedError =
    error !== undefined ? toDeepAnalysisError(error) : undefined;

  const isFrozenByAdmin =
    parsedError?.status === 503 &&
    parsedError.reason === 'LLM_FROZEN_BY_ADMIN';

  async function invokeLlm(): Promise<DeepAnalysisResponse | undefined> {
    return mutate(getDeepAnalysis(normalized, true), {
      revalidate: false,
    });
  }

  async function refresh(): Promise<DeepAnalysisResponse | undefined> {
    return mutate(undefined, { revalidate: true });
  }

  return {
    data,
    error: parsedError,
    isLoading,
    isValidating,
    isFrozenByAdmin,
    invokeLlm,
    refresh,
  };
}
