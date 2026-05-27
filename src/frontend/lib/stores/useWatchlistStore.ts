import { create } from 'zustand';
import {
  addWatchlistItem,
  fetchWatchlist,
  removeWatchlistItem,
  type Watchlist,
  type WatchlistItem,
} from '@/lib/api/watchlist';
import { toUserMessage } from '@/lib/to-user-message';

/**
 * Watchlist store (TSK-035, US-017).
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §useWatchlistStore.
 */

export interface WatchlistState {
  readonly watchlist: Watchlist | null;
  readonly items: ReadonlyArray<WatchlistItem>;
  readonly loading: boolean;
  readonly error: string | null;
  readonly fetch: () => Promise<void>;
  readonly add: (ticker: string) => Promise<void>;
  readonly remove: (ticker: string) => Promise<void>;
  readonly reset: () => void;
}

export const useWatchlistStore = create<WatchlistState>((set, get) => ({
  watchlist: null,
  items: [],
  loading: false,
  error: null,

  reset: (): void => set({ watchlist: null, items: [], loading: false, error: null }),

  fetch: async (): Promise<void> => {
    set({ loading: true, error: null });
    try {
      const watchlist = await fetchWatchlist();
      set({ watchlist, items: watchlist.items, loading: false });
    } catch (err) {
      set({
        loading: false,
        error: toUserMessage(err, {
          fallback: 'Impossibile caricare la watchlist. Riprova.',
        }),
      });
    }
  },

  add: async (ticker: string): Promise<void> => {
    try {
      const item = await addWatchlistItem(ticker);
      const current = get().items;
      if (!current.some((it) => it.ticker === item.ticker)) {
        set({ items: [item, ...current] });
      }
    } catch (err) {
      set({
        error: toUserMessage(err, {
          fallback: 'Aggiunta alla watchlist non riuscita. Riprova.',
          statusOverrides: {
            409: 'Ticker già presente in watchlist.',
            404: 'Ticker non trovato.',
          },
        }),
      });
      throw err;
    }
  },

  remove: async (ticker: string): Promise<void> => {
    try {
      await removeWatchlistItem(ticker);
      set({ items: get().items.filter((it) => it.ticker !== ticker) });
    } catch (err) {
      set({
        error: toUserMessage(err, {
          fallback: 'Rimozione dalla watchlist non riuscita. Riprova.',
        }),
      });
      throw err;
    }
  },
}));
