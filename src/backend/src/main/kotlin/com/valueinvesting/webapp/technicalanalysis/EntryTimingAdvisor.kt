package com.valueinvesting.webapp.technicalanalysis

import com.valueinvesting.webapp.api.model.EntryTimingAdvisor as EntryTimingAdvisorDto
import com.valueinvesting.webapp.api.model.EntryTimingRationale
import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.api.model.ReentryCondition
import com.valueinvesting.webapp.api.model.ReentryConditionCode
import org.springframework.stereotype.Component
import java.util.Locale

// EntryTimingAdvisor — verdetto Triple-Screen Elder deterministico.
// EP-024 / US-099 / TSK-328.
//
// Implementazione DICHIARATIVA (no if/else annidati > 1 livello):
// `MAPPING_TABLE` e' una lista di righe MatchRule(predicate → outcome) valutata
// in ordine. La prima riga che matcha vince. Le righe coprono esaustivamente
// la tabella US-099 §"Tabella di mapping (Screen 1 × Screen 2 × Screen 3)";
// l'ultima riga e' un catch-all che chiude lo spazio degli stati.
//
// Pure-function: no I/O, no LLM, no persistenza. Riusabile dal backtest US-105
// in modalita' "as-of date" senza side-effects.
//
// Kdoc cita [[ta-entry-timing-stock-detail]] e [[elder-triple-screen-impulse-system]].
//
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md]
// [^src: wiki/concepts/elder-triple-screen-impulse-system.md]
// [^src: wiki/concepts/oscillators-momentum-rsi.md]
@Component
class EntryTimingAdvisor {

    /**
     * Input necessari al verdetto Triple-Screen. Tutti puri valori, nessuna
     * dipendenza da FmpAdapter o DB.
     */
    data class Input(
        val trend: TrendClassification,
        val rsi14: Double?,
        val macdDaily: Double?,
        val macdWeekly: Double?,
        val currentPrice: Double?,
        val nearestSupport: Double?,
    )

    /** RSI zone: Wilder 1978 (replicata in TradingView, FMP, Bloomberg). */
    enum class RsiZone { OVERSOLD, NEUTRAL, OVERBOUGHT, UNKNOWN }

    /**
     * Direction interpretata sul MACD daily.
     *  - BULLISH_STRONG: MACD > 0 e in salita marcata (>= MACD_STRONG_BULLISH).
     *  - BULLISH:        MACD > 0.
     *  - FLAT:           |MACD| sotto soglia di significativita'.
     *  - BEARISH:        MACD < 0.
     *  - UNKNOWN:        MACD null.
     */
    enum class MacdDirection { BULLISH_STRONG, BULLISH, FLAT, BEARISH, UNKNOWN }

    /** Distance bucket dal support piu' vicino: <5% / 5..15% / oltre / unknown. */
    enum class SupportDistance { WITHIN_5_PCT, WITHIN_15_PCT, BEYOND_15_PCT, UNKNOWN }

    fun advise(input: Input): EntryTimingAdvisorDto {
        if (input.trend == TrendClassification.INDETERMINATE) {
            return indeterminate()
        }
        val rsi = rsiZone(input.rsi14)
        val macd = macdDirection(input.macdDaily)
        val dist = supportDistance(input.currentPrice, input.nearestSupport)

        val outcome = MAPPING_TABLE.firstOrNull { it.matches(input.trend, rsi, macd, dist) }
            ?: CATCH_ALL_OUTCOME

        return EntryTimingAdvisorDto(
            verdict = outcome.verdict,
            reentryCondition = outcome.reentryCode?.let { ReentryCondition(it, reentryDescription(it)) },
            rationale = buildRationale(input, rsi, macd, dist),
        )
    }

    // ------------------------------------------------------------------------
    // Tabella di mapping DICHIARATIVA
    // ------------------------------------------------------------------------

    private data class MatchRule(
        val trends: Set<TrendClassification>,
        val rsis: Set<RsiZone>,
        val macds: Set<MacdDirection>,
        val distances: Set<SupportDistance>,
        val verdict: EntryTimingVerdict,
        val reentryCode: ReentryConditionCode? = null,
    ) {
        fun matches(
            trend: TrendClassification,
            rsi: RsiZone,
            macd: MacdDirection,
            dist: SupportDistance,
        ): Boolean =
            trend in trends && rsi in rsis && macd in macds && dist in distances
    }

