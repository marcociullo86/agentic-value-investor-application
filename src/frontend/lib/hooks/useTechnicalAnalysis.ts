'use client';

import useSWR from 'swr';
import { isAxiosError } from 'axios';
import {
  getTechnicalAnalysis,
  type TechnicalAnalysisResponse,
} from '@/lib/api/technical';

/**
 * SWR hook for the Technical Analysis tab (TSK-333 — US-101, EP-024 Fase 1).
 *
 * Endpoint backend (sync, deterministic):
 *  - GET /api/analysis/{ticker}/technical?equity=… → TechnicalAnalysisResponse
 *
 * Behaviour:
 *  - LAZY: il fetch parte SOLO quando `enabled === true` (gating SWR via key
 *    condizionale). Lo invoca il `TechnicalAnalysisPageClient` dal momento in
 *    cui l'utente atterra sulla rotta `/analysis/technical?ticker=…`.
 *    Aprire `/analysis?ticker=…` (tab Analisi Base) NON deve generare alcuna
 *    chiamata a `/technical` — verificato montando l'hook con `enabled=false`
 *    fuori dalla rotta technical.
 *  - `equity` (input client-side, persistito in localStorage dal pannello
 *    position-sizing TSK-335) è parte della SWR key: cambiarla → nuovo fetch
 *    automatico, nessun side-effect manuale.
 *  - Coerente con `useDeepAnalysis`: nessun retry on focus / reconnect /
 *    error (l'utente ha già il pulsante "Riprova" del NotificationProvider
 *    EP-015 + il banner `confidenceReduced` per dati limitati).
 *  - Stati `loading` / `error` esposti ai componenti consumer (skeleton
 *    coerente design system EP-016 + ProblemDetail decoding nei pannelli).
 *
 * Riferimento:
 *  - design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/technical
 *  - US-101 §"Comportamento" — lazy load mandatorio (AC verificato con network test)
 */

export interface UseTechnicalAnalysisOptions {
  /**
   * Gating del fetch. Pattern lazy: il consumer (rotta `/analysis/technical`)
   * passa `true` solo quando la pagina è effettivamente montata; il tab
   * Analisi Base / Deep Analysis non istanziano l'hook e NESSUNA chiamata
   * a `/technical` parte al mount della pagina dettaglio ticker.
   *
   * Default `false` — fail-safe: un montaggio accidentale dell'hook NON
   * genera fetch se non si è esplicitamente abilitati.
   */
  readonly enabled?: boolean;
  /**
   * Capitale di riferimento per il calcolo 2%/6% Rule (US-100). Default
   * undefined → il BE applica 50000 USD. Mai persistito server-side.
   * Cambia → la SWR key cambia → refetch automatico.
   */
  readonly equity?: number;
}

export interface UseTechnicalAnalysisResult {
  readonly data: TechnicalAnalysisResponse | undefined;
  readonly isLoading: boolean;
  readonly error: TechnicalAnalysisError | undefined;
  /** Trigger manuale di revalidation (es. dopo retry su error). */
  readonly mutate: () => Promise<TechnicalAnalysisResponse | undefined>;
}

export interface TechnicalAnalysisError {
  readonly status: number | null;
  readonly message: string;
}

function fromHttpError(err: unknown): TechnicalAnalysisError {
  if (isAxiosError(err)) {
    const status = err.response?.status ?? null;
    if (status === 404) {
      return {
        status,
        message:
          'Ticker non trovato. Verifica il simbolo e riprova.',
      };
    }
    if (status === 503) {
      return {
        status,
        message:
          'Servizio dati di mercato temporaneamente non disponibile. Riprova più tardi.',
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

export function useTechnicalAnalysis(
  ticker: string,
  options: UseTechnicalAnalysisOptions = {},
): UseTechnicalAnalysisResult {
  const { enabled = false, equity } = options;
  const normalized = ticker.trim().toUpperCase();

  // Lazy key — `null` disabilita SWR (nessun fetch). Includere `equity` nella
  // key fa scattare il refetch automatico quando l'utente modifica l'input.
  const key =
    enabled && normalized.length > 0
      ? ([
          '/api/analysis',
          normalized,
          'technical',
          equity ?? 'default',
        ] as const)
      : null;

  const {
    data,
    error: fetchError,
    isLoading,
    mutate,
  } = useSWR<TechnicalAnalysisResponse, unknown>(
    key,
    () =>
      getTechnicalAnalysis(
        normalized,
        equity !== undefined ? { equity } : {},
      ),
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
