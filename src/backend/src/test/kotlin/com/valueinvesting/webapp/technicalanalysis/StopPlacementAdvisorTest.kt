package com.valueinvesting.webapp.technicalanalysis

import com.valueinvesting.webapp.api.model.PositionSizingWarning
import com.valueinvesting.webapp.api.model.RewardRiskLabel
import com.valueinvesting.webapp.api.model.StopType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit test per StopPlacementAdvisor (TSK-331 / US-100).
//
// Copre i ≥6 scenari richiesti da US-100 §AC:
//   1) SUPPORT_BASED normale
//   2) SUPPORT_BASED con buffer 0.5% verificato
//   3) SMA200_BASED (no support utile, prezzo > SMA200)
//   4) ATR_BASED (no support, prezzo < SMA200, ATR disponibile)
//   5) NOT_CALCULABLE (nessun ancoraggio disponibile)
//   6) POSITION_EXCEEDS_EQUITY (stop molto stretto → size teorica > equity)
//
// Plus: asserzioni su rewardRiskRatio (etichette EXCELLENT/ACCEPTABLE/MARGINAL/
// UNFAVORABLE/NOT_APPLICABLE) e su sixPercentRule.maxAggregateRiskPerMonth.
//
// Test assenza persistenza equity: verifica che StopPlacementAdvisor sia una
// pure-function senza stato interno (nessun campo mutable, nessuna scrittura DB).
//
// Nessun LLM, nessun FmpAdapter — pure-function Kotlin.
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-100-stop-placement-position-sizing-be/TSK-331.md]
// [^src: wiki/syntheses/ta-stop-placement-position-sizing.md] (Murphy §Page 82, Elder §50/§51/§54)
// [^src: wiki/concepts/elder-risk-management-2pct-6pct.md]
class StopPlacementAdvisorTest {

    private lateinit var advisor: StopPlacementAdvisor

    @BeforeEach
    fun setUp() {
        advisor = StopPlacementAdvisor()
    }

    // =========================================================================
    // Scenario 1: SUPPORT_BASED normale
    // =========================================================================

    @Test
    fun `scenario 1 SUPPORT_BASED stop when nearest support is below current price`() {
        // price=100, support=95 → stop = 95 * (1 - 0.005) = 94.525
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 100.0,
            nearestSupport = 95.0,
            nearestSupportLabel = "SWING_LOW",
            sma200 = 80.0,
            atr14 = 2.0,
        )
        val result = advisor.suggestStop(input)

