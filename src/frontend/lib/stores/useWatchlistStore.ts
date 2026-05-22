import { create } from 'zustand';
import {
  addWatchlistItem,
  fetchWatchlist,
  removeWatchlistItem,
  type Watchlist,
  type WatchlistItem,
} from '@/lib/api/watchlist';

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
      const message = err instanceof Error ? err.message : 'fetch failed';
      set({ loading: false, error: message });
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
      const message = err instanceof Error ? err.message : 'add failed';
      set({ error: message });
      throw err;
    }
  },

  remove: async (ticker: string): Promise<void> => {
    try {
      await removeWatchlistItem(ticker);
      set({ items: get().items.filter((it) => it.ticker !== ticker) });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'remove failed';
      set({ error: message });
      throw err;
    }
  },
}));
