package com.valueinvesting.webapp.technicalanalysis

import kotlin.math.abs

// Pure-function finder per i livelli support/resistance — TSK-325 / US-098.
//
// Algoritmo:
//   1) Estrai swing low/high LOCALI dalla serie EOD ultimi 12 mesi via fractal
//      a finestra simmetrica (SWING_WINDOW): un punto è swing-low se è il
//      minimo della finestra (i-w..i+w) e simmetricamente per gli swing-high.
//   2) Calcola i 3 livelli di retracement Murphy (33/50/66%) sul range 12m
//      (min12m, max12m) come livelli derivati universali.
//   3) Filtra:
//        - Support  = livelli STRETTAMENTE SOTTO il prezzo corrente.
//        - Resistance = livelli STRETTAMENTE SOPRA il prezzo corrente.
//   4) Deduplica livelli "vicini" (entro DEDUP_DISTANCE_PCT) preservando
//      quello con confidence piu' alta (in caso di parità, il piu' vicino
//      al prezzo).
//   5) Ordina per distanza dal prezzo (crescente) e tronca a MAX_LEVELS (3).
//
// Confidence:
//   - Swing low/high "prominenti" (range relativo > PROMINENT_RANGE_PCT) → HIGH.
//   - Swing low/high standard → MEDIUM.
//   - Retracement (derivato algoritmico) → LOW.
//
// [^src: wiki/concepts/trend-trendlines-support-resistance.md] (Murphy §Page 85)
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md §"Screen 3 — Livello d'entry"]
object SupportResistanceFinder {

    // Finestra simmetrica per la detection swing: un punto è swing se è min/max
    // su (i-SWING_WINDOW..i+SWING_WINDOW). 5 = ~settimana lavorativa, evita
    // micro-swing intra-week dominati dal noise giornaliero.
    const val SWING_WINDOW: Int = 5

    // Massimo numero di livelli ritornati per lato (support / resistance).
    const val MAX_LEVELS: Int = 3

    // Soglia di dedup: livelli a distanza relativa inferiore a questa frazione
    // sono considerati lo stesso (es. swing-low $47.42 e retracement $47.50
    // collassano nello swing). 0.5% e' coerente con il buffer usato dallo
    // StopPlacementAdvisor (US-100).
    const val DEDUP_DISTANCE_PCT: Double = 0.005

    // Range relativo (max-min) della finestra che qualifica uno swing come
    // "prominente" → confidence HIGH. 5% e' la soglia tipica usata in letteratura
    // chart-pattern (Murphy §Page 85) per distinguere swing significativi da
    // oscillazioni di breve.
    const val PROMINENT_RANGE_PCT: Double = 0.05

    /**
     * Estrae i livelli support/resistance dalla serie EOD (ordinata cronologicamente
     * asc) e dal prezzo corrente. La serie deve contenere ~252 sedute (12 mesi);
     * il caller e' responsabile dello slicing.
     */
    fun find(currentPrice: Double, eodCloses: List<Double>): SupportResistanceLevels {
        if (currentPrice <= 0.0 || eodCloses.size < SWING_WINDOW * 2 + 1) {
            return SupportResistanceLevels(support = emptyList(), resistance = emptyList())
        }

        val swings = detectSwings(eodCloses)
        val retracements = computeRetracements(eodCloses)

        val supports = (swings + retracements)
            .asSequence()
            .filter { it.price < currentPrice }
            .sortedBy { currentPrice - it.price }
            .let { dedup(it.toList()) }
            .take(MAX_LEVELS)
            .toList()

        val resistances = (swings + retracements)
            .asSequence()
            .filter { it.price > currentPrice }
            .sortedBy { it.price - currentPrice }
            .let { dedup(it.toList()) }
            .take(MAX_LEVELS)
            .toList()

        return SupportResistanceLevels(support = supports, resistance = resistances)
    }

    internal fun detectSwings(closes: List<Double>): List<PriceLevel> {
        if (closes.size < SWING_WINDOW * 2 + 1) return emptyList()
        val results = mutableListOf<PriceLevel>()
        for (i in SWING_WINDOW until closes.size - SWING_WINDOW) {
            val pivot = closes[i]
            val window = closes.subList(i - SWING_WINDOW, i + SWING_WINDOW + 1)
            val winMin = window.min()
            val winMax = window.max()
            val range = winMax - winMin
            val relRange = if (winMax > 0) range / winMax else 0.0
            val confidence = if (relRange >= PROMINENT_RANGE_PCT) LevelConfidence.HIGH
                else LevelConfidence.MEDIUM

            if (pivot == winMin) {
                results += PriceLevel(price = pivot, type = SupportType.SWING_LOW, confidence = confidence)
            } else if (pivot == winMax) {
                results += PriceLevel(price = pivot, type = SupportType.SWING_HIGH, confidence = confidence)
            }
        }
        return results
    }

    internal fun computeRetracements(closes: List<Double>): List<PriceLevel> {
        if (closes.isEmpty()) return emptyList()
        val low = closes.min()
        val high = closes.max()
        if (high <= low) return emptyList()
        val range = high - low
        return listOf(
            PriceLevel(price = low + range * 0.33, type = SupportType.RETRACEMENT_33, confidence = LevelConfidence.LOW),
            PriceLevel(price = low + range * 0.50, type = SupportType.RETRACEMENT_50, confidence = LevelConfidence.LOW),
            PriceLevel(price = low + range * 0.66, type = SupportType.RETRACEMENT_66, confidence = LevelConfidence.LOW),
        )
    }

    /**
     * Deduplica livelli vicini (entro [DEDUP_DISTANCE_PCT] del prezzo del candidato),
     * preservando la confidence piu' alta tra i duplicati. L'input deve essere
     * gia' ordinato per distanza dal prezzo corrente (il primo elemento vince
     * la posizione, gli "absorbed" cedono solo la propria confidence).
     */
    internal fun dedup(sorted: List<PriceLevel>): List<PriceLevel> {
        val out = mutableListOf<PriceLevel>()
        for (cand in sorted) {
            val dup = out.firstOrNull { kept ->
                val ref = kept.price
                ref > 0.0 && abs(cand.price - ref) / ref < DEDUP_DISTANCE_PCT
            }
            if (dup == null) {
                out += cand
            } else if (cand.confidence > dup.confidence) {
                // Sostituisce: il livello assorbito porta una confidence piu' alta.
                val idx = out.indexOf(dup)
                out[idx] = dup.copy(confidence = cand.confidence)
            }
        }
        return out
    }
}

/**
 * Tuple di livelli ritornata da [SupportResistanceFinder]. I support sono
 * SOTTO il prezzo corrente, le resistance SOPRA — ordinati per distanza
 * crescente dal prezzo.
 */
data class SupportResistanceLevels(
    val support: List<PriceLevel>,
    val resistance: List<PriceLevel>,
)
