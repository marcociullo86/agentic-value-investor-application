package com.valueinvesting.webapp.technicalanalysis

import com.valueinvesting.webapp.fmp.dto.EodPriceRecord

// Pure-function builder per il blocco `priceContext` del payload TA — TSK-325.
// US-098 §"Indicatori in scope (sub-set canonico)" — riga `priceContext`.
//
// Output:
//   - currentPrice: prezzo corrente del titolo (da ProfileDto, passato dal caller).
//   - high52w / low52w: max/min su finestra di MIN_SERIES_DAYS sedute (252).
//   - drawdownFrom52wHigh: (currentPrice − high52w) / high52w, frazione 0..1
//     espressa come MAGNITUDO positiva (es. 0.32 = -32% dal picco).
//
// Nota: la magnitudo positiva e' coerente con il rendering UI ("draw-down 32%")
// del tab TA. Per il signal `panicDiscount` esistente in PriceActionAnalyzer la
// convenzione era invece "negativa" (drawdownPct = -32%) — non interferiamo
// con quel componente che resta invariato (zero regressione).
object PriceContextBuilder {

    const val MIN_SERIES_DAYS: Int = 252

    /**
     * Costruisce il PriceContext da una serie EOD (ordinabile per data) +
     * il prezzo corrente. Ritorna `confidenceReduced=true` quando la serie
     * disponibile e' inferiore alla finestra 52w.
     */
    fun build(currentPrice: Double?, eodPrices: List<EodPriceRecord>): PriceContext {
        val sorted = eodPrices
            .filter { it.close != null && it.date != null }
            .sortedBy { it.date }
            .mapNotNull { it.close }

        val seriesDays = sorted.size
        if (currentPrice == null || currentPrice <= 0.0 || seriesDays == 0) {
            return PriceContext(
                currentPrice = currentPrice,
                high52w = null,
                low52w = null,
                drawdownFrom52wHigh = null,
                seriesDays = seriesDays,
                confidenceReduced = true,
            )
        }
        val window = sorted.takeLast(MIN_SERIES_DAYS)
        val high = window.max()
        val low = window.min()
        val drawdown = if (high > 0.0) {
            // Magnitudo positiva: 0..1; 0 = al picco, 0.32 = -32% dal picco.
            ((high - currentPrice) / high).coerceAtLeast(0.0)
        } else null
        return PriceContext(
            currentPrice = currentPrice,
            high52w = high,
            low52w = low,
            drawdownFrom52wHigh = drawdown,
            seriesDays = seriesDays,
            confidenceReduced = seriesDays < MIN_SERIES_DAYS,
        )
    }
}

/**
 * Blocco `priceContext` del TechnicalAnalysisResponse.
 */
data class PriceContext(
    val currentPrice: Double?,
    val high52w: Double?,
    val low52w: Double?,
    val drawdownFrom52wHigh: Double?,
    val seriesDays: Int,
    val confidenceReduced: Boolean,
)
