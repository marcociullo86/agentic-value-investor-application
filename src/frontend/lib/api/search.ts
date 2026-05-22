import { isAxiosError } from 'axios';
import { apiGet, type ApiResult } from '@/lib/api/client';

/**
 * Search domain API (TSK-003 — US-001).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts, zero-touch da TSK-030).
 * Consumato da `components/search/SearchBar.tsx`.
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml §/api/search,
 *   §/api/search/{ticker} — schemas SearchResultList / SearchResultItem /
 *   StockProfile.
 *
 * NOTA tipi TS: il generatore `openapi-typescript` (script `npm run
 * generate:api`) emette `lib/api/generated/schema.ts` ma è gitignored e
 * l'artefatto non è committed in repo. Definiamo qui i tipi minimal
 * compatibili con lo schema OpenAPI (subset usato nel flow US-001). Quando
 * il generato sarà materializzato in CI/local i tipi qui resteranno
 * structurally compatibili (drop-in replace).
 */

export interface SearchResultItem {
  readonly ticker: string;
  readonly companyName: string;
  readonly sector?: string | null;
  readonly marketCapUsd?: number | null;
}

export interface SearchResultList {
  readonly items: ReadonlyArray<SearchResultItem>;
}

export interface StockProfile {
  readonly ticker: string;
  readonly companyName: string;
  readonly sector?: string | null;
  readonly industry?: string | null;
  readonly marketCapUsd?: number | null;
  readonly currentPrice?: number | null;
  readonly dataSnapshotAt?: string | null;
}

/**
 * Normalizza il ticker (uppercase + trim) per allinearsi alla validazione
 * server-side `[A-Z0-9.\-]+` (US-001 AC, SearchService BE).
 */
export function normalizeTicker(raw: string): string {
  return raw.trim().toUpperCase();
}

/**
 * GET /api/search?query={normalized}.
 *
 * Ritorna `SearchResultList` (anche vuota se nessun match — il 200 con
 * `items: []` è il caso "not found" canonico per la search free-text).
 *
 * Errori network/5xx → rilanciati: il caller (SearchBar) li traduce in UI.
 */
export async function searchTicker(query: string): Promise<SearchResultList> {
  const normalized = normalizeTicker(query);
  const result: ApiResult<SearchResultList> = await apiGet<SearchResultList>(
    `/api/search?query=${encodeURIComponent(normalized)}`,
  );
  return result.data;
}

/**
 * GET /api/search/{ticker} — validazione esistenza ticker.
 *
 * Use case `validateTicker` (US-001 AC): se il match è esatto (single result
 * o input == ticker) il caller può direttamente navigare a `/analysis/{ticker}`
 * senza una seconda chiamata, ma esponiamo l'API per il flow esplicito.
 *
 * 404 → ritorna `null` (semantica "non esiste"); altri errori rilanciati.
 */
export async function getStockProfile(
  ticker: string,
): Promise<StockProfile | null> {
  const normalized = normalizeTicker(ticker);
  try {
    const result: ApiResult<StockProfile> = await apiGet<StockProfile>(
      `/api/search/${encodeURIComponent(normalized)}`,
    );
    return result.data;
  } catch (err: unknown) {
    if (isAxiosError(err) && err.response?.status === 404) {
      return null;
    }
    throw err;
  }
}