    private companion object {

        // Tutti i valori non-INDETERMINATE / non-UNKNOWN — utili per i wildcard.
        val ANY_RSI: Set<RsiZone> = setOf(RsiZone.OVERSOLD, RsiZone.NEUTRAL, RsiZone.OVERBOUGHT, RsiZone.UNKNOWN)
        val ANY_MACD: Set<MacdDirection> = setOf(
            MacdDirection.BULLISH_STRONG, MacdDirection.BULLISH, MacdDirection.FLAT,
            MacdDirection.BEARISH, MacdDirection.UNKNOWN,
        )
        val ANY_DIST: Set<SupportDistance> = setOf(
            SupportDistance.WITHIN_5_PCT, SupportDistance.WITHIN_15_PCT,
            SupportDistance.BEYOND_15_PCT, SupportDistance.UNKNOWN,
        )

        val BULLISH_ANY: Set<MacdDirection> = setOf(MacdDirection.BULLISH, MacdDirection.BULLISH_STRONG)
        val NON_BULLISH: Set<MacdDirection> = setOf(MacdDirection.FLAT, MacdDirection.BEARISH, MacdDirection.UNKNOWN)

        // Soglie. RSI standard Wilder. MACD: la "direction strong bullish" e' una
        // valutazione qualitativa: data l'assenza di una soglia universalmente
        // accettata, usiamo "MACD > MACD_STRONG_BULLISH_ABS" come euristica.
        // L'output non e' sensibile alla scelta esatta della soglia (cambia solo
        // il ranking BULLISH vs BULLISH_STRONG, non il verdetto finale).
        const val RSI_OVERSOLD_LT: Double = 30.0
        const val RSI_OVERBOUGHT_GT: Double = 70.0
        const val MACD_FLAT_ABS: Double = 0.05   // |macd| < 0.05 ⇒ FLAT
        const val MACD_STRONG_BULLISH_ABS: Double = 1.0  // macd > 1.0 ⇒ BULLISH_STRONG (qualitativo)

        // Tabella di mapping US-099 §"Tabella di mapping". L'ordine conta: la
        // prima riga che matcha vince. Le righe sono scritte per essere
        // mutualmente esclusive negli intervalli rilevanti; il CATCH_ALL chiude
        // gli stati non altrimenti codificati a INDETERMINATE.
        val MAPPING_TABLE: List<MatchRule> = listOf(

            // ============== DOWNTREND ===========================================
            // Oversold + MACD bullish forte + entro 5% support → WAIT (re-entry
            // prezzo > SMA200 con conferma volume). Murphy §Page 239: divergenza
            // di lungo, segnale "attenzione" non ENTRY.
            MatchRule(
                trends = setOf(TrendClassification.DOWNTREND),
                rsis = setOf(RsiZone.OVERSOLD),
                macds = setOf(MacdDirection.BULLISH_STRONG),
                distances = setOf(SupportDistance.WITHIN_5_PCT),
                verdict = EntryTimingVerdict.WAIT,
                reentryCode = ReentryConditionCode.PRICE_ABOVE_SMA200_WITH_VOLUME,
            ),
            // DOWNTREND altro qualsiasi → ENTRY_UNFAVORABLE.
            MatchRule(
                trends = setOf(TrendClassification.DOWNTREND),
                rsis = ANY_RSI,
                macds = ANY_MACD,
                distances = ANY_DIST,
                verdict = EntryTimingVerdict.ENTRY_UNFAVORABLE,
            ),

            // ============== UPTREND =============================================
            // Overbought + MACD piatto/ribassista (qualsiasi distanza) → WAIT
            // (re-entry RSI < 50).
            MatchRule(
                trends = setOf(TrendClassification.UPTREND),
                rsis = setOf(RsiZone.OVERBOUGHT),
                macds = NON_BULLISH,
                distances = ANY_DIST,
                verdict = EntryTimingVerdict.WAIT,
                reentryCode = ReentryConditionCode.RSI_BELOW_50,
            ),
            // Overbought + MACD bullish strong → ENTRY_NEUTRAL (in trend forti
            // RSI puo' restare overbought a lungo — Murphy §Page 239).
            MatchRule(
                trends = setOf(TrendClassification.UPTREND),
                rsis = setOf(RsiZone.OVERBOUGHT),
                macds = setOf(MacdDirection.BULLISH_STRONG),
                distances = ANY_DIST,
                verdict = EntryTimingVerdict.ENTRY_NEUTRAL,
            ),
            // Overbought + MACD bullish (non-strong) → ENTRY_NEUTRAL.
            MatchRule(
                trends = setOf(TrendClassification.UPTREND),
                rsis = setOf(RsiZone.OVERBOUGHT),
                macds = setOf(MacdDirection.BULLISH),
                distances = ANY_DIST,
                verdict = EntryTimingVerdict.ENTRY_NEUTRAL,
            ),
            // Oversold + MACD bullish + entro 5% support → ENTRY_FAVORABLE.
            MatchRule(
                trends = setOf(TrendClassification.UPTREND),
                rsis = setOf(RsiZone.OVERSOLD),
                macds = BULLISH_ANY,
                distances = setOf(SupportDistance.WITHIN_5_PCT),
                verdict = EntryTimingVerdict.ENTRY_FAVORABLE,
            ),
            // Neutral + MACD bullish + entro 5% support → ENTRY_FAVORABLE.
            MatchRule(
                trends = setOf(TrendClassification.UPTREND),
                rsis = setOf(RsiZone.NEUTRAL),
                macds = BULLISH_ANY,
                distances = setOf(SupportDistance.WITHIN_5_PCT),
                verdict = EntryTimingVerdict.ENTRY_FAVORABLE,
            ),
            // Neutral + MACD bullish + 5..15% support → ENTRY_NEUTRAL.
            MatchRule(
                trends = setOf(TrendClassification.UPTREND),
                rsis = setOf(RsiZone.NEUTRAL),
                macds = BULLISH_ANY,
                distances = setOf(SupportDistance.WITHIN_15_PCT),
                verdict = EntryTimingVerdict.ENTRY_NEUTRAL,
            ),
            // UPTREND catch-all (MACD non chiaro, support lontano, ecc.) →
            // ENTRY_NEUTRAL (mai unfavorable in uptrend salvo override sopra).
            MatchRule(
                trends = setOf(TrendClassification.UPTREND),
                rsis = ANY_RSI,
                macds = ANY_MACD,
                distances = ANY_DIST,
                verdict = EntryTimingVerdict.ENTRY_NEUTRAL,
            ),

            // ============== SIDEWAYS ============================================
            // Overbought (qualsiasi MACD, qualsiasi distanza) → WAIT (re-entry
            // RSI < 50).
            MatchRule(
                trends = setOf(TrendClassification.SIDEWAYS),
                rsis = setOf(RsiZone.OVERBOUGHT),
                macds = ANY_MACD,
                distances = ANY_DIST,
                verdict = EntryTimingVerdict.WAIT,
                reentryCode = ReentryConditionCode.RSI_BELOW_50,
            ),
            // Oversold + MACD bullish + entro 5% support → ENTRY_NEUTRAL.
            MatchRule(
                trends = setOf(TrendClassification.SIDEWAYS),
                rsis = setOf(RsiZone.OVERSOLD),
                macds = BULLISH_ANY,
                distances = setOf(SupportDistance.WITHIN_5_PCT),
                verdict = EntryTimingVerdict.ENTRY_NEUTRAL,
            ),
            // SIDEWAYS catch-all → ENTRY_NEUTRAL.
            MatchRule(
                trends = setOf(TrendClassification.SIDEWAYS),
                rsis = ANY_RSI,
                macds = ANY_MACD,
                distances = ANY_DIST,
                verdict = EntryTimingVerdict.ENTRY_NEUTRAL,
            ),
        )

        // Catch-all difensivo: stati non altrimenti codificati → INDETERMINATE
        // (mai eseguito in pratica per i 4 valori non-INDETERMINATE di trend,
        // ma serve come sicurezza contro estensioni future della tabella).
        val CATCH_ALL_OUTCOME = MatchRule(
            trends = emptySet(),
            rsis = emptySet(),
            macds = emptySet(),
            distances = emptySet(),
            verdict = EntryTimingVerdict.INDETERMINATE,
        )

        const val WIKI_TRIPLE_SCREEN: String = "elder-triple-screen-impulse-system"
        const val WIKI_SCREEN1: String = "ta-entry-timing-stock-detail#screen-1"
        const val WIKI_SCREEN2: String = "ta-entry-timing-stock-detail#screen-2"
        const val WIKI_SCREEN3: String = "ta-entry-timing-stock-detail#screen-3"
        const val WIKI_RSI: String = "oscillators-momentum-rsi"
        const val WIKI_SMA: String = "moving-averages-ta"
    }

