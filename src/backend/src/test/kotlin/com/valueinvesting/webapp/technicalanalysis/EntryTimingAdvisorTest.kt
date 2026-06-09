package com.valueinvesting.webapp.technicalanalysis

import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.api.model.ReentryConditionCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit test per EntryTimingAdvisor (TSK-329 / US-099).
//
// Copre TUTTE le righe della tabella di mapping US-099
// §"Tabella di mapping (Screen 1 × Screen 2 × Screen 3)" + 4 fixture integrazione
// (AAPL, CPRT-style, MSFT, downtrend).
//
// Regole di mapping usate come valori di confine:
//   - RSI_OVERSOLD  : rsi < 30.0 (strict)
//   - RSI_OVERBOUGHT: rsi > 70.0 (strict)
//   - RSI_NEUTRAL   : 30.0 ≤ rsi ≤ 70.0
//   - MACD_FLAT     : |macd| < 0.05
//   - MACD_BULLISH  : macd > 0.05 e macd ≤ 1.0
//   - MACD_BULLISH_STRONG: macd > 1.0
//   - MACD_BEARISH  : macd < -0.05
//   - WITHIN_5_PCT  : (price - support) / price < 0.05
//   - WITHIN_15_PCT : 0.05 ≤ (price - support) / price < 0.15
//   - BEYOND_15_PCT : (price - support) / price ≥ 0.15
//
// Nessuna LLM, nessuna dipendenza da FmpAdapter — pure-function Kotlin.
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-099-entry-timing-advisor-be/TSK-329.md]
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md §"Regola di timing derivata dai due badge combinati"]
// [^src: wiki/concepts/elder-triple-screen-impulse-system.md]
class EntryTimingAdvisorTest {

    private lateinit var advisor: EntryTimingAdvisor

    @BeforeEach
    fun setUp() {
        advisor = EntryTimingAdvisor()
    }

    // Helpers per costruire input in modo leggibile nei test
    private fun input(
        trend: TrendClassification,
        rsi: Double?,
        macdDaily: Double?,
        price: Double = 100.0,
        nearestSupport: Double? = null,
    ) = EntryTimingAdvisor.Input(
        trend = trend,
        rsi14 = rsi,
        macdDaily = macdDaily,
        macdWeekly = null,
        currentPrice = price,
        nearestSupport = nearestSupport,
    )

    // price=100, support=96 → distanza 4% → WITHIN_5_PCT
    private fun within5Support() = 96.0

    // price=100, support=90 → distanza 10% → WITHIN_15_PCT
    private fun within15Support() = 90.0

    // price=100, support=80 → distanza 20% → BEYOND_15_PCT
    private fun beyond15Support() = 80.0

    // =========================================================================
    // INDETERMINATE (storico corto)
    // =========================================================================

