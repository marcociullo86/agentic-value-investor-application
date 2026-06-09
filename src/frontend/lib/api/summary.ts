import { apiGet, type ApiResult } from '@/lib/api/client';
import type {
  EntryTimingVerdict,
  ReentryCondition,
} from '@/lib/api/technical';

/**
 * Riepilogo cross-dominio domain API (TSK-342 — US-104, EP-024 Fase 2).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts), allineato pattern
 * `technical.ts` / `deep-analysis.ts` / `historical.ts`.
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml
 *   §/api/analysis/{ticker}/summary — schema `SummaryVerdictResponse`
 *   (EP-024 Fase 2 BE, US-103).
 *
 * NOTA tipi TS: il generatore `openapi-typescript` (script
 * `npm run generate:api`) emette `lib/api/generated/schema.ts` (gitignored).
 * Definiamo qui i tipi minimal del subset usato in US-104, structurally
 * compatibili con le definizioni OpenAPI — drop-in replace quando il
 * generato sarà rigenerato in CI/local (la CI rigenera in stage fe-build).
 *
 * Layer capstone di trasparenza: questo endpoint NON ricalcola alcunché
 * lato FE — il verdetto arriva GIÀ deciso dal BE (gate VI hardcoded in
 * Kotlin, ADR-030 §3+§5). Il FE renderizza soltanto.
 */

/* ------------------------------------------------------------------ */
/*  Enum tipati (verbatim OpenAPI §components/schemas)                  */
/* ------------------------------------------------------------------ */

/**
 * Verdetto azionabile del Riepilogo cross-dominio (US-103):
 *  - ENTER_NOW         → VI gate passato + Deep OK/NOT_AVAILABLE + TA favorable/neutral.
 *  - WAIT_FOR_SETUP    → VI gate passato ma TA o Deep sfavorevole (situazione COPART).
 *  - AVOID             → VI gate fallito OPPURE Munger RISCHIO_ESTREMO.
 *  - INSUFFICIENT_DATA → dati VI troppo lacunosi (≥ 1/3 INDETERMINATE/NOT_CALCULABLE).
 */
export type SummaryVerdict =
  | 'ENTER_NOW'
  | 'WAIT_FOR_SETUP'
  | 'AVOID'
  | 'INSUFFICIENT_DATA';

/**
 * Classificazione aggregata del verdetto VI sui ruleId DECISIONALI disponibili
 * (ADR-030 §3 — NCAV_LATEST informativo escluso, INDETERMINATE/NOT_CALCULABLE
 * esclusi):
 *  - GREEN_DOMINANT         → quota GREEN ≥ 60%.
 *  - RED_DOMINANT           → quota GREEN < 33%.
 *  - YELLOW_DOMINANT        → intervallo intermedio (33% ≤ quota < 60%).
 *  - INDETERMINATE_DOMINANT → ≥ 1/3 ruleId INDETERMINATE/NOT_CALCULABLE.
 */
export type ViVerdict =
  | 'GREEN_DOMINANT'
  | 'YELLOW_DOMINANT'
  | 'RED_DOMINANT'
  | 'INDETERMINATE_DOMINANT';

/**
 * Stato della Deep Analysis (Munger inversion):
 *  - AVAILABLE     → deep indicizzata + analizzata, deepVerdict popolato.
 *  - NOT_INDEXED   → nessuna run eseguita per il ticker, deepVerdict = null.
 *                    Non blocca il Summary. Espone CTA "Indicizza filing →".
 *  - NOT_AVAILABLE → deep tecnicamente indisponibile (es. FAILED, schema drift).
 */
export type DeepAnalysisStatus = 'AVAILABLE' | 'NOT_INDEXED' | 'NOT_AVAILABLE';

/**
 * Verdetto sintetico Deep Analysis rispetto al gate del Summary:
 *  - OK              → Munger APPROVATO / APPROVATO_PANIC_BUY → compatibile con ENTER_NOW.
 *  - WATCHLIST       → Munger WATCHLIST → degrada a WAIT_FOR_SETUP.
 *  - RISCHIO_ESTREMO → Munger RISCHIO_ESTREMO → override gate VI → AVOID (regola assoluta).
 */
export type DeepVerdict = 'OK' | 'WATCHLIST' | 'RISCHIO_ESTREMO';

/* ------------------------------------------------------------------ */
/*  Citazioni wiki cross-dominio (US-103 §RAG)                          */
/* ------------------------------------------------------------------ */

/**
 * Citazione di una pagina wiki cross-dominio (US-103 §"Citazioni RAG
 * cross-dominio"). `id` = slug della pagina (= `wiki_source_id` in
 * `filing_chunks` BE).
 */
