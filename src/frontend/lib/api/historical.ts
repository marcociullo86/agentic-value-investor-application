import { apiGet, type ApiResult } from '@/lib/api/client';

/**
 * Historical domain API (TSK-024 — US-015).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts, zero-touch da TSK-030).
 * Consumato da `components/charts/HistoricalChart.tsx` (via hook
 * `useHistorical`) e dalla page `app/analysis/page.tsx`
 * (`/analysis?ticker=`, TSK-055 / ADR-013).
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml
 *   §/api/historical/{ticker} — schemas `HistoricalSeries` /
 *   `HistoricalSeriesPoint`.
 *
 * NOTA naming: il contratto canonico OpenAPI usa `points` (NON `items`) e
 * `fiscalYear` (NON `year`); allineato verbatim al BE TSK-023.
 *
 * NOTA tipi TS: il generatore `openapi-typescript` (script
 * `npm run generate:api`) emette `lib/api/generated/schema.ts` ma è
 * gitignored. Definiamo qui i tipi minimal del subset usato in US-015,
 * structurally compatibili con lo schema OpenAPI (drop-in replace quando il
 * generato sarà materializzato in CI/local).
 */

export interface HistoricalSeriesPoint {
  readonly fiscalYear: number;
  /** USD assoluti — `null` quando il dato non è disponibile (NB: mai 0). */
  readonly revenue: number | null;
  /** USD assoluti — `null` quando il dato non è disponibile (NB: mai 0). */
  readonly netIncome: number | null;
  /** `true` se almeno una delle due metriche dell'anno è mancante. */
  readonly isMissing: boolean;
}

export interface HistoricalSeries {
  readonly ticker: string;
  readonly points: ReadonlyArray<HistoricalSeriesPoint>;
  /** ISO-8601 — momento snapshot upstream (FMP). Stesso valore di `X-Data-Snapshot-At`. */
  readonly dataSnapshotAt?: string;
}

/**
 * GET /api/historical/{ticker} — restituisce `HistoricalSeries`.
 *
 * Ordine `points`: cronologico crescente per `fiscalYear` (garantito BE
 * TSK-023, il FE NON deve risortare). Lunghezza ≤ 10. `points: []` quando
 * non esiste storia per il ticker (200 OK semantico, non 404).
 *
 * Errori network/4xx/5xx rilanciati al caller (hook/componente) per gestione
 * UI; 404 del BE significa ticker inesistente (semanticamente diverso da
 * "storia vuota").
 */
export async function getHistorical(ticker: string): Promise<HistoricalSeries> {
  const normalized = ticker.trim().toUpperCase();
  const result: ApiResult<HistoricalSeries> = await apiGet<HistoricalSeries>(
    `/api/historical/${encodeURIComponent(normalized)}`,
  );
  return result.data;
}
