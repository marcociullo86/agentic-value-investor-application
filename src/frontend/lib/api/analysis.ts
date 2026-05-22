import { apiGet, type ApiResult } from '@/lib/api/client';

/**
 * Analysis domain API (TSK-021 — US-014).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts, zero-touch da TSK-030).
 * Consumato da `lib/stores/useAnalysisStore.ts` e indirettamente dai
 * componenti `components/analysis/TrafficLightPanel.tsx`,
 * `RuleSignalCard.tsx`, `ValuationSummary.tsx`.
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml
 *   §/api/analysis/{ticker} — schemas `RuleEngineResult` / `RuleSignal`
 *   / `Signal` / `DcfMethod`.
 *
 * Naming CANONICAL:
 *  - Root object: `RuleEngineResult` (verbatim da OpenAPI line 536).
 *  - Timestamp snapshot dati: `dataSnapshotAt` (verbatim da OpenAPI; il
 *    TSK-021 spec menzionava `sourceSnapshotFetchedAt` ma la gerarchia
 *    delle fonti (PATTERN §1) pone OpenAPI sopra il TSK: OpenAPI vince).
 *  - `evaluatedAt` aggiuntivo per quando il Rule Engine ha effettuato la
 *    valutazione (≠ `dataSnapshotAt` che è il momento snapshot upstream FMP).
 *  - `Signal` enum allargato include `NOT_APPLICABLE` (usato da `mosSignal`)
 *    rispetto al subset gestito da `lib/utils/signal-color.ts` (TSK-030,
 *    che copre solo le 5 regole quantitative). Definiamo qui un alias
 *    `MosSignal` che è strutturalmente il `Signal` completo.
 *
 * NOTA tipi TS: il generatore `openapi-typescript` (script `npm run
 * generate:api`) emette `lib/api/generated/schema.ts` ma è gitignored.
 * Definiamo qui i tipi minimal del subset usato in US-014, structurally
 * compatibili (drop-in replace).
 */

/** Enum `Signal` allargato — verbatim OpenAPI §components/schemas/Signal. */
export type Signal =
  | 'GREEN'
  | 'YELLOW'
  | 'RED'
  | 'INDETERMINATE'
  | 'NOT_APPLICABLE'
  | 'NOT_CALCULABLE';

/** Alias semantico — `mosSignal` usa l'intero enum `Signal` (include `NOT_APPLICABLE`). */
export type MosSignal = Signal;

/** Enum `DcfMethod` — verbatim OpenAPI §components/schemas/DcfMethod. */
export type DcfMethod = 'GREENWALD' | 'FCF_FALLBACK' | 'NOT_APPLICABLE';

export interface RuleSignal {
  readonly ruleId: string;
  readonly signal: Signal;
  /** Valore osservato; `null` quando la regola non è calcolabile. */
  readonly observedValue: number | null;
  /** Stringa descrittiva della soglia (es. "ROE ≥ 15%"). */
  readonly threshold: string;
  /** Razionale human-readable; opzionale nel contratto, default `''`. */
  readonly rationale?: string;
}

export interface RuleEngineResult {
  readonly ticker: string;
  /** ISO-8601 — momento valutazione Rule Engine. */
  readonly evaluatedAt: string;
  readonly signals: ReadonlyArray<RuleSignal>;
  /** Graham Number USD; `null` se EPS o BVPS non utilizzabili. */
  readonly grahamNumber: number | null;
  /** DCF intrinsic value USD; `null` se metodo non applicabile. */
  readonly dcfIntrinsicValue: number | null;
  /** Metodo DCF effettivamente usato (o `NOT_APPLICABLE`). */
  readonly dcfMethod: DcfMethod;
  /** Semaforo Margin of Safety. */
  readonly mosSignal: MosSignal;
  /** Prezzo corrente al momento della valutazione; `null` se non disponibile. */
  readonly currentPriceAtEval: number | null;
  /** ISO-8601 — momento snapshot dati upstream (FMP). */
  readonly dataSnapshotAt: string;
  /** `true` se i dati sono oltre la soglia di freschezza (US-005/006). */
  readonly isStale?: boolean;
}

/**
 * GET /api/analysis/{ticker} — restituisce `RuleEngineResult`.
 *
 * Headers `X-Data-Snapshot-At` / `X-Data-Stale` letti dal wrapper
 * `apiGet` in `ApiResult.snapshotAt` / `ApiResult.isStale` (TSK-030).
 * Il body include già `dataSnapshotAt` + `isStale`, quindi tipicamente
 * NON serve leggere gli headers; manteniamo l'unwrap canonico a `T`.
 *
 * Per casi in cui il caller voglia accedere agli headers (es. cache
 * conditional), esponiamo anche `getAnalysisRaw` che ritorna l'intero
 * `ApiResult<RuleEngineResult>`.
 *
 * Errori network/4xx/5xx rilanciati al caller (store/componente).
 *  - 404: ticker inesistente.
 *  - 503: dati insufficienti per analisi e FMP non raggiungibile.
 */
export async function getAnalysis(ticker: string): Promise<RuleEngineResult> {
  const normalized = ticker.trim().toUpperCase();
  const result: ApiResult<RuleEngineResult> = await apiGet<RuleEngineResult>(
    `/api/analysis/${encodeURIComponent(normalized)}`,
  );
  return result.data;
}

/**
 * Variante che espone l'intero `ApiResult` (headers inclusi).
 * Usata quando il caller vuole correlare `X-Data-Stale` (header) con
 * `result.isStale` (body) — per ora non usata, esposta per estensione.
 */
export async function getAnalysisRaw(
  ticker: string,
): Promise<ApiResult<RuleEngineResult>> {
  const normalized = ticker.trim().toUpperCase();
  return apiGet<RuleEngineResult>(
    `/api/analysis/${encodeURIComponent(normalized)}`,
  );
}
