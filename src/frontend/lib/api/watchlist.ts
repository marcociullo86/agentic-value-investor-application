import { apiDelete, apiGet, apiPost } from '@/lib/api/client';

/**
 * Watchlist API wrapper (TSK-035). Schema reference:
 * design_&_architecture/api/openapi.yaml §components.schemas (Watchlist,
 * WatchlistItem, WatchlistItemRequest).
 */

export interface WatchlistItem {
  readonly ticker: string;
  readonly companyName: string | null;
  readonly sector: string | null;
  readonly marketCapUsd: number | null;
  readonly addedAt: string;
}

export interface Watchlist {
  readonly id: string;
  readonly name: string;
  readonly isDefault: boolean;
  readonly items: ReadonlyArray<WatchlistItem>;
}

export async function fetchWatchlist(): Promise<Watchlist> {
  const result = await apiGet<Watchlist>('/api/watchlist');
  return result.data;
}

export async function addWatchlistItem(ticker: string): Promise<WatchlistItem> {
  const result = await apiPost<WatchlistItem, { ticker: string }>(
    '/api/watchlist/items',
    { ticker },
  );
  return result.data;
}

export async function removeWatchlistItem(ticker: string): Promise<void> {
  await apiDelete<void>(`/api/watchlist/items/${encodeURIComponent(ticker)}`);
}