    @Test
    fun `INDETERMINATE trend returns INDETERMINATE verdict`() {
        val result = advisor.advise(input(TrendClassification.INDETERMINATE, rsi = 50.0, macdDaily = 0.3))
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.INDETERMINATE) },
            { assertThat(result.reentryCondition).isNull() },
            { assertThat(result.rationale.wikiCitations).isNotEmpty() },
        )
    }

    // =========================================================================
    // DOWNTREND — riga 1: Oversold + MACD BULLISH_STRONG + entro 5% → WAIT
    // =========================================================================

    @Test
    fun `DOWNTREND Oversold macd strong bullish within 5pct support returns WAIT with PRICE_ABOVE_SMA200`() {
        // macd > 1.0 → BULLISH_STRONG, rsi < 30 → OVERSOLD, distanza 4% → WITHIN_5_PCT
        val result = advisor.advise(
            input(TrendClassification.DOWNTREND, rsi = 25.0, macdDaily = 1.5, nearestSupport = within5Support()),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.WAIT) },
            { assertThat(result.reentryCondition?.code).isEqualTo(ReentryConditionCode.PRICE_ABOVE_SMA200_WITH_VOLUME) },
            { assertThat(result.rationale.wikiCitations).isNotEmpty() },
        )
    }

    // DOWNTREND — riga 2: qualsiasi altra combinazione → ENTRY_UNFAVORABLE

    @Test
    fun `DOWNTREND neutral rsi flat macd returns ENTRY_UNFAVORABLE`() {
        val result = advisor.advise(
            input(TrendClassification.DOWNTREND, rsi = 50.0, macdDaily = 0.0),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_UNFAVORABLE)
    }

    @Test
    fun `DOWNTREND overbought rsi bearish macd returns ENTRY_UNFAVORABLE`() {
        val result = advisor.advise(
            input(TrendClassification.DOWNTREND, rsi = 75.0, macdDaily = -0.5),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_UNFAVORABLE)
    }

    @Test
    fun `DOWNTREND oversold rsi bearish macd beyond 5pct returns ENTRY_UNFAVORABLE`() {
        // Oversold + MACD non bullish_strong → ENTRY_UNFAVORABLE (prima riga non matcha)
        val result = advisor.advise(
            input(TrendClassification.DOWNTREND, rsi = 25.0, macdDaily = -0.3, nearestSupport = within5Support()),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_UNFAVORABLE)
    }

    // =========================================================================
    // UPTREND — tabella completa
    // =========================================================================

    // Riga: UPTREND + Oversold + MACD bullish + entro 5% → ENTRY_FAVORABLE

    @Test
    fun `UPTREND oversold rsi macd bullish within 5pct returns ENTRY_FAVORABLE`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 25.0, macdDaily = 0.3, nearestSupport = within5Support()),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_FAVORABLE) },
            { assertThat(result.reentryCondition).isNull() },
        )
    }

    // Riga: UPTREND + Neutral + MACD bullish + entro 5% → ENTRY_FAVORABLE

    @Test
    fun `UPTREND neutral rsi macd bullish within 5pct returns ENTRY_FAVORABLE`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 50.0, macdDaily = 0.3, nearestSupport = within5Support()),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_FAVORABLE)
    }

    // Riga: UPTREND + Neutral + MACD bullish + 5..15% → ENTRY_NEUTRAL

    @Test
    fun `UPTREND neutral rsi macd bullish within 15pct returns ENTRY_NEUTRAL`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 50.0, macdDaily = 0.3, nearestSupport = within15Support()),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_NEUTRAL)
    }

    // Riga: UPTREND + Overbought + MACD rialzista forte → ENTRY_NEUTRAL (Murphy §239)

    @Test
    fun `UPTREND overbought rsi macd bullish strong returns ENTRY_NEUTRAL not WAIT`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 75.0, macdDaily = 1.5),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_NEUTRAL)
    }

    // Riga: UPTREND + Overbought + MACD flat/ribassista → WAIT (re-entry RSI < 50)
    // Questo e' il test-anchor stile-COPART (US-099 AC)

    @Test
    fun `UPTREND overbought rsi macd flat returns WAIT with RSI_BELOW_50 — CPRT anchor test`() {
        // CPRT scenario: uptrend, RSI overbought (>70), MACD giornaliero flat
        // → WAIT, re-entry quando RSI torna sotto 50
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 75.0, macdDaily = 0.02),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.WAIT) },
            { assertThat(result.reentryCondition).isNotNull() },
            { assertThat(result.reentryCondition!!.code).isEqualTo(ReentryConditionCode.RSI_BELOW_50) },
            { assertThat(result.reentryCondition!!.description).isNotBlank() },
            { assertThat(result.rationale.wikiCitations).isNotEmpty() },
        )
    }

    @Test
    fun `UPTREND overbought rsi macd bearish returns WAIT with RSI_BELOW_50`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 80.0, macdDaily = -0.3),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.WAIT) },
            { assertThat(result.reentryCondition?.code).isEqualTo(ReentryConditionCode.RSI_BELOW_50) },
        )
    }

    // Riga: UPTREND catch-all (MACD non chiaro/null, support lontano) → ENTRY_NEUTRAL

    @Test
    fun `UPTREND neutral rsi null macd beyond 15pct returns ENTRY_NEUTRAL (catch-all)`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 50.0, macdDaily = null, nearestSupport = beyond15Support()),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_NEUTRAL)
    }

    @Test
    fun `UPTREND any rsi null macd null support returns ENTRY_NEUTRAL (catch-all)`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = null, macdDaily = null),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_NEUTRAL)
    }

    // =========================================================================
    // SIDEWAYS — tabella completa
    // =========================================================================

    // Riga: SIDEWAYS + Overbought (qualsiasi MACD, qualsiasi distanza) → WAIT

    @Test
    fun `SIDEWAYS overbought rsi any macd returns WAIT with RSI_BELOW_50`() {
        val result = advisor.advise(
            input(TrendClassification.SIDEWAYS, rsi = 75.0, macdDaily = 0.5),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.WAIT) },
            { assertThat(result.reentryCondition?.code).isEqualTo(ReentryConditionCode.RSI_BELOW_50) },
        )
    }

    @Test
    fun `SIDEWAYS overbought rsi bearish macd returns WAIT`() {
        val result = advisor.advise(
            input(TrendClassification.SIDEWAYS, rsi = 72.0, macdDaily = -0.3),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.WAIT)
    }

    // Riga: SIDEWAYS + Oversold + MACD bullish + entro 5% → ENTRY_NEUTRAL

    @Test
    fun `SIDEWAYS oversold rsi macd bullish within 5pct returns ENTRY_NEUTRAL`() {
        val result = advisor.advise(
            input(TrendClassification.SIDEWAYS, rsi = 25.0, macdDaily = 0.3, nearestSupport = within5Support()),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_NEUTRAL)
    }

    // Riga: SIDEWAYS catch-all → ENTRY_NEUTRAL

    @Test
    fun `SIDEWAYS neutral rsi flat macd returns ENTRY_NEUTRAL (catch-all)`() {
        val result = advisor.advise(
            input(TrendClassification.SIDEWAYS, rsi = 50.0, macdDaily = 0.01),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_NEUTRAL)
    }

    @Test
    fun `SIDEWAYS oversold rsi bearish macd returns ENTRY_NEUTRAL (sideways catch-all)`() {
        val result = advisor.advise(
            input(TrendClassification.SIDEWAYS, rsi = 25.0, macdDaily = -0.3),
        )
        assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_NEUTRAL)
    }

    // =========================================================================
    // Rationale strutturato (US-099 AC: screen1/2/3 + wikiCitations non vuote)
    // =========================================================================

    @Test
    fun `advise produces non-empty rationale with screen1 screen2 screen3 and wikiCitations for non-INDETERMINATE`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 45.0, macdDaily = 0.3, nearestSupport = within5Support()),
        )
        assertAll(
            { assertThat(result.rationale.screen1).isNotBlank() },
            { assertThat(result.rationale.screen2).isNotBlank() },
            { assertThat(result.rationale.screen3).isNotBlank() },
            { assertThat(result.rationale.wikiCitations).isNotEmpty() },
        )
    }

    // =========================================================================
    // viGate disclaimer (US-099 AC)
    // =========================================================================

    @Test
    fun `advise always exposes viGate disclaimer equal to 'this_advisor_assumes_vi_verdict_positive'`() {
        val result = advisor.advise(
            input(TrendClassification.UPTREND, rsi = 45.0, macdDaily = 0.3),
        )
        assertThat(result.viGate).isEqualTo("this_advisor_assumes_vi_verdict_positive")
    }

    // =========================================================================
    // Helper function unit tests: rsiZone / macdDirection / supportDistance
    // =========================================================================

    @Test
    fun `rsiZone classifies boundaries correctly`() {
        assertAll(
            { assertThat(advisor.rsiZone(null)).isEqualTo(EntryTimingAdvisor.RsiZone.UNKNOWN) },
            { assertThat(advisor.rsiZone(25.0)).isEqualTo(EntryTimingAdvisor.RsiZone.OVERSOLD) },
            { assertThat(advisor.rsiZone(30.0)).isEqualTo(EntryTimingAdvisor.RsiZone.NEUTRAL) }, // strict <30
            { assertThat(advisor.rsiZone(50.0)).isEqualTo(EntryTimingAdvisor.RsiZone.NEUTRAL) },
            { assertThat(advisor.rsiZone(70.0)).isEqualTo(EntryTimingAdvisor.RsiZone.NEUTRAL) }, // strict >70
            { assertThat(advisor.rsiZone(75.0)).isEqualTo(EntryTimingAdvisor.RsiZone.OVERBOUGHT) },
        )
    }

    @Test
    fun `macdDirection classifies thresholds correctly`() {
        assertAll(
            { assertThat(advisor.macdDirection(null)).isEqualTo(EntryTimingAdvisor.MacdDirection.UNKNOWN) },
            { assertThat(advisor.macdDirection(0.01)).isEqualTo(EntryTimingAdvisor.MacdDirection.FLAT) },   // |macd| < 0.05
            { assertThat(advisor.macdDirection(-0.01)).isEqualTo(EntryTimingAdvisor.MacdDirection.FLAT) },
            { assertThat(advisor.macdDirection(0.3)).isEqualTo(EntryTimingAdvisor.MacdDirection.BULLISH) }, // 0.05 < macd ≤ 1.0
            { assertThat(advisor.macdDirection(-0.3)).isEqualTo(EntryTimingAdvisor.MacdDirection.BEARISH) },
            { assertThat(advisor.macdDirection(1.5)).isEqualTo(EntryTimingAdvisor.MacdDirection.BULLISH_STRONG) }, // > 1.0
        )
    }

    @Test
    fun `supportDistance classifies distance buckets correctly`() {
        // price=100
        assertAll(
            { assertThat(advisor.supportDistance(null, 90.0)).isEqualTo(EntryTimingAdvisor.SupportDistance.UNKNOWN) },
            { assertThat(advisor.supportDistance(100.0, null)).isEqualTo(EntryTimingAdvisor.SupportDistance.UNKNOWN) },
            // 4% distanza → WITHIN_5_PCT
            { assertThat(advisor.supportDistance(100.0, 96.0)).isEqualTo(EntryTimingAdvisor.SupportDistance.WITHIN_5_PCT) },
            // 10% distanza → WITHIN_15_PCT
            { assertThat(advisor.supportDistance(100.0, 90.0)).isEqualTo(EntryTimingAdvisor.SupportDistance.WITHIN_15_PCT) },
            // 20% distanza → BEYOND_15_PCT
            { assertThat(advisor.supportDistance(100.0, 80.0)).isEqualTo(EntryTimingAdvisor.SupportDistance.BEYOND_15_PCT) },
        )
    }

    // =========================================================================
    // 4 FIXTURE DI INTEGRAZIONE (US-099 AC / TSK-329)
    // =========================================================================

    // FIXTURE 1: AAPL — uptrend + RSI neutral + MACD bullish rialzista → ENTRY_FAVORABLE

    @Test
    fun `fixture AAPL uptrend neutral rsi bullish macd within 5pct returns ENTRY_FAVORABLE`() {
        // AAPL: price 195, sma50 190, sma200 175 → UPTREND
        // RSI 55 → NEUTRAL, MACD daily 0.8 → BULLISH, support 94% del prezzo → 4% → WITHIN_5_PCT
        val result = advisor.advise(
            EntryTimingAdvisor.Input(
                trend = TrendClassification.UPTREND,
                rsi14 = 55.0,
                macdDaily = 0.8,
                macdWeekly = 1.2,
                currentPrice = 195.0,
                nearestSupport = 187.2, // 4% sotto 195
            ),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_FAVORABLE) },
            { assertThat(result.rationale.screen1).contains("UPTREND") },
            { assertThat(result.rationale.wikiCitations).isNotEmpty() },
        )
    }

    // FIXTURE 2: CPRT stile-COPART — uptrend + RSI overbought + MACD flat → WAIT RSI_BELOW_50
    // TEST-ANCHOR della motivazione dell'epica (TSK-329 AC)

    @Test
    fun `fixture CPRT uptrend overbought rsi macd flat returns WAIT with RSI_BELOW_50 — copart anchor`() {
        // CPRT scenario stile-COPART: trend UPTREND (azienda solida VI),
        // ma RSI > 70 (comprare sul picco) e MACD giornaliero flat.
        // Verdetto atteso: WAIT — non è il momento di entrare.
        val result = advisor.advise(
            EntryTimingAdvisor.Input(
                trend = TrendClassification.UPTREND,
                rsi14 = 76.0,     // overbought (> 70)
                macdDaily = 0.02, // flat (|macd| < 0.05)
                macdWeekly = 0.5,
                currentPrice = 58.0,
                nearestSupport = 52.0,
            ),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.WAIT) },
            { assertThat(result.reentryCondition).isNotNull() },
            { assertThat(result.reentryCondition!!.code).isEqualTo(ReentryConditionCode.RSI_BELOW_50) },
            { assertThat(result.reentryCondition!!.description).isNotBlank() },
            { assertThat(result.rationale.wikiCitations).isNotEmpty() },
            { assertThat(result.viGate).isEqualTo("this_advisor_assumes_vi_verdict_positive") },
        )
    }

    // FIXTURE 3: MSFT — uptrend + RSI oversold → ENTRY_FAVORABLE

    @Test
    fun `fixture MSFT uptrend oversold rsi bullish macd returns ENTRY_FAVORABLE`() {
        val result = advisor.advise(
            EntryTimingAdvisor.Input(
                trend = TrendClassification.UPTREND,
                rsi14 = 27.0,    // oversold
                macdDaily = 0.4, // bullish
                macdWeekly = 0.8,
                currentPrice = 410.0,
                nearestSupport = 396.0, // 3.4% → WITHIN_5_PCT
            ),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_FAVORABLE) },
            { assertThat(result.rationale.screen2).contains("OVERSOLD") },
        )
    }

    // FIXTURE 4: titolo in downtrend → ENTRY_UNFAVORABLE

    @Test
    fun `fixture downtrend ticker returns ENTRY_UNFAVORABLE`() {
        val result = advisor.advise(
            EntryTimingAdvisor.Input(
                trend = TrendClassification.DOWNTREND,
                rsi14 = 40.0,
                macdDaily = -0.5,
                macdWeekly = -1.0,
                currentPrice = 85.0,
                nearestSupport = 80.0,
            ),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.ENTRY_UNFAVORABLE) },
            { assertThat(result.reentryCondition).isNull() },
        )
    }

    // =========================================================================
    // INDETERMINATE su storico corto (US-099 AC)
    // =========================================================================

    @Test
    fun `INDETERMINATE trend produces INDETERMINATE verdict with non-empty wikiCitations`() {
        val result = advisor.advise(
            EntryTimingAdvisor.Input(
                trend = TrendClassification.INDETERMINATE,
                rsi14 = 50.0,
                macdDaily = 0.1,
                macdWeekly = null,
                currentPrice = 25.0,
                nearestSupport = null,
            ),
        )
        assertAll(
            { assertThat(result.verdict).isEqualTo(EntryTimingVerdict.INDETERMINATE) },
            { assertThat(result.reentryCondition).isNull() },
            { assertThat(result.rationale.wikiCitations).isNotEmpty() },
        )
    }
}
