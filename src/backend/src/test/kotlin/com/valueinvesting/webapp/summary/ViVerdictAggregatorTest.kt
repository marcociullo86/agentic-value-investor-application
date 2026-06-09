package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit test per ViVerdictAggregator (TSK-341 / US-103 / ADR-030 §3).
//
// Copre la soglia proporzionale ADR-030 §3 verbatim:
//   GREEN_DOMINANT          = quota GREEN ≥ 60% dei decisionali disponibili
//   RED_DOMINANT            = quota GREEN < 33% dei decisionali disponibili
//   YELLOW_DOMINANT         = intervallo intermedio (33% ≤ quota < 60%)
//   INDETERMINATE_DOMINANT  = ≥ 1/3 dei ruleId INDETERMINATE/NOT_CALCULABLE
//
// Esclusioni obbligatorie (ADR-029 §2 + ADR-030 §3):
//   - NCAV_LATEST: informativo, escluso dal denominatore.
//   - INDETERMINATE/NOT_CALCULABLE: esclusi dal denominatore.
//
// Fixture con 14 ruleId decisionali (set corrente EP-023: 13 storici + NET_NET_RATIO;
// NCAV_LATEST escluso come informativo):
//   GREEN_DOMINANT threshold: ≥ 9 GREEN su 14 (≈ 60%)
//   RED_DOMINANT threshold:   < 5 GREEN su 14 (< 33%)
//   YELLOW_DOMINANT:          5-8 GREEN su 14 (33% ≤ quota < 60%)
//
// Pure-function, nessun Spring context, nessuna I/O.
//
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3]
// [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §2]
// [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Definizione delle classi *_DOMINANT"]
class ViVerdictAggregatorTest {

    private lateinit var aggregator: ViVerdictAggregator

    @BeforeEach
    fun setUp() {
        aggregator = ViVerdictAggregator()
    }

    // =========================================================================
    // Helper: costruisce una lista di RuleSignal con i 13 ruleId decisionali
    // storici + NET_NET_RATIO (14 totali) + NCAV_LATEST (informativo, escluso).
    // =========================================================================

    /**
     * Costruisce [nGreen] segnali GREEN + [nRed] segnali RED tra i ruleId
     * DECISIONALI (13 storici + NET_NET_RATIO), più NCAV_LATEST (informativo).
     * [nIndeterminate] aggiunge segnali INDETERMINATE su ruleId decisionali.
     * Il totale è nGreen + nRed + nIndeterminate ruleId decisionali + 1 NCAV_LATEST.
     */
    private fun buildSignals(
        nGreen: Int,
        nRed: Int = 0,
        nIndeterminate: Int = 0,
        includeNcavLatest: Boolean = true,
    ): List<RuleSignal> {
        // 14 ruleId decisionali disponibili
        val decisionalRuleIds = listOf(
            "SIZE_LATEST",
            "EARNINGS_STABILITY_10Y",
            "EPS_GROWTH_10Y",
            "PE_3Y_AVG",
            "PB_LATEST",
            "DIVIDEND_CONTINUITY_20Y",
            "ROE_10Y_AVG",
            "ROIC_10Y_AVG",
            "GROSS_MARGIN_10Y_AVG",
            "NET_MARGIN_10Y_AVG",
            "CURRENT_RATIO_LATEST",
            "DEBT_TO_INCOME_LATEST",
            "CAPEX_INTENSITY_10Y_AVG",
            "NET_NET_RATIO", // EP-023 — decisionale
        )
        require(nGreen + nRed + nIndeterminate <= decisionalRuleIds.size) {
            "Troppi segnali richiesti: ${nGreen + nRed + nIndeterminate} > ${decisionalRuleIds.size}"
        }
        val signals = mutableListOf<RuleSignal>()
        var idx = 0
        // Aggiungi GREEN
        repeat(nGreen) {
            signals += ruleSignalForId(decisionalRuleIds[idx++], Signal.GREEN)
        }
        // Aggiungi RED
        repeat(nRed) {
            signals += ruleSignalForId(decisionalRuleIds[idx++], Signal.RED)
        }
        // Aggiungi INDETERMINATE
        repeat(nIndeterminate) {
            signals += ruleSignalForId(decisionalRuleIds[idx++], Signal.INDETERMINATE)
        }
        // Aggiungi NCAV_LATEST informativo (deve essere escluso dal denominatore)
        if (includeNcavLatest) {
            signals += RuleSignal.NcavLatest(signal = Signal.GREEN)
        }
        return signals
    }

