package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema

// DTO Entry-Timing Advisor — verdetto Triple-Screen di timing (EP-024, US-099,
// TSK-328). Layer ADVISORY: NON sostituisce il verdetto VI del Rule Engine.
//
// Il payload include esplicitamente `viGate = "this_advisor_assumes_vi_verdict_positive"`
// come disclaimer machine-readable: il Riepilogo (US-103) e' l'unico autorizzato a
// comporre verdetto VI + TA per produrre la raccomandazione finale.
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-099-entry-timing-advisor-be/US-099.md]
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md]
// [^src: wiki/concepts/elder-triple-screen-impulse-system.md]

@Schema(
    name = "EntryTimingVerdict",
    description = """
Verdetto Triple-Screen Elder (Screen 1 trend × Screen 2 oscillatore × Screen 3 livello d'entry):
- ENTRY_FAVORABLE: Screen 1 favorevole + Screen 2 favorevole + prezzo entro 5% da support.
- ENTRY_NEUTRAL: ambiguo (Screen 1 OK ma Screen 2 incerto, o viceversa).
- ENTRY_UNFAVORABLE: trend sfavorevole (DOWNTREND) e/o RSI Overbought.
- WAIT: trend favorevole ma RSI Overbought con MACD piatto/ribassista; attendere pullback (reentryCondition esposta).
- INDETERMINATE: dati insufficienti.
""",
)
enum class EntryTimingVerdict {
    ENTRY_FAVORABLE,
    ENTRY_NEUTRAL,
    ENTRY_UNFAVORABLE,
    WAIT,
    INDETERMINATE,
}

@Schema(
    name = "ReentryConditionCode",
    description = """
Condizione machine-readable che sblocca la rivalutazione del WAIT:
- RSI_BELOW_50: Re-valuta quando RSI 14d scende sotto 50.
- PRICE_ABOVE_SMA200_WITH_VOLUME: Re-valuta quando il prezzo torna sopra SMA200 con conferma volume.
- PULLBACK_TO_SUPPORT_50PCT: Re-valuta su pullback al 50% di retracement del range 12m.
""",
)
enum class ReentryConditionCode {
    RSI_BELOW_50,
    PRICE_ABOVE_SMA200_WITH_VOLUME,
    PULLBACK_TO_SUPPORT_50PCT,
}

@Schema(name = "ReentryCondition", description = "Condizione di re-entry per WAIT (machine + human readable).")
data class ReentryCondition(
    val code: ReentryConditionCode,
    val description: String,
)

@Schema(name = "EntryTimingRationale", description = "Rationale strutturato Triple-Screen — note testuali per Screen 1/2/3 + citazioni wiki.")
data class EntryTimingRationale(
    val screen1: String,
    val screen2: String,
    val screen3: String,
    val wikiCitations: List<String>,
)

@Schema(name = "EntryTimingAdvisor", description = "Verdetto di timing Triple-Screen (US-099, TSK-328). Layer ADVISORY, NON sostituisce il verdetto VI.")
data class EntryTimingAdvisor(
    val verdict: EntryTimingVerdict,
    val reentryCondition: ReentryCondition?,
    val rationale: EntryTimingRationale,
    /**
     * Disclaimer machine-readable. Costante per design: il valore e' SEMPRE
     * `"this_advisor_assumes_vi_verdict_positive"`. Esposto come field separato
     * (non costante implicita) cosi' il FE puo' renderizzarlo direttamente
     * senza hard-coding.
     */
    @field:Schema(
        description = "Disclaimer machine-readable. Sempre 'this_advisor_assumes_vi_verdict_positive'.",
        example = "this_advisor_assumes_vi_verdict_positive",
    )
    val viGate: String = "this_advisor_assumes_vi_verdict_positive",
)
