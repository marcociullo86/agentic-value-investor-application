'use client';

import { useCallback, useEffect, useState } from 'react';

/**
 * useEquityLocalStorage — TSK-335 (US-101, EP-024 Fase 1).
 *
 * Hook ultra-leggero che mantiene il valore `equity` (capitale di riferimento
 * per il 2%/6% Rule sizing) in `localStorage`, coerente con il principio
 * US-100 §"Separazione di responsabilità": il BE NON persiste l'equity
 * dell'utente, lo accetta come query param della GET /technical.
 *
 * Pattern equivalente al `use-theme` di EP-016 (memoria locale stateful con
 * hydration safe):
 *  - Inizializzazione `default` per evitare hydration mismatch SSR/RSC.
 *  - Lettura sincrona da `localStorage` al mount.
 *  - Scrittura ad ogni `set`.
 *  - Degrado silenzioso se `localStorage` non disponibile (private browsing,
 *    policy enterprise): la UI funziona ugualmente con il valore in memoria.
 *
 * Non parsa stringhe non numeriche; ritorna `default` su valori non finiti
 * (es. NaN, Infinity, stringhe vuote).
 */

const STORAGE_KEY = 'ta-sizing-equity:v1';
const DEFAULT_EQUITY = 50_000;
const MIN_EQUITY = 0.01;

export interface UseEquityLocalStorageResult {
  /** Valore corrente (hydration-safe: parte da default, poi cambia al mount). */
  readonly equity: number;
  /** True dopo aver letto `localStorage` (per evitare flash di valore stale). */
  readonly hydrated: boolean;
  /** Setter: scrive in `localStorage` se ammissibile (>= MIN_EQUITY). */
  readonly setEquity: (value: number) => void;
  /** Reset al default factory (50000 USD). */
  readonly reset: () => void;
}

function readFromStorage(): number {
  if (typeof window === 'undefined') return DEFAULT_EQUITY;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === null) return DEFAULT_EQUITY;
    const parsed = Number(raw);
    if (!Number.isFinite(parsed) || parsed < MIN_EQUITY) return DEFAULT_EQUITY;
    return parsed;
  } catch {
    return DEFAULT_EQUITY;
  }
}

function writeToStorage(value: number): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, value.toString());
  } catch {
    // best-effort, no-throw
  }
}

export function useEquityLocalStorage(): UseEquityLocalStorageResult {
  const [equity, setEquityState] = useState<number>(DEFAULT_EQUITY);
  const [hydrated, setHydrated] = useState<boolean>(false);

  useEffect(() => {
    setEquityState(readFromStorage());
    setHydrated(true);
  }, []);

  const setEquity = useCallback((value: number): void => {
    if (!Number.isFinite(value) || value < MIN_EQUITY) return;
    setEquityState(value);
    writeToStorage(value);
  }, []);

  const reset = useCallback((): void => {
    setEquityState(DEFAULT_EQUITY);
    writeToStorage(DEFAULT_EQUITY);
  }, []);

  return { equity, hydrated, setEquity, reset };
}

export const TA_EQUITY_DEFAULT = DEFAULT_EQUITY;
export const TA_EQUITY_MIN = MIN_EQUITY;
