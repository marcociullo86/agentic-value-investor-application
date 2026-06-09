'use client';

import useSWR from 'swr';
import { isAxiosError } from 'axios';
import { getSummary, type SummaryVerdictResponse } from '@/lib/api/summary';

/**
 * SWR hook for the Riepilogo cross-dominio tab (TSK-342 — US-104, EP-024 Fase 2).
 *
 * Endpoint backend (sync, deterministic):
 *  - GET /api/analysis/{ticker}/summary → SummaryVerdictResponse
 *
 * Behaviour:
 *  - LAZY: il fetch parte SOLO quando `enabled === true` (gating SWR via
 *    key condizionale). Il `SummaryPageClient` lo abilita quando l'utente
 *    atterra sulla rotta `/analysis?ticker=…` (landing/default) o sul
 *    deep-link `/analysis/summary?ticker=…`. Aprire `/analysis/deep?ticker=…`
 *    o `/analysis/technical?ticker=…` NON genera fetch su `/summary`.
 *  - Coerente con `useTechnicalAnalysis` e `useDeepAnalysis`: nessun retry
 *    on focus / reconnect / error. L'utente ha il pulsante "Riprova"
 *    nell'`ErrorPanel` del componente consumer.
 *  - Stati `loading` / `error` esposti al `SummaryPageClient` (skeleton
 *    coerente design system EP-016 + ProblemDetail decoding).
 *
 * Riferimento:
 *  - design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/summary
 *  - US-104 §"Comportamento" — fetch al mount del tab default
 */

export interface UseSummaryOptions {
  /**
   * Gating del fetch. Pattern lazy: il consumer (rotta `/analysis` o
   * `/analysis/summary`) passa `true` solo quando la pagina è effettivamente
   * montata. Default `false` — fail-safe: un montaggio accidentale dell'hook
   * NON genera fetch se non si è esplicitamente abilitati.
   */
  readonly enabled?: boolean;
}

export interface UseSummaryResult {
  readonly data: SummaryVerdictResponse | undefined;
  readonly isLoading: boolean;
  readonly error: SummaryError | undefined;
  /** Trigger manuale di revalidation (es. dopo retry su error). */
  readonly mutate: () => Promise<SummaryVerdictResponse | undefined>;
}

export interface SummaryError {
  readonly status: number | null;
  readonly message: string;
}

function fromHttpError(err: unknown): SummaryError {
  if (isAxiosError(err)) {
    const status = err.response?.status ?? null;
    if (status === 404) {
      return {
        status,
        message: 'Ticker non trovato. Verifica il simbolo e riprova.',
      };
    }
    if (status === 503) {
      return {
        status,
        message:
          'Riepilogo temporaneamente non disponibile (servizio upstream giù). Riprova più tardi.',
      };
    }
    if (typeof status === 'number') {
      return {
        status,
        message: `Errore server (${status}). Riprova.`,
      };
    }
  }
  return {
    status: null,
    message: 'Errore di rete. Verifica la connessione.',
  };
}

export function useSummary(
  ticker: string,
  options: UseSummaryOptions = {},
): UseSummaryResult {
  const { enabled = false } = options;
  const normalized = ticker.trim().toUpperCase();

  // Lazy key — `null` disabilita SWR (nessun fetch).
  const key =
    enabled && normalized.length > 0
      ? (['/api/analysis', normalized, 'summary'] as const)
      : null;

  const {
    data,
    error: fetchError,
    isLoading,
    mutate,
  } = useSWR<SummaryVerdictResponse, unknown>(
    key,
    () => getSummary(normalized),
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      shouldRetryOnError: false,
    },
  );

  const error =
    fetchError !== undefined ? fromHttpError(fetchError) : undefined;

  return {
    data,
    isLoading,
    error,
    mutate: () => mutate(),
  };
}
