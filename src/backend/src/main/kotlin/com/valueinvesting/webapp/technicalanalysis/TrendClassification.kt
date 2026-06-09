package com.valueinvesting.webapp.technicalanalysis

// Trend primario sul timeframe daily — classificazione deterministica
// US-098 §"Classificazione del trend primario (deterministica)" e
// [[ta-vs-vi-decision-layer]] §"La regola sequenziale".
//
// Regole:
//   - UPTREND     : prezzo > SMA50 > SMA200 con slope SMA200 positivo
//                   (regressione lineare ultime 20 sedute, coeff > 0).
//   - DOWNTREND   : prezzo < SMA50 < SMA200 con slope SMA200 negativo.
//   - SIDEWAYS    : ogni altra combinazione (incluso |slope| < soglia di
//                   significativita' documentata in TrendClassifier).
//   - INDETERMINATE: storico < 200 sedute (impossibile calcolare SMA200).
//
// IMPORTANTE: la classificazione e' input per l'EntryTimingAdvisor (US-099) ed
// e' parte del payload TA — NON contribuisce ai 13 RuleSignal del Rule Engine
// (che restano puramente fondamentali). Coerente con la "lente di valore" che
// ammette la TA come layer advisory di timing, mai come strategia autonoma.
// [^src: memory/semantic/value-investing-design-lens.md]
//
// [^src: wiki/concepts/dow-theory.md]
// [^src: wiki/concepts/trend-trendlines-support-resistance.md]
// [^src: wiki/concepts/moving-averages-ta.md]
enum class TrendClassification {
    UPTREND,
    SIDEWAYS,
    DOWNTREND,
    INDETERMINATE,
}