    /** Costruisce un RuleSignal minimo per il ruleId dato. */
    private fun ruleSignalForId(ruleId: String, signal: Signal): RuleSignal = when (ruleId) {
        "SIZE_LATEST" -> RuleSignal.Size(signal = signal)
        "EARNINGS_STABILITY_10Y" -> RuleSignal.EarningsStability10y(signal = signal)
        "EPS_GROWTH_10Y" -> RuleSignal.EpsGrowth10y(signal = signal)
        "PE_3Y_AVG" -> RuleSignal.Pe3yAvg(signal = signal)
        "PB_LATEST" -> RuleSignal.PbLatest(signal = signal)
        "DIVIDEND_CONTINUITY_20Y" -> RuleSignal.DividendContinuity20y(signal = signal)
        "ROE_10Y_AVG" -> RuleSignal.Roe10yAvg(signal = signal)
        "ROIC_10Y_AVG" -> RuleSignal.Roic10yAvg(signal = signal)
        "GROSS_MARGIN_10Y_AVG" -> RuleSignal.GrossMargin10yAvg(signal = signal)
        "NET_MARGIN_10Y_AVG" -> RuleSignal.NetMargin10yAvg(signal = signal)
        "CURRENT_RATIO_LATEST" -> RuleSignal.CurrentRatioLatest(signal = signal)
        "DEBT_TO_INCOME_LATEST" -> RuleSignal.DebtToIncomeLatest(signal = signal)
        "CAPEX_INTENSITY_10Y_AVG" -> RuleSignal.CapexIntensity10yAvg(signal = signal)
        "NET_NET_RATIO" -> RuleSignal.NetNetRatio(signal = signal)
        "NCAV_LATEST" -> RuleSignal.NcavLatest(signal = signal)
        else -> error("Unknown ruleId: $ruleId")
    }

    // =========================================================================
    // GREEN_DOMINANT — quota GREEN ≥ 60%
    // =========================================================================

