'use client';

import useSWR from 'swr';
import { isAxiosError } from 'axios';
import {
  buildTopPicksUrl,
  getTopPicks,
  type TopPicksPageResponse,
  type TopPicksQueryParams,
} from '@/lib/api/top-picks';

/**
 * SWR hook for GET /api/top-picks (TSK-141 — US-051, EP-012).
 *
 * Patterns:
 *  - SWR key = full URL stringa (deduplication automatica).
 *  - `revalidateOnFocus: false` — daily run, niente re-fetch su tab focus.
 *  - `keepPreviousData: true` — paginazione fluida senza flash bianco.
 *  - Error mapping → status + message user-friendly (400, 503, network).
 */

export interface TopPicksError {
  readonly status: number | null;
  readonly message: string;
}

function toTopPicksError(err: unknown): TopPicksError {
  if (isAxiosError(err)) {
    const status = err.response?.status ?? null;
    if (status === 400) {
      return {
        status,
        message: 'Data non valida. Usa il formato YYYY-MM-DD (es. 2026-05-26).',
      };
    }
    if (status === 503) {
      return {
        status,
        message: 'Servizio non disponibile, riprova più tardi.',
      };
    }
    if (typeof status === 'number') {
      return { status, message: `Errore server (${status}). Riprova.` };
    }
  }
  return {
    status: null,
    message: 'Errore di rete. Verifica la connessione.',
  };
}

export interface UseTopPicksResult {
  readonly data: TopPicksPageResponse | undefined;
  readonly error: TopPicksError | undefined;
  readonly isLoading: boolean;
  readonly isValidating: boolean;
  readonly mutate: () => Promise<TopPicksPageResponse | undefined>;
}

export function useTopPicks(params: TopPicksQueryParams): UseTopPicksResult {
  const key = buildTopPicksUrl(params);

  const { data, error, isLoading, isValidating, mutate } = useSWR<
    TopPicksPageResponse,
    unknown
  >(key, () => getTopPicks(params), {
    revalidateOnFocus: false,
    keepPreviousData: true,
    shouldRetryOnError: false,
  });

  const parsedError = error !== undefined ? toTopPicksError(error) : undefined;

  return {
    data,
    error: parsedError,
    isLoading,
    isValidating,
    mutate: () => mutate(),
  };
}
