package com.valueinvesting.webapp.contextflags

// Container per i flag advisory di Mr. Market (EP-013).
// Esposto come sezione opzionale `contextFlags` di RuleEngineResultResponse,
// DISTINTA dai 13 `signals` del Rule Engine (questi ultimi contribuiscono alla
// Margin of Safety, i context flag NO).
//
// Tutti i field nullable per permettere il backfill incrementale (TSK-165
// popola solo mrMarketRsi; TSK-166 popola anche longTermTrend). Field aggiunti
// in futuro (es. volatilityRegime, sectorRotation) seguiranno la stessa
// convenzione additive non-breaking.
//
// Serializzazione: Jackson default → null fields restano in JSON output.
// Se il frontend ha bisogno di omettere i null, applicare
// @JsonInclude(JsonInclude.Include.NON_NULL) a livello data class.
//
// [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-165.md]
// [^src: management/kanban/EP-013-mr-market-context-flags/US-057-sma200-trend-context-flag/TSK-166.md]
data class ContextFlags(
    val mrMarketRsi: MrMarketRsiFlag? = null,
    val longTermTrend: LongTermTrendFlag? = null,
)
