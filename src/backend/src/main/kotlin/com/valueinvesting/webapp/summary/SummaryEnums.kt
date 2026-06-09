package com.valueinvesting.webapp.summary

import io.swagger.v3.oas.annotations.media.Schema

// Enum tipati del Summary cross-dominio (EP-024 / US-103 / TSK-340).
//
// Convenzione: ogni enum esposto via OpenAPI con `@Schema(name = ...)` springdoc
// per garantire il match 1:1 con il canonical openapi.yaml (contract drift test).
// Stile coerente con EntryTimingVerdict / StopType (US-099/US-100, ADR-030 §5).
//
// [^src: management/kanban/EP-024-.../US-103-aggregatore-riepilogo-cross-dominio-be/US-103.md §"Verdetto Summary — tassonomia"]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §5]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"La regola sequenziale"]

/**
 * Verdetto sintetico azionabile del Riepilogo (US-103).
 *
 *  - `ENTER_NOW` — VI gate passato + Deep OK/NOT_AVAILABLE + TA favorable/neutral.
 *  - `WAIT_FOR_SETUP` — VI gate passato ma TA o Deep sfavorevole (situazione COPART:
 *    tesi VI corretta ma timing tecnico ostile). Espone `reentryCondition`.
 *  - `AVOID` — VI gate fallito (titolo VI-negativo non puo' MAI diventare ENTER_NOW,
 *    indipendentemente dalla TA — ADR-030 §3 + ta-vs-vi-decision-layer §"La regola
 *    sequenziale"). Include anche Munger RISCHIO_ESTREMO che overrides il gate VI.
 *  - `INSUFFICIENT_DATA` — ≥ 1/3 dei ruleId VI in stato INDETERMINATE/NOT_CALCULABLE
 *    (ADR-030 §3 — soglia proporzionale `INDETERMINATE_DOMINANT`).
 */
@Schema(
    name = "SummaryVerdict",
    description = """
Verdetto azionabile del Riepilogo cross-dominio (US-103):
- ENTER_NOW: VI gate passato + Deep OK/NOT_AVAILABLE + TA favorable/neutral.
- WAIT_FOR_SETUP: VI gate passato ma TA o Deep sfavorevole (situazione COPART) — reentryCondition esposta.
- AVOID: VI gate fallito (titolo VI-negativo non puo' MAI diventare ENTER_NOW) OPPURE Munger RISCHIO_ESTREMO override.
- INSUFFICIENT_DATA: dati VI troppo lacunosi (≥ 1/3 ruleId INDETERMINATE/NOT_CALCULABLE).
""",
)
enum class SummaryVerdict {
    ENTER_NOW,
    WAIT_FOR_SETUP,
    AVOID,
    INSUFFICIENT_DATA,
}

/**
 * Classificazione aggregata del VI verdict, proporzionale sui ruleId decisionali
 * disponibili (ADR-030 §3). NCAV_LATEST e' informativo (ADR-029 §2): escluso dal
 * denominatore. Anche i ruleId in stato INDETERMINATE/NOT_CALCULABLE sono esclusi
 * dal denominatore — se troppi (≥ 1/3) il verdetto degrada a
 * INDETERMINATE_DOMINANT → `SummaryVerdict.INSUFFICIENT_DATA`.
 *
 * Soglie (verbatim ADR-030 §3):
 *   GREEN_DOMINANT          = quota GREEN ≥ 60% dei decisionali disponibili
 *   RED_DOMINANT            = quota GREEN < 33% dei decisionali disponibili
 *   YELLOW_DOMINANT         = intervallo intermedio (33% ≤ quota < 60%)
 *   INDETERMINATE_DOMINANT  = ≥ 1/3 ruleId INDETERMINATE/NOT_CALCULABLE
 */
@Schema(
    name = "ViVerdict",
    description = """
Classificazione aggregata del verdetto VI, proporzionale sui ruleId DECISIONALI
disponibili (ADR-030 §3 — NCAV_LATEST informativo escluso, INDETERMINATE/NOT_CALCULABLE esclusi):
- GREEN_DOMINANT: quota GREEN ≥ 60% dei decisionali disponibili.
- RED_DOMINANT: quota GREEN < 33% dei decisionali disponibili.
- YELLOW_DOMINANT: intervallo intermedio (33% ≤ quota < 60%).
- INDETERMINATE_DOMINANT: ≥ 1/3 dei ruleId sono INDETERMINATE/NOT_CALCULABLE.
""",
)
enum class ViVerdict {
    GREEN_DOMINANT,
    YELLOW_DOMINANT,
    RED_DOMINANT,
    INDETERMINATE_DOMINANT,
}

/**
 * Stato della Deep Analysis (Munger inversion + owner earnings + moat) per il
 * ticker. La Deep e' lazy: se mai eseguita (`NOT_INDEXED`) il Summary continua
 * a funzionare con verdetto basato solo su VI + TA, `deepVerdict = null`
 * esplicito (US-103 AC §"Caso deepAnalysisStatus = NOT_INDEXED").
 */
@Schema(
    name = "DeepAnalysisStatus",
    description = """
Stato della Deep Analysis:
- AVAILABLE: deep analysis indicizzata + analizzata, deepVerdict popolato.
- NOT_INDEXED: nessuna run eseguita per il ticker, deepVerdict = null. Non blocca il Summary.
- NOT_AVAILABLE: deep analysis tecnicamente indisponibile (es. FAILED, schema drift), deepVerdict = null.
""",
)
enum class DeepAnalysisStatus {
    AVAILABLE,
    NOT_INDEXED,
    NOT_AVAILABLE,
}

/**
 * Verdetto sintetico della Deep Analysis (Munger cascade) rispetto al gate del
 * Summary. Riduzione dei `VerdictClass` Munger (APPROVATO, APPROVATO_PANIC_BUY,
 * WATCHLIST, BOCCIATO_*) ai 3 segnali rilevanti per il gate:
 *   - `OK` — verdetto Munger compatibile con ENTER_NOW (APPROVATO / APPROVATO_PANIC_BUY).
 *   - `WATCHLIST` — verdetto Munger WATCHLIST (degrada a WAIT_FOR_SETUP nella tabella).
 *   - `RISCHIO_ESTREMO` — RISCHIO_ESTREMO Munger overrides il gate VI → AVOID
 *     (regola assoluta, US-103 §"Regola assoluta" + munger-inversion-rag §Cascade Logica).
 */
@Schema(
    name = "DeepVerdict",
    description = """
Verdetto sintetico Deep Analysis rispetto al gate del Summary:
- OK: Munger APPROVATO / APPROVATO_PANIC_BUY → compatibile con ENTER_NOW.
- WATCHLIST: Munger WATCHLIST → degrada a WAIT_FOR_SETUP.
- RISCHIO_ESTREMO: Munger RISCHIO_ESTREMO → override gate VI → AVOID (regola assoluta).
""",
)
enum class DeepVerdict {
    OK,
    WATCHLIST,
    RISCHIO_ESTREMO,
}