    private fun indeterminate(): EntryTimingAdvisorDto = EntryTimingAdvisorDto(
        verdict = EntryTimingVerdict.INDETERMINATE,
        reentryCondition = null,
        rationale = EntryTimingRationale(
            screen1 = "Trend INDETERMINATE: storico EOD insufficiente (< 200 sedute).",
            screen2 = "Screen 2 non valutabile in assenza di trend di lungo.",
            screen3 = "Screen 3 non valutabile in assenza di trend di lungo.",
            wikiCitations = listOf(WIKI_TRIPLE_SCREEN, WIKI_SMA),
        ),
    )

    internal fun rsiZone(rsi: Double?): RsiZone = when {
        rsi == null -> RsiZone.UNKNOWN
        rsi < RSI_OVERSOLD_LT -> RsiZone.OVERSOLD
        rsi > RSI_OVERBOUGHT_GT -> RsiZone.OVERBOUGHT
        else -> RsiZone.NEUTRAL
    }

    internal fun macdDirection(macd: Double?): MacdDirection = when {
        macd == null -> MacdDirection.UNKNOWN
        macd > MACD_STRONG_BULLISH_ABS -> MacdDirection.BULLISH_STRONG
        macd > MACD_FLAT_ABS -> MacdDirection.BULLISH
        macd < -MACD_FLAT_ABS -> MacdDirection.BEARISH
        else -> MacdDirection.FLAT
    }

