import { apiGet, type ApiResult } from '@/lib/api/client';

/**
 * Screener domain API (TSK-006 — US-002).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts, zero-touch da TSK-030).
 * Consumato da `lib/stores/useScreenerStore.ts` e `components/screener/**`.
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml §/api/screener
 *   schema `ScreenerResultPage` / `SearchResultItem`.
 *
 * Criteri operativi (fasce market cap + 11 settori GICS): vedi
 *   `wiki/sources/vi-07-risoluzione-q002-q003.md` §Criteri Q_003.
 *
 * Multi-value query params: lo schema OpenAPI espone `marketCap` e `sector`
 * come `array` con `explode: true` ⇒ formato ripetuto `?marketCap=LARGE&marketCap=MID`.
 * Axios v1 serializza nativamente gli array con `?key[]=...` (paramsSerializer
 * indexes=null) — qui usiamo `paramsSerializer` esplicito per produrre la
 * forma "exploded repeated" attesa dallo spec OpenAPI.
 */

export type MarketCapBand = 'MICRO' | 'SMALL' | 'MID' | 'LARGE' | 'MEGA';

export type GicsSector =
  | 'INFORMATION_TECHNOLOGY'
  | 'FINANCIALS'
  | 'HEALTH_CARE'
  | 'CONSUMER_DISCRETIONARY'
  | 'CONSUMER_STAPLES'
  | 'COMMUNICATION_SERVICES'
  | 'INDUSTRIALS'
  | 'ENERGY'
  | 'MATERIALS'
  | 'REAL_ESTATE'
  | 'UTILITIES';

export interface MarketCapBandOption {
  readonly value: MarketCapBand;
  readonly label: string;
}

export interface GicsSectorOption {
  readonly value: GicsSector;
  readonly label: string;
}

/**
 * Fasce capitalizzazione (vi-07-risoluzione-q002-q003 §Criteri Q_003).
 * Soglia minima hardcoded server-side: $50M (Nano Cap escluse).
 */
export const MARKET_CAP_BANDS: ReadonlyArray<MarketCapBandOption> = [
  { value: 'MICRO', label: 'Micro Cap: $50M – $300M' },
  { value: 'SMALL', label: 'Small Cap: $300M – $2B' },
  { value: 'MID', label: 'Mid Cap: $2B – $10B' },
  { value: 'LARGE', label: 'Large Cap: $10B – $200B' },
  { value: 'MEGA', label: 'Mega Cap: > $200B' },
] as const;

/**
 * 11 settori GICS supportati FMP (vi-07-risoluzione-q002-q003 §Criteri Q_003).
 * Label IT user-facing.
 */
export const GICS_SECTORS: ReadonlyArray<GicsSectorOption> = [
  { value: 'INFORMATION_TECHNOLOGY', label: 'Tecnologia' },
  { value: 'FINANCIALS', label: 'Finanza (Banche, Assicurazioni)' },
  { value: 'HEALTH_CARE', label: 'Sanità' },
  { value: 'CONSUMER_DISCRETIONARY', label: 'Beni di consumo discrezionali' },
  { value: 'CONSUMER_STAPLES', label: 'Beni di consumo essenziali' },
  { value: 'COMMUNICATION_SERVICES', label: 'Servizi di comunicazione' },
  { value: 'INDUSTRIALS', label: 'Industriali' },
  { value: 'ENERGY', label: 'Energia' },
  { value: 'MATERIALS', label: 'Materiali' },
  { value: 'REAL_ESTATE', label: 'Real Estate' },
  { value: 'UTILITIES', label: 'Utilities' },
] as const;

export interface ScreenerCriteria {
  readonly marketCap: ReadonlyArray<MarketCapBand>;
  readonly sector: ReadonlyArray<GicsSector>;
  readonly excludeHardToPredict: boolean;
  /** Default 50 (OpenAPI), max 200. */
  readonly limit: number;
}

export interface ScreenerResultItem {
  readonly ticker: string;
  readonly companyName: string;
  readonly sector?: string | null;
  readonly marketCapUsd?: number | null;
}

export interface ScreenerResultPage {
  readonly items: ReadonlyArray<ScreenerResultItem>;
  readonly nextCursor: string | null;
}

export const DEFAULT_SCREENER_LIMIT = 50;

export const EMPTY_SCREENER_CRITERIA: ScreenerCriteria = {
  marketCap: [],
  sector: [],
  excludeHardToPredict: false,
  limit: DEFAULT_SCREENER_LIMIT,
};

/**
 * Serializza i criteri in query string OpenAPI-compliant
 * (`?marketCap=LARGE&marketCap=MID&sector=...&excludeHardToPredict=...&limit=...&cursor=...`).
 * Espone una funzione pura per facilitare i test unit.
 */
export function buildScreenerQuery(
  criteria: ScreenerCriteria,
  cursor?: string,
): string {
  const parts: string[] = [];
  for (const band of criteria.marketCap) {
    parts.push(`marketCap=${encodeURIComponent(band)}`);
  }
  for (const sector of criteria.sector) {
    parts.push(`sector=${encodeURIComponent(sector)}`);
  }
  if (criteria.excludeHardToPredict) {
    parts.push('excludeHardToPredict=true');
  }
  parts.push(`limit=${encodeURIComponent(String(criteria.limit))}`);
  if (cursor !== undefined && cursor !== null && cursor.length > 0) {
    parts.push(`cursor=${encodeURIComponent(cursor)}`);
  }
  return parts.length > 0 ? `?${parts.join('&')}` : '';
}

/**
 * GET /api/screener — restituisce `ScreenerResultPage` (anche `items: []` quando
 * nessun titolo soddisfa i criteri: 200 OK semantica, non 404).
 * Errori network/5xx rilanciati al caller (store) per gestione UI.
 */
export async function screen(
  criteria: ScreenerCriteria,
  cursor?: string,
): Promise<ScreenerResultPage> {
  const queryString = buildScreenerQuery(criteria, cursor);
  const result: ApiResult<ScreenerResultPage> = await apiGet<ScreenerResultPage>(
    `/api/screener${queryString}`,
  );
  return result.data;
}
