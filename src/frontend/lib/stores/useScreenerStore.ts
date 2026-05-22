import { create } from 'zustand';
import { isAxiosError } from 'axios';
import {
  EMPTY_SCREENER_CRITERIA,
  screen,
  type ScreenerCriteria,
  type ScreenerResultItem,
} from '@/lib/api/screener';

/**
 * Screener store (TSK-006 — US-002).
 *
 * Riferimento design: design_&_architecture/components/frontend-components.md
 *   §State management → `useScreenerStore`.
 *
 * Contratto:
 *  - `filters` rappresenta la "draft" che l'utente sta editando + l'ultima
 *    submission attiva (memorizzata per refresh page / refilter).
 *  - `submit()` resetta `results` e `cursor` poi fetcha la prima pagina.
 *  - `loadMore()` appende items usando `cursor`; no-op se `cursor === null`.
 *  - `reset()` riporta filters e results allo stato iniziale.
 *
 * Persistenza filtri:
 *  - Per US-002 i filtri vivono SOLO nello store (in-memory).
 *  - Sync con URL query params è esplicitamente fuori scope TSK-006
 *    (vedi spec "NON in scope (follow-up)"); resta possibile aggiungerlo
 *    in un task successivo senza rompere il contratto pubblico dello store.
 */

export interface ScreenerState {
  readonly filters: ScreenerCriteria;
  readonly results: ReadonlyArray<ScreenerResultItem>;
  readonly cursor: string | null;
  readonly loading: boolean;
  readonly error: string | null;
  readonly hasSubmitted: boolean;
  readonly setFilters: (partial: Partial<ScreenerCriteria>) => void;
  readonly submit: () => Promise<void>;
  readonly loadMore: () => Promise<void>;
  readonly reset: () => void;
}

function toUserMessage(err: unknown): string {
  if (isAxiosError(err) && err.response?.status) {
    return `Errore server (${err.response.status}). Riprova.`;
  }
  return 'Errore di rete. Verifica la connessione.';
}

export const useScreenerStore = create<ScreenerState>((set, get) => ({
  filters: { ...EMPTY_SCREENER_CRITERIA },
  results: [],
  cursor: null,
  loading: false,
  error: null,
  hasSubmitted: false,

  setFilters: (partial: Partial<ScreenerCriteria>): void => {
    set((state) => ({ filters: { ...state.filters, ...partial } }));
  },

  submit: async (): Promise<void> => {
    const { filters } = get();
    set({ loading: true, error: null, results: [], cursor: null });
    try {
      const page = await screen(filters);
      set({
        results: page.items,
        cursor: page.nextCursor,
        loading: false,
        hasSubmitted: true,
      });
    } catch (err: unknown) {
      set({
        loading: false,
        error: toUserMessage(err),
        hasSubmitted: true,
      });
    }
  },

  loadMore: async (): Promise<void> => {
    const { filters, cursor, loading, results } = get();
    if (cursor === null || loading) return;
    set({ loading: true, error: null });
    try {
      const page = await screen(filters, cursor);
      set({
        results: [...results, ...page.items],
        cursor: page.nextCursor,
        loading: false,
      });
    } catch (err: unknown) {
      set({ loading: false, error: toUserMessage(err) });
    }
  },

  reset: (): void => {
    set({
      filters: { ...EMPTY_SCREENER_CRITERIA },
      results: [],
      cursor: null,
      loading: false,
      error: null,
      hasSubmitted: false,
    });
  },
}));