        assertAll(
            { assertThat(result.type).isEqualTo(StopType.SUPPORT_BASED) },
            { assertThat(result.stopPrice).isNotNull() },
            { assertThat(result.stopPrice!!).isCloseTo(94.525, Offset.offset(0.001)) },
            { assertThat(result.stopDistance).isNotNull() },
            { assertThat(result.stopDistancePct).isNotNull() },
            { assertThat(result.anchorReference).contains("support@95.00") },
            { assertThat(result.anchorReference).contains("SWING_LOW") },
            { assertThat(result.rationale).isNotBlank() },
        )
    }

    // =========================================================================
    // Scenario 2: SUPPORT_BASED con buffer 0.5% verificato
    // =========================================================================

    @Test
    fun `scenario 2 SUPPORT_BASED buffer is exactly 0 5 pct below support level`() {
        val support = 47.5
        val expectedStop = support * (1.0 - 0.005) // 47.5 * 0.995 = 47.2625
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 48.0,
            nearestSupport = support,
            nearestSupportLabel = "RETRACEMENT_50",
            sma200 = 40.0,
            atr14 = 0.5,
        )
        val result = advisor.suggestStop(input)

        assertAll(
            { assertThat(result.type).isEqualTo(StopType.SUPPORT_BASED) },
            { assertThat(result.stopPrice!!).isCloseTo(expectedStop, Offset.offset(0.0001)) },
            // stopDistance = 48.0 - 47.2625 = 0.7375
            { assertThat(result.stopDistance!!).isCloseTo(48.0 - expectedStop, Offset.offset(0.0001)) },
            // stopDistancePct = stopDistance / currentPrice * 100
            { assertThat(result.stopDistancePct!!).isCloseTo((48.0 - expectedStop) / 48.0 * 100.0, Offset.offset(0.01)) },
        )
    }

    // =========================================================================
    // Scenario 3: SMA200_BASED (no support utile, prezzo > SMA200)
    // =========================================================================

    @Test
    fun `scenario 3 SMA200_BASED when no support and price above SMA200`() {
        // Nessun support (null), price=100 > sma200=90 → stop = 90 * 0.995 = 89.55
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 100.0,
            nearestSupport = null,
            nearestSupportLabel = null,
            sma200 = 90.0,
            atr14 = 2.0,
        )
        val result = advisor.suggestStop(input)

        assertAll(
            { assertThat(result.type).isEqualTo(StopType.SMA200_BASED) },
            { assertThat(result.stopPrice!!).isCloseTo(89.55, Offset.offset(0.001)) },
            { assertThat(result.anchorReference).contains("SMA200@90.00") },
            { assertThat(result.rationale).contains("SMA200") },
        )
    }

    @Test
    fun `scenario 3 SMA200_BASED not used when price is below SMA200`() {
        // price < sma200 → SMA200_BASED non applicabile → fallback ad ATR
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 80.0,
            nearestSupport = null,
            nearestSupportLabel = null,
            sma200 = 90.0, // price(80) < sma200(90)
            atr14 = 2.0,
        )
        val result = advisor.suggestStop(input)

        assertThat(result.type).isEqualTo(StopType.ATR_BASED)
    }

    // =========================================================================
    // Scenario 4: ATR_BASED (no support, prezzo < SMA200, ATR disponibile)
    // =========================================================================

    @Test
    fun `scenario 4 ATR_BASED when no support and price below SMA200`() {
        // price=80, sma200=90 (price < sma200 → SMA200_BASED non applicabile)
        // atr14=2.5 → stop = 80 - 2*2.5 = 75.0
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 80.0,
            nearestSupport = null,
            nearestSupportLabel = null,
            sma200 = 90.0,
            atr14 = 2.5,
        )
        val result = advisor.suggestStop(input)

        assertAll(
            { assertThat(result.type).isEqualTo(StopType.ATR_BASED) },
            { assertThat(result.stopPrice!!).isCloseTo(75.0, Offset.offset(0.001)) },
            { assertThat(result.stopDistance!!).isCloseTo(5.0, Offset.offset(0.001)) },
            { assertThat(result.anchorReference).contains("ATR14=2.50") },
        )
    }

    // =========================================================================
    // Scenario 5: NOT_CALCULABLE
    // =========================================================================

    @Test
    fun `scenario 5 NOT_CALCULABLE when no support no SMA200 applicable and no ATR`() {
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 80.0,
            nearestSupport = null,
            nearestSupportLabel = null,
            sma200 = 90.0,  // price < sma200 → SMA200 non applicabile
            atr14 = null,   // nessun ATR
        )
        val result = advisor.suggestStop(input)

        assertAll(
            { assertThat(result.type).isEqualTo(StopType.NOT_CALCULABLE) },
            { assertThat(result.stopPrice).isNull() },
            { assertThat(result.stopDistance).isNull() },
            { assertThat(result.stopDistancePct).isNull() },
            { assertThat(result.anchorReference).isNull() },
            { assertThat(result.rationale).isNotBlank() },
        )
    }

    @Test
    fun `scenario 5 NOT_CALCULABLE when no support no sma200 and no atr`() {
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 80.0,
            nearestSupport = null,
            nearestSupportLabel = null,
            sma200 = null,
            atr14 = null,
        )
        val result = advisor.suggestStop(input)

        assertThat(result.type).isEqualTo(StopType.NOT_CALCULABLE)
    }

    // =========================================================================
    // Scenario 6: POSITION_EXCEEDS_EQUITY
    // =========================================================================

    @Test
    fun `scenario 6 POSITION_EXCEEDS_EQUITY flagged when stop very tight`() {
        // equity = 1000, maxRisk = 20 (2%)
        // stopDistance = 0.001 (molto stretto) → shares = floor(20/0.001) = 20000
        // maxSharesByEquity = floor(1000/100) = 10
        // → warning = POSITION_EXCEEDS_EQUITY, shares = 10 (capped)
        val stopSuggestion = advisor.suggestStop(
            StopPlacementAdvisor.StopInput(
                currentPrice = 100.0,
                nearestSupport = 99.9, // stop = 99.9 * 0.995 ≈ 99.4005, distance ≈ 0.5995
                nearestSupportLabel = "SWING_LOW",
                sma200 = null,
                atr14 = null,
            ),
        )
        // Usiamo un equity molto piccolo per triggerare POSITION_EXCEEDS_EQUITY
        // con stopDistance moderata: equity=100, currentPrice=100
        // maxRisk = 2, shares = floor(2/stopDistance)
        // Con support a 99.9 e buffer 0.5%: stop ≈ 99.4, distance ≈ 0.6
        // shares = floor(2/0.6) = 3, positionValue = 300 > 100 (equity) → EXCEEDS
        val sizing = advisor.computePositionSizing(
            equity = 100.0,
            currentPrice = 100.0,
            stopSuggestion = stopSuggestion,
        )

        assertAll(
            { assertThat(sizing.twoPercentRule.warning).isEqualTo(PositionSizingWarning.POSITION_EXCEEDS_EQUITY) },
            // Quando capped: shares = floor(equity / currentPrice) = 1
            { assertThat(sizing.twoPercentRule.sharesRecommended).isEqualTo(1L) },
            { assertThat(sizing.twoPercentRule.positionValueRecommended).isCloseTo(100.0, Offset.offset(0.01)) },
        )
    }

    // =========================================================================
    // Position sizing 2% Rule — formula Elder §50
    // =========================================================================

    @Test
    fun `computePositionSizing applies 2pct rule correctly`() {
        // equity=50000, stop distance=5.0
        // maxRisk = 1000, shares = floor(1000/5) = 200
        // positionValue = 200 * 100.0 = 20000
        val stopSuggestion = advisor.suggestStop(
            StopPlacementAdvisor.StopInput(
                currentPrice = 100.0,
                nearestSupport = null,
                nearestSupportLabel = null,
                sma200 = 95.0, // stop = 95 * 0.995 = 94.525, distance = 5.475
                atr14 = null,
            ),
        )
        val sizing = advisor.computePositionSizing(
            equity = 50_000.0,
            currentPrice = 100.0,
            stopSuggestion = stopSuggestion,
        )

        assertAll(
            { assertThat(sizing.twoPercentRule.equity).isEqualTo(50_000.0) },
            { assertThat(sizing.twoPercentRule.maxRiskAllowed).isCloseTo(1_000.0, Offset.offset(0.01)) },
            { assertThat(sizing.twoPercentRule.sharesRecommended).isGreaterThan(0L) },
            { assertThat(sizing.twoPercentRule.positionValueRecommended).isGreaterThan(0.0) },
            { assertThat(sizing.twoPercentRule.warning).isNull() },
        )
    }

    // =========================================================================
    // 6% Rule (US-100 AC)
    // =========================================================================

    @Test
    fun `computePositionSizing sixPercentRule maxAggregateRiskPerMonth is 6pct of equity`() {
        val stopSuggestion = advisor.suggestStop(
            StopPlacementAdvisor.StopInput(
                currentPrice = 100.0,
                nearestSupport = null,
                nearestSupportLabel = null,
                sma200 = 90.0,
                atr14 = null,
            ),
        )
        val sizing = advisor.computePositionSizing(50_000.0, 100.0, stopSuggestion)

        assertAll(
            // 6% Rule: maxAggregateRiskPerMonth = equity * 0.06
            { assertThat(sizing.sixPercentRule.maxAggregateRiskPerMonth).isCloseTo(3_000.0, Offset.offset(0.01)) },
            { assertThat(sizing.sixPercentRule.disclaimer).isNotBlank() },
        )
    }

    // =========================================================================
    // rewardRiskRatio — etichette (US-100 AC)
    // =========================================================================

    @Test
    fun `computeRewardRisk returns EXCELLENT label when ratio ge 3`() {
        // upside = 60, downside = 10 → ratio = 6.0 ≥ 3.0
        val stop = stopWithDistance(10.0)
        val result = advisor.computeRewardRisk(currentPrice = 100.0, dcfIntrinsicValue = 160.0, stopSuggestion = stop)

        assertAll(
            { assertThat(result.label).isEqualTo(RewardRiskLabel.EXCELLENT) },
            { assertThat(result.upside!!).isCloseTo(60.0, Offset.offset(0.01)) },
            { assertThat(result.downside!!).isCloseTo(10.0, Offset.offset(0.01)) },
            { assertThat(result.value!!).isCloseTo(6.0, Offset.offset(0.01)) },
            { assertThat(result.rationale).contains("Eccellente") },
        )
    }

    @Test
    fun `computeRewardRisk returns ACCEPTABLE label when ratio ge 2 and lt 3`() {
        // upside = 50, downside = 20 → ratio = 2.5
        val stop = stopWithDistance(20.0)
        val result = advisor.computeRewardRisk(currentPrice = 100.0, dcfIntrinsicValue = 150.0, stopSuggestion = stop)

        assertAll(
            { assertThat(result.label).isEqualTo(RewardRiskLabel.ACCEPTABLE) },
            { assertThat(result.rationale).contains("Accettabile") },
        )
    }

    @Test
    fun `computeRewardRisk returns MARGINAL label when ratio ge 1 and lt 2`() {
        // upside = 15, downside = 10 → ratio = 1.5
        val stop = stopWithDistance(10.0)
        val result = advisor.computeRewardRisk(currentPrice = 100.0, dcfIntrinsicValue = 115.0, stopSuggestion = stop)

        assertAll(
            { assertThat(result.label).isEqualTo(RewardRiskLabel.MARGINAL) },
            { assertThat(result.rationale).contains("Marginale") },
        )
    }

    @Test
    fun `computeRewardRisk returns UNFAVORABLE label when ratio lt 1`() {
        // upside = 5, downside = 10 → ratio = 0.5
        val stop = stopWithDistance(10.0)
        val result = advisor.computeRewardRisk(currentPrice = 100.0, dcfIntrinsicValue = 105.0, stopSuggestion = stop)

        assertAll(
            { assertThat(result.label).isEqualTo(RewardRiskLabel.UNFAVORABLE) },
            { assertThat(result.rationale).contains("Sfavorevole") },
        )
    }

    @Test
    fun `computeRewardRisk returns NOT_APPLICABLE when dcfIntrinsicValue is null`() {
        val stop = stopWithDistance(5.0)
        val result = advisor.computeRewardRisk(currentPrice = 100.0, dcfIntrinsicValue = null, stopSuggestion = stop)

        assertAll(
            { assertThat(result.label).isEqualTo(RewardRiskLabel.NOT_APPLICABLE) },
            { assertThat(result.upside).isNull() },
            { assertThat(result.value).isNull() },
            { assertThat(result.rationale).isNotBlank() },
        )
    }

    @Test
    fun `computeRewardRisk returns NOT_APPLICABLE when dcfIntrinsicValue le currentPrice`() {
        // DCF <= prezzo corrente: nessun upside fondamentale
        val stop = stopWithDistance(5.0)
        val result = advisor.computeRewardRisk(currentPrice = 100.0, dcfIntrinsicValue = 100.0, stopSuggestion = stop)

        assertThat(result.label).isEqualTo(RewardRiskLabel.NOT_APPLICABLE)
    }

    @Test
    fun `computeRewardRisk returns NOT_APPLICABLE when stop is NOT_CALCULABLE`() {
        val notCalculable = advisor.suggestStop(
            StopPlacementAdvisor.StopInput(
                currentPrice = 80.0,
                nearestSupport = null,
                nearestSupportLabel = null,
                sma200 = 90.0,
                atr14 = null,
            ),
        )
        assertThat(notCalculable.type).isEqualTo(StopType.NOT_CALCULABLE)

        val result = advisor.computeRewardRisk(currentPrice = 80.0, dcfIntrinsicValue = 120.0, stopSuggestion = notCalculable)

        assertThat(result.label).isEqualTo(RewardRiskLabel.NOT_APPLICABLE)
    }

    // =========================================================================
    // Test assenza persistenza equity (US-100 AC)
    //
    // StopPlacementAdvisor e' un @Component Spring senza campi mutable. Verifichiamo
    // che non esista alcun field persistente: invocare computePositionSizing due volte
    // con equity diverse produce risultati indipendenti (no side effect / state
    // conditioning). Se StopPlacementAdvisor avesse salvato equity internamente, la
    // seconda invocazione produrrebbe un risultato contaminato dalla prima.
    // =========================================================================

    @Test
    fun `equity is not persisted between calls — two independent invocations produce independent results`() {
        val stop = stopWithDistance(5.0)

        val first = advisor.computePositionSizing(
            equity = 50_000.0,
            currentPrice = 100.0,
            stopSuggestion = stop,
        )
        val second = advisor.computePositionSizing(
            equity = 100_000.0,
            currentPrice = 100.0,
            stopSuggestion = stop,
        )

        assertAll(
            // Le due chiamate non devono condividere stato: equity diverse → risultati diversi
            { assertThat(first.twoPercentRule.equity).isEqualTo(50_000.0) },
            { assertThat(second.twoPercentRule.equity).isEqualTo(100_000.0) },
            { assertThat(first.twoPercentRule.maxRiskAllowed).isCloseTo(1_000.0, Offset.offset(0.01)) },
            { assertThat(second.twoPercentRule.maxRiskAllowed).isCloseTo(2_000.0, Offset.offset(0.01)) },
            // L'equity della prima chiamata NON contamina la seconda
            { assertThat(second.twoPercentRule.equity).isNotEqualTo(first.twoPercentRule.equity) },
            { assertThat(second.sixPercentRule.maxAggregateRiskPerMonth).isCloseTo(6_000.0, Offset.offset(0.01)) },
            { assertThat(first.sixPercentRule.maxAggregateRiskPerMonth).isCloseTo(3_000.0, Offset.offset(0.01)) },
        )
    }

    @Test
    fun `StopPlacementAdvisor has no mutable instance state — pure function class`() {
        // Verifica strutturale: StopPlacementAdvisor non deve dichiarare field
        // mutable (var) fuori dai companion object / metodi.
        // Approccio: reflection sui declared fields dell'istanza.
        val fields = StopPlacementAdvisor::class.java.declaredFields
        val mutableInstanceFields = fields.filter { field ->
            // Escludiamo i campi synthetics (Kotlin companion, logger injectato da Spring)
            !field.name.contains("$") &&
                !field.name.startsWith("log") &&
                !field.name.startsWith("INSTANCE") &&
                // Verifica se il campo è final (immutable)
                !java.lang.reflect.Modifier.isFinal(field.modifiers) &&
                !java.lang.reflect.Modifier.isStatic(field.modifiers)
        }
        assertThat(mutableInstanceFields)
            .withFailMessage {
                "StopPlacementAdvisor contiene campi mutable (equity potrebbe essere persistita!): " +
                    mutableInstanceFields.map { it.name }
            }
            .isEmpty()
    }

    // =========================================================================
    // Priority ordering: SUPPORT_BASED ha priorità su SMA200_BASED su ATR_BASED
    // =========================================================================

    @Test
    fun `SUPPORT_BASED takes priority over SMA200_BASED and ATR_BASED when all available`() {
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 100.0,
            nearestSupport = 95.0,
            nearestSupportLabel = "SWING_LOW",
            sma200 = 90.0,
            atr14 = 2.0,
        )
        val result = advisor.suggestStop(input)
        assertThat(result.type).isEqualTo(StopType.SUPPORT_BASED)
    }

    @Test
    fun `SMA200_BASED takes priority over ATR_BASED when no support`() {
        val input = StopPlacementAdvisor.StopInput(
            currentPrice = 100.0,
            nearestSupport = null,
            nearestSupportLabel = null,
            sma200 = 90.0,
            atr14 = 2.0,
        )
        val result = advisor.suggestStop(input)
        assertThat(result.type).isEqualTo(StopType.SMA200_BASED)
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Crea uno StopSuggestion con una distanza nota senza passare per suggestStop().
     * Usato nei test rewardRiskRatio dove la logica testata e' il calcolo del ratio,
     * non il calcolo dello stop.
     */
    private fun stopWithDistance(distance: Double) = com.valueinvesting.webapp.api.model.StopSuggestion(
        type = StopType.SUPPORT_BASED,
        stopPrice = 100.0 - distance,
        stopDistance = distance,
        stopDistancePct = distance,
        anchorReference = "support@${100.0 - distance}",
        rationale = "test stop",
    )
}