export interface WikiCitation {
  readonly id: string;
  /** Ancora opzionale al paragrafo specifico della pagina wiki. */
  readonly anchor: string | null;
  /**
   * Dominio della pagina wiki — uno tra:
   *  - 'value-investing'           (VI rule engine, MoS, intrinsic value, …)
   *  - 'technical-analysis-trading' (Elder, Murphy, decision layer, …)
   * Stringa libera lato OpenAPI; raggruppata in UI dal componente di sezione
   * citazioni (TSK-343).
   */
  readonly domain: string;
}

/* ------------------------------------------------------------------ */
/*  Rationale narrativo                                                 */
/* ------------------------------------------------------------------ */

/**
 * Rationale narrativo del Riepilogo. I 3 *Summary sono generati dall'LLM
 * (1 sola call lato BE); il `decisionPath` è deterministico (mai LLM).
 */
export interface SummaryRationale {
  /** Sintesi narrativa del verdetto VI (LLM o fallback deterministico). */
  readonly viSummary: string;
  /** Sintesi narrativa della Deep Analysis. Null se deepAnalysisStatus != AVAILABLE. */
  readonly deepSummary: string | null;
  /** Sintesi narrativa del verdetto TA. Null se taVerdict = null. */
  readonly taSummary: string | null;
  /** Riassunto testuale deterministico del gate applicato. Sempre presente. */
  readonly decisionPath: string;
}

/* ------------------------------------------------------------------ */
/*  Root payload                                                        */
/* ------------------------------------------------------------------ */

/**
 * Payload del Riepilogo cross-dominio VI + Deep + TA (EP-024 Fase 2, US-103).
 *
 * Verdetti tipati (`summaryVerdict`, `viVerdict`, `deepVerdict`, `taVerdict`)
 * prodotti da pure-function Kotlin con tabella di mapping hardcoded (ADR-030
 * §3+§5). L'LLM (1 sola call) genera SOLO i 3 campi narrativi del `rationale`.
 *
 * Gate VI primario hardcoded: un titolo con `viVerdict = RED_DOMINANT` non
 * può MAI diventare `ENTER_NOW`. Munger `RISCHIO_ESTREMO` override → AVOID.
 *
 * `warningAntiCopart` presente SOLO quando `viVerdict = GREEN_DOMINANT` AND
 * `taVerdict ∈ {WAIT, ENTRY_UNFAVORABLE}` AND `summaryVerdict = WAIT_FOR_SETUP`.
 */
export interface SummaryVerdictResponse {
  readonly ticker: string;
  /** ISO-8601 — istante valutazione lato BE. */
  readonly evaluatedAt: string;
  readonly summaryVerdict: SummaryVerdict;
  readonly viVerdict: ViVerdict;
  readonly deepAnalysisStatus: DeepAnalysisStatus;
  /** Null quando deepAnalysisStatus != AVAILABLE (US-103 AC). */
  readonly deepVerdict: DeepVerdict | null;
  /** Null quando la TA non è calcolabile (FMP indisponibile). */
  readonly taVerdict: EntryTimingVerdict | null;
  readonly rationale: SummaryRationale;
  /**
   * Condizione tecnica di re-entry. Popolata quando
   * `summaryVerdict = WAIT_FOR_SETUP` AND `taVerdict = WAIT`.
   * Propagata dall'EntryTimingAdvisor (US-099).
   */
  readonly reentryCondition: ReentryCondition | null;
  /** Citazioni RAG cross-dominio (US-103 §"Citazioni RAG cross-dominio"). */
  readonly wikiCitations: ReadonlyArray<WikiCitation>;
  /**
   * Warning anti-COPART. Presente SOLO quando viVerdict = GREEN_DOMINANT AND
   * taVerdict ∈ {WAIT, ENTRY_UNFAVORABLE} AND summaryVerdict = WAIT_FOR_SETUP.
   */
  readonly warningAntiCopart: string | null;
}

/* ------------------------------------------------------------------ */
/*  Fetcher                                                             */
/* ------------------------------------------------------------------ */

/**
 * GET /api/analysis/{ticker}/summary
 *
 * Errori HTTP rilanciati al caller (hook/componente):
 *  - 404 → ticker inesistente
 *  - 503 → BE down / dipendenze upstream non disponibili (ProblemDetail body)
 */
export async function getSummary(
  ticker: string,
): Promise<SummaryVerdictResponse> {
  const normalized = ticker.trim().toUpperCase();
  const result: ApiResult<SummaryVerdictResponse> =
    await apiGet<SummaryVerdictResponse>(
      `/api/analysis/${encodeURIComponent(normalized)}/summary`,
    );
  return result.data;
}