    internal fun supportDistance(price: Double?, nearestSupport: Double?): SupportDistance {
        if (price == null || price <= 0.0 || nearestSupport == null || nearestSupport <= 0.0) {
            return SupportDistance.UNKNOWN
        }
        val pct = (price - nearestSupport) / price
        return when {
            pct < 0.05 -> SupportDistance.WITHIN_5_PCT
            pct < 0.15 -> SupportDistance.WITHIN_15_PCT
            else -> SupportDistance.BEYOND_15_PCT
        }
    }

    private fun reentryDescription(code: ReentryConditionCode): String = when (code) {
        ReentryConditionCode.RSI_BELOW_50 ->
            "Re-valuta quando RSI 14d rientra sotto 50."
        ReentryConditionCode.PRICE_ABOVE_SMA200_WITH_VOLUME ->
            "Re-valuta quando il prezzo torna sopra SMA200 con conferma volume."
        ReentryConditionCode.PULLBACK_TO_SUPPORT_50PCT ->
            "Re-valuta su pullback al 50% di retracement del range 12 mesi."
    }

    private fun buildRationale(
        input: Input,
        rsi: RsiZone,
        macd: MacdDirection,
        dist: SupportDistance,
    ): EntryTimingRationale {
        val rsiStr = input.rsi14?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "n/d"
        val macdDailyStr = input.macdDaily?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "n/d"
        val macdWeeklyStr = input.macdWeekly?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "n/d"
        val supportStr = input.nearestSupport?.let { String.format(Locale.ROOT, "$%.2f", it) } ?: "n/d"
        val distStr = if (input.nearestSupport != null && input.currentPrice != null) {
            String.format(
                Locale.ROOT,
                "%.1f%%",
                100.0 * (input.currentPrice - input.nearestSupport) / input.currentPrice,
            )
        } else "n/d"

        return EntryTimingRationale(
            screen1 = "Screen 1 (trend di lungo): ${input.trend} — MACD weekly $macdWeeklyStr.",
            screen2 = "Screen 2 (oscillatore): RSI14 $rsiStr (zona $rsi), MACD daily $macdDailyStr (direzione $macd).",
            screen3 = "Screen 3 (livello d'entry): support piu' vicino $supportStr ($distStr sotto il prezzo — bucket $dist).",
            wikiCitations = listOf(WIKI_SCREEN1, WIKI_SCREEN2, WIKI_SCREEN3, WIKI_RSI, WIKI_TRIPLE_SCREEN),
        )
    }
}
