'use client';

import { useCallback, useMemo, useState } from 'react';
import useSWR from 'swr';
import { isAxiosError } from 'axios';
import {
  getBacktest,
  BACKTEST_YEARS_MAX,
  type BacktestHorizonMonths,
  type BacktestResponse,
  type BacktestYearsOption,
} from '@/lib/api/backtest';

/**
 * useBacktest — TSK-350 (US-106, EP-024 Fase 3).
 *
 * SWR hook **on-demand** per la verifica storica per-ticker
 * (`GET /api/analysis/{ticker}/backtest`).
 *
 * Differenze rispetto agli altri hook SWR EP-024 (`useSummary`,
 * `useTechnicalAnalysis`):
 *  - **NESSUN fetch al mount**: lo stato iniziale è `idle`. La chiamata
 *    parte SOLO dopo il primo `trigger()` (click sul bottone "BACKTEST").
 *  - **Auto-refetch sui parametri**: una volta innescato, i cambi di
 *    `years`/`horizonMonths`/`equity` ri-eseguono il backtest automaticamente
 *    (SWR key include i parametri).
 *  - Stato derivato `status: 'idle' | 'loading' | 'result' | 'empty' | 'error'`,
 *    AC US-106. `empty` = strategia EP024_ENTER_NOW senza segnali nella
 *    finestra (mostra messaggio + baseline VI_ONLY/BUY_AND_HOLD comunque).
 *
 * Pattern di "lazy trigger" implementato in due passi:
 *  1. `triggered` boolean stato interno (default false).
 *  2. SWR key = `null` finché `!triggered`; popolata al primo `trigger()`.
 *
 * I `key` SWR includono `years` + `horizonMonths` + `equity` come parte del
 * tuple — cambiarli, post-trigger, fa scattare il refetch automatico (stesso
 * pattern di `useTechnicalAnalysis` per `equity`).
 *
 * Errori HTTP normalizzati in `BacktestError` (status + message human-readable):
 *  - 400 → "Parametri non validi…" (years/horizonMonths fuori range)
 *  - 404 → "Ticker non trovato…"
 *  - 503 → "Servizio dati non disponibile…"
 *  - rete → "Errore di rete…"
 *
 * Il consumer (es. `BacktestPanel`) può inoltrare l'`error.message` al
 * `NotificationProvider` (EP-015) per il toast retry; in alternativa lo
 * mostra inline (AC US-106 cita esplicitamente NotificationProvider).
 */

export type BacktestState =
  | 'idle'
  | 'loading'
  | 'result'
  | 'empty'
  | 'error';

export interface BacktestError {
  readonly status: number | null;
  readonly message: string;
}

export interface UseBacktestOptions {
  readonly ticker: string;
  /** Default 5 anni (allineato BE / US-105 §Endpoint). */
  readonly years?: BacktestYearsOption;
  /** Default 6 mesi (allineato BE / US-105 §Endpoint). */
  readonly horizonMonths?: BacktestHorizonMonths;
  /** Capitale di riferimento (mai persistito server-side — US-100/TSK-335). */
  readonly equity?: number;
}

export interface UseBacktestResult {
  /** Stato a 5 valori (idle/loading/result/empty/error) — AC US-106. */
  readonly status: BacktestState;
  /** Payload BE quando `status ∈ {result, empty}`. */
  readonly data: BacktestResponse | undefined;
  readonly error: BacktestError | undefined;
  /**
   * Innesca il backtest. Idempotente: chiamate successive (es. doppio click)
   * non causano un nuovo fetch finché parametri identici. Il refetch su cambio
   * parametri è automatico via SWR key.
   */
  readonly trigger: () => void;
  /** Trigger manuale di revalidation (es. dopo retry su error). */
  readonly retry: () => void;
  /** True quando il backtest è già stato innescato almeno una volta. */
  readonly triggered: boolean;
}

function fromHttpError(err: unknown): BacktestError {
  if (isAxiosError(err)) {
    const status = err.response?.status ?? null;
    if (status === 400) {
      return {
        status,
        message:
          'Parametri del backtest non validi. Verifica i selettori e riprova.',
      };
    }
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

/**
 * EP024_ENTER_NOW senza segnali nella finestra → stato `empty`.
 * Driver: payload `status = OK` ma la strategia ENTER_NOW ha
 * `noSignalsInPeriod = true`. Le baseline (VI_ONLY / BUY_AND_HOLD) restano
 * comunque visibili — AC US-106 "0 segnali → messaggio + baseline".
 */
function isEmptyResult(data: BacktestResponse | undefined): boolean {
  if (data === undefined) return false;
  if (data.status !== 'OK') return false;
  const ep024 = data.strategies?.find(
    (s) => s.strategy === 'EP024_ENTER_NOW',
  );
  if (ep024 === undefined) return false;
  return ep024.noSignalsInPeriod === true;
}

export function useBacktest(options: UseBacktestOptions): UseBacktestResult {
  const { ticker, years, horizonMonths, equity } = options;
  const normalized = ticker.trim().toUpperCase();

  // Lazy-trigger gate. `null` SWR key disabilita la fetch.
  const [triggered, setTriggered] = useState<boolean>(false);

  const key = useMemo(() => {
    if (!triggered || normalized.length === 0) return null;
    // tuple stabile per SWR — eviare ricreate ogni render
    return [
      '/api/analysis',
      normalized,
      'backtest',
      years ?? 'default',
      horizonMonths ?? 'default',
      equity ?? 'default',
    ] as const;
  }, [triggered, normalized, years, horizonMonths, equity]);

  const {
    data,
    error: fetchError,
    isLoading,
    isValidating,
    mutate,
  } = useSWR<BacktestResponse, unknown>(
    key,
    () =>
      getBacktest(normalized, {
        years,
        horizonMonths,
        equity,
      }),
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      shouldRetryOnError: false,
    },
  );

  const trigger = useCallback((): void => {
    setTriggered(true);
  }, []);

  const retry = useCallback((): void => {
    setTriggered(true);
    void mutate();
  }, [mutate]);

  const error =
    fetchError !== undefined ? fromHttpError(fetchError) : undefined;

  const status: BacktestState = useMemo(() => {
    if (!triggered) return 'idle';
    if (error !== undefined && data === undefined) return 'error';
    // `isValidating` copre il refetch a parametri cambiati (no flicker su
    // `result`/`empty` → loading state visibile per il consumer).
    if (isLoading || (isValidating && data === undefined)) return 'loading';
    if (data === undefined) return 'loading';
    if (isEmptyResult(data)) return 'empty';
    return 'result';
  }, [triggered, isLoading, isValidating, data, error]);

  return {
    status,
    data,
    error,
    trigger,
    retry,
    triggered,
  };
}
