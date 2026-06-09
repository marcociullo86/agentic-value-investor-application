package com.valueinvesting.webapp.technicalanalysis

// Modelli pure-data per i livelli strutturali support/resistance del payload TA.
// US-098 §"Indicatori in scope (sub-set canonico)" — riga `levels`.
//
// Tipi (Murphy §Page 85 + Dow Theory):
//   - SWING_LOW / SWING_HIGH : minimo/massimo locale derivato dall'EOD 12m.
//   - RETRACEMENT_33/50/66   : ritracciamenti Murphy 33% / 50% / 66% del range 12m.
//
// Confidence:
//   - HIGH:   swing prominente (range > 5% locale) o livello con piu' tocchi.
//   - MEDIUM: swing standard (range > 2%).
//   - LOW:    livello derivato (retracement) o swing poco prominente.
//
// Ordinamento: ritornati per distanza CRESCENTE dal prezzo corrente
// (support → quelli SOTTO il prezzo, dal piu' vicino al piu' lontano;
//  resistance → quelli SOPRA il prezzo, dal piu' vicino al piu' lontano).
//
// [^src: wiki/concepts/trend-trendlines-support-resistance.md]

enum class SupportType {
    SWING_LOW,
    SWING_HIGH,
    RETRACEMENT_33,
    RETRACEMENT_50,
    RETRACEMENT_66,
}

enum class LevelConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

data class PriceLevel(
    val price: Double,
    val type: SupportType,
    val confidence: LevelConfidence,
)
