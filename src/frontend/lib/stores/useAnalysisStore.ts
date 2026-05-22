import { create } from 'zustand';
import { isAxiosError } from 'axios';
import { getAnalysis, type RuleEngineResult } from '@/lib/api/analysis';

/**
 * Analysis store (TSK-021 — US-014).
 *
 * Riferimento design: design_&_architecture/components/frontend-components.md
 *   §State management → `useAnalysisStore`.
 *
 * Contratto:
 *  - `byTicker` mappa ticker (UPPERCASE) → ultimo risultato Rule Engine
 *    osservato. Cache in-memory cross-mount; persistenza fuori scope MVP.
 *  - `loading[ticker]` `true` durante una fetch in volo.
 *  - `errors[ticker]` user-facing string (o `null` su success).
 *  - `fetchAnalysis(ticker, { force })`: se il ticker è già in cache e
 *    `force !== true`, è no-op (skip fetch). Permette al consumer di
 *    chiamarlo on mount senza preoccuparsi del double-fetch in StrictMode.
 *  - `clear(ticker?)`: pulisce il singolo ticker o tutto lo store.
 *
 * Coerente con il pattern `useScreenerStore` (TSK-006):
 *  - error handling tramite helper `toUserMessage` (Axios-aware).
 *  - state readonly + actions readonly nel tipo pubblico.
 */

export interface AnalysisState {
  readonly byTicker: Readonly<Record<string, RuleEngineResult>>;
  readonly loading: Readonly<Record<string, boolean>>;
  readonly errors: Readonly<Record<string, string | null>>;
  readonly fetchAnalysis: (
    ticker: string,
    options?: { readonly force?: boolean },
  ) => Promise<void>;
  readonly clear: (ticker?: string) => void;
}

function toUserMessage(err: unknown): string {
  if (isAxiosError(err)) {
    const status = err.response?.status;
    if (status === 404) {
      return 'Ticker non trovato.';
    }
    if (status === 503) {
      return 'Dati insufficienti per analisi e provider upstream non raggiungibile. Riprova più tardi.';
    }
    if (typeof status === 'number') {
      return `Errore server (${status}). Riprova.`;
    }
  }
  return 'Errore di rete. Verifica la connessione.';
}

function normalize(ticker: string): string {
  return ticker.trim().toUpperCase();
}

export const useAnalysisStore = create<AnalysisState>((set, get) => ({
  byTicker: {},
  loading: {},
  errors: {},

  fetchAnalysis: async (
    ticker: string,
    options?: { readonly force?: boolean },
  ): Promise<void> => {
    const key = normalize(ticker);
    if (key.length === 0) return;
    const state = get();
    const alreadyCached = state.byTicker[key] !== undefined;
    const force = options?.force === true;
    if (alreadyCached && !force) {
      return;
    }
    if (state.loading[key] === true) {
      // Una fetch è già in volo per questo ticker: dedup.
      return;
    }
    set((s) => ({
      loading: { ...s.loading, [key]: true },
      errors: { ...s.errors, [key]: null },
    }));
    try {
      const result = await getAnalysis(key);
      set((s) => ({
        byTicker: { ...s.byTicker, [key]: result },
        loading: { ...s.loading, [key]: false },
        errors: { ...s.errors, [key]: null },
      }));
    } catch (err: unknown) {
      set((s) => ({
        loading: { ...s.loading, [key]: false },
        errors: { ...s.errors, [key]: toUserMessage(err) },
      }));
    }
  },

  clear: (ticker?: string): void => {
    if (ticker === undefined) {
      set({ byTicker: {}, loading: {}, errors: {} });
      return;
    }
    const key = normalize(ticker);
    set((s) => {
      const byTicker = { ...s.byTicker };
      const loading = { ...s.loading };
      const errors = { ...s.errors };
      delete byTicker[key];
      delete loading[key];
      delete errors[key];
      return { byTicker, loading, errors };
    });
  },
}));