    @Test
    fun `9 of 14 GREEN (64 pct) produces GREEN_DOMINANT — at threshold boundary`() {
        // 9/14 ≈ 64.3% ≥ 60% → GREEN_DOMINANT
        val result = aggregator.aggregate(buildSignals(nGreen = 9, nRed = 5))
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.GREEN_DOMINANT) },
            { assertThat(result.greenCount).isEqualTo(9) },
            { assertThat(result.decisionalAvailable).isEqualTo(14) },
            { assertThat(result.greenShare!!).isGreaterThanOrEqualTo(0.60) },
        )
    }

    @Test
    fun `14 of 14 GREEN produces GREEN_DOMINANT — all green`() {
        val result = aggregator.aggregate(buildSignals(nGreen = 14, nRed = 0))
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.GREEN_DOMINANT) },
            { assertThat(result.greenCount).isEqualTo(14) },
            { assertThat(result.greenShare!!).isEqualTo(1.0) },
        )
    }

    @Test
    fun `10 of 14 GREEN produces GREEN_DOMINANT`() {
        val result = aggregator.aggregate(buildSignals(nGreen = 10, nRed = 4))
        assertThat(result.verdict).isEqualTo(ViVerdict.GREEN_DOMINANT)
    }

    // =========================================================================
    // RED_DOMINANT — quota GREEN < 33%
    // =========================================================================

    @Test
    fun `4 of 14 GREEN (28 pct) produces RED_DOMINANT — below threshold`() {
        // 4/14 ≈ 28.6% < 33% → RED_DOMINANT
        val result = aggregator.aggregate(buildSignals(nGreen = 4, nRed = 10))
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.RED_DOMINANT) },
            { assertThat(result.greenCount).isEqualTo(4) },
            { assertThat(result.greenShare!!).isLessThan(0.33) },
        )
    }

    @Test
    fun `0 of 14 GREEN produces RED_DOMINANT`() {
        val result = aggregator.aggregate(buildSignals(nGreen = 0, nRed = 14))
        assertThat(result.verdict).isEqualTo(ViVerdict.RED_DOMINANT)
    }

    @Test
    fun `1 of 14 GREEN produces RED_DOMINANT`() {
        val result = aggregator.aggregate(buildSignals(nGreen = 1, nRed = 13))
        assertThat(result.verdict).isEqualTo(ViVerdict.RED_DOMINANT)
    }

    // =========================================================================
    // YELLOW_DOMINANT — intervallo intermedio (33% ≤ quota < 60%)
    // =========================================================================

    @Test
    fun `5 of 14 GREEN (35 pct) produces YELLOW_DOMINANT — just above RED threshold`() {
        // 5/14 ≈ 35.7% → 33% ≤ quota < 60% → YELLOW_DOMINANT
        val result = aggregator.aggregate(buildSignals(nGreen = 5, nRed = 9))
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.YELLOW_DOMINANT) },
            { assertThat(result.greenShare!!)
                .isGreaterThanOrEqualTo(0.33)
                .isLessThan(0.60) },
        )
    }

    @Test
    fun `8 of 14 GREEN (57 pct) produces YELLOW_DOMINANT — just below GREEN threshold`() {
        // 8/14 ≈ 57.1% < 60% → YELLOW_DOMINANT
        val result = aggregator.aggregate(buildSignals(nGreen = 8, nRed = 6))
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.YELLOW_DOMINANT) },
            { assertThat(result.greenShare!!).isLessThan(0.60) },
        )
    }

    // =========================================================================
    // INDETERMINATE_DOMINANT — ≥ 1/3 dei ruleId INDETERMINATE/NOT_CALCULABLE
    // =========================================================================

    @Test
    fun `5 of 15 total ruleId INDETERMINATE (15 total = 14 decisional + NCAV_LATEST) produces INDETERMINATE_DOMINANT`() {
        // 15 totali (14 decisionali + NCAV_LATEST): soglia = ceil(15/3) = 5
        // 5 INDETERMINATE ≥ 5 → INDETERMINATE_DOMINANT
        val signals = buildSignals(nGreen = 5, nRed = 4, nIndeterminate = 5)
        val result = aggregator.aggregate(signals)
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.INDETERMINATE_DOMINANT) },
            { assertThat(result.indeterminateCount).isEqualTo(5) },
        )
    }

    @Test
    fun `4 of 15 ruleId INDETERMINATE is below threshold and does NOT produce INDETERMINATE_DOMINANT`() {
        // 4 < ceil(15/3) = 5 → non è INDETERMINATE_DOMINANT
        val signals = buildSignals(nGreen = 7, nRed = 3, nIndeterminate = 4)
        val result = aggregator.aggregate(signals)
        assertThat(result.verdict).isNotEqualTo(ViVerdict.INDETERMINATE_DOMINANT)
    }

    @Test
    fun `empty signal list produces INDETERMINATE_DOMINANT`() {
        val result = aggregator.aggregate(emptyList())
        assertThat(result.verdict).isEqualTo(ViVerdict.INDETERMINATE_DOMINANT)
    }

    // =========================================================================
    // NCAV_LATEST esclusione — ADR-029 §2: mai nel denominatore
    // =========================================================================

    @Test
    fun `NCAV_LATEST GREEN is excluded from denominator — does not inflate GREEN count`() {
        // 9 GREEN decisionali + NCAV_LATEST GREEN → denominator = 14 (non 15),
        // verde = 9/14 ≈ 64.3% → GREEN_DOMINANT.
        // Se NCAV_LATEST fosse incluso: 10/15 ≈ 66.7% comunque GREEN_DOMINANT —
        // ma verifichiamo che il denominatore è 14 non 15.
        val signals = buildSignals(nGreen = 9, nRed = 5, includeNcavLatest = true)
        val result = aggregator.aggregate(signals)
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.GREEN_DOMINANT) },
            { assertThat(result.decisionalAvailable).isEqualTo(14) }, // NCAV_LATEST escluso
            { assertThat(result.greenCount).isEqualTo(9) },
        )
    }

    @Test
    fun `NCAV_LATEST RED does not push towards RED_DOMINANT — excluded from denominator`() {
        // 9 GREEN decisionali + NCAV_LATEST RED → verdetto deve restare GREEN_DOMINANT
        // perché NCAV_LATEST non entra nel denominatore.
        val signals = mutableListOf<RuleSignal>()
        // 9 GREEN decisionali
        val greenIds = listOf(
            "SIZE_LATEST", "EARNINGS_STABILITY_10Y", "EPS_GROWTH_10Y", "PE_3Y_AVG", "PB_LATEST",
            "DIVIDEND_CONTINUITY_20Y", "ROE_10Y_AVG", "ROIC_10Y_AVG", "GROSS_MARGIN_10Y_AVG",
        )
        greenIds.forEach { signals += ruleSignalForId(it, Signal.GREEN) }
        // 5 RED decisionali
        val redIds = listOf(
            "NET_MARGIN_10Y_AVG", "CURRENT_RATIO_LATEST", "DEBT_TO_INCOME_LATEST",
            "CAPEX_INTENSITY_10Y_AVG", "NET_NET_RATIO",
        )
        redIds.forEach { signals += ruleSignalForId(it, Signal.RED) }
        // NCAV_LATEST RED (informativo — deve essere escluso)
        signals += RuleSignal.NcavLatest(signal = Signal.RED)

        val result = aggregator.aggregate(signals)
        assertAll(
            { assertThat(result.verdict).isEqualTo(ViVerdict.GREEN_DOMINANT) },
            { assertThat(result.decisionalAvailable).isEqualTo(14) }, // NCAV_LATEST escluso
            { assertThat(result.totalRuleIds).isEqualTo(15) }, // conteggio totale include NCAV_LATEST
        )
    }

    @Test
    fun `signals without NCAV_LATEST produce same verdict as with NCAV_LATEST GREEN — denominator identical`() {
        // Verifica esplicita che la presenza/assenza di NCAV_LATEST non cambia il verdetto.
        val withNcav = buildSignals(nGreen = 9, nRed = 5, includeNcavLatest = true)
        val withoutNcav = buildSignals(nGreen = 9, nRed = 5, includeNcavLatest = false)

        val r1 = aggregator.aggregate(withNcav)
        val r2 = aggregator.aggregate(withoutNcav)

        assertAll(
            { assertThat(r1.verdict).isEqualTo(r2.verdict) },
            { assertThat(r1.greenCount).isEqualTo(r2.greenCount) },
            // Il denominatore è lo stesso perché NCAV_LATEST è sempre escluso.
            { assertThat(r1.decisionalAvailable).isEqualTo(r2.decisionalAvailable) },
        )
    }

    // =========================================================================
    // Result fields — validazione struttura
    // =========================================================================

    @Test
    fun `aggregate result exposes correct totalRuleIds greenCount decisionalAvailable and greenShare`() {
        // 10 GREEN + 4 RED + NCAV_LATEST GREEN = 15 totali
        val signals = buildSignals(nGreen = 10, nRed = 4, includeNcavLatest = true)
        val result = aggregator.aggregate(signals)

        assertAll(
            { assertThat(result.totalRuleIds).isEqualTo(15) },
            { assertThat(result.greenCount).isEqualTo(10) },
            { assertThat(result.decisionalAvailable).isEqualTo(14) },
            { assertThat(result.greenShare).isNotNull },
            { assertThat(result.greenShare!!).isCloseTo(10.0 / 14.0, org.assertj.core.data.Offset.offset(0.001)) },
        )
    }
}
