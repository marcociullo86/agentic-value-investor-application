package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// Unit tests for NetNetRatioRule (TSK-318, US-096 AC, EP-023).
//
// Covers TSK-318 §Scenari obbligatori for NET_NET_RATIO:
//   Scenario 1 GREEN:  price < (2/3) × ncavPerShare → ratio < 0.6667.
//   Scenario 2 RED:    price >= (2/3) × ncavPerShare → ratio >= 0.6667.
//   Scenario 3 INDETERMINATE: missing balance (totalCurrentAssets null).
//   Scenario 3 INDETERMINATE: missing shares (both weighted shares null).
//   Scenario 3 INDETERMINATE: priceLatest null.
//   Scenario 4 NOT_CALCULABLE: ncavTotal <= 0 (ncavPerShare <= 0).
//   ruleId assertion.
//   Typed subtype: output is RuleSignal.NetNetRatio.
//   THRESHOLD_RATIO constant is 2.0/3.0.
//
// US-096 Acceptance Criteria (ADR-029 §3):
//   - ncavPerShare not calculable OR priceLatest null → INDETERMINATE
//   - ncavPerShare <= 0 (reason="negative") → NOT_CALCULABLE
//   - ratio < 0.6667 → GREEN
//   - ratio >= 0.6667 → RED
//
// Coerenza con NCAV_LATEST:
//   - NCAV_LATEST INDETERMINATE ⇒ NET_NET_RATIO INDETERMINATE
//   - NCAV_LATEST RED (ncavTotal ≤ 0) ⇒ NET_NET_RATIO NOT_CALCULABLE
//   - NCAV_LATEST GREEN ⇒ NET_NET_RATIO GREEN or RED depending on price
//
// Idiomi: JUnit5 + AssertJ assertAll (same pattern as PbLatestRuleTest/NcavLatestRuleTest).
// Nessuna dipendenza Spring/Mockito — pure unit test.
//
// [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §3]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/TSK-318.md §Scenari]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/US-096.md §AC]
@Suppress("DEPRECATION")
class NetNetRatioRuleTest {

    private val rule = NetNetRatioRule()

    // =========================================================================
    // Scenario 1 — GREEN: ratio < 0.6667
    // US-096 AC: priceLatest < (2/3) × ncavPerShare
    // =========================================================================

    @Test
    fun `GREEN when price is well below two-thirds of ncavPerShare (ratio 0_40)`() {
        // ncavTotal = 1_000_000 - 400_000 = 600_000; shares=100_000 → ncavPerShare=6.0
        // threshold = 6.0 * 2/3 = 4.0; price=2.4 → ratio=0.40 < 0.6667 → GREEN
        val dataset = dataset(
            currentAssets = 1_000_000.0,
            totalLiabilities = 400_000.0,
            sharesDil = 100_000.0,
            price = 2.4,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("NET_NET_RATIO") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ratio).isNotNull()
                assertThat(typed.ratio!!).isCloseTo(0.40, within(1e-6))
                assertThat(typed.ncavPerShare).isCloseTo(6.0, within(1e-6))
                assertThat(typed.priceLatest).isCloseTo(2.4, within(1e-6))
                assertThat(typed.thresholdRatio).isCloseTo(2.0 / 3.0, within(1e-9))
            },
        )
    }

    @Test
    fun `GREEN returns RuleSignal NetNetRatio subtype`() {
        val dataset = dataset(
            currentAssets = 1_000_000.0,
            totalLiabilities = 400_000.0,
            sharesDil = 100_000.0,
            price = 2.0,
        )

        val result = rule.evaluate(dataset)

        assertThat(result).isInstanceOf(RuleSignal.NetNetRatio::class.java)
        assertThat(result.signal).isEqualTo(Signal.GREEN)
    }

    @Test
    fun `GREEN boundary — ratio just below 0_6667 (price exactly two-thirds minus epsilon)`() {
        // ncavPerShare=10.0; threshold_price = 10 * 2/3 = 6.6667
        // price=6.60 → ratio=0.660 < 0.6667 → GREEN
        val dataset = dataset(
            currentAssets = 2_000_000.0,
            totalLiabilities = 1_000_000.0,
            sharesDil = 100_000.0,
            price = 6.60,
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.GREEN)
    }

    @Test
    fun `GREEN observedValue equals ratio (legacy field compat)`() {
        // observedValue must equal ratio for transition window R+1/R+2 (ADR-028 §8)
        val dataset = dataset(
            currentAssets = 1_000_000.0,
            totalLiabilities = 400_000.0,
            sharesDil = 100_000.0,
            price = 2.4,
        )

        val result = rule.evaluate(dataset)
        val typed = result as RuleSignal.NetNetRatio

        // observedValue == ratio (both represent price/ncavPerShare)
        assertThat(result.observedValue!!).isCloseTo(typed.ratio!!, within(1e-9))
    }

    // =========================================================================
    // Scenario 2 — RED: ratio >= 0.6667
    // US-096 AC: priceLatest >= (2/3) × ncavPerShare
    // =========================================================================

    @Test
    fun `RED when price exceeds two-thirds of ncavPerShare (ratio 0_80)`() {
        // ncavPerShare=5.0; price=4.0 → ratio=0.80 >= 0.6667 → RED
        val dataset = dataset(
            currentAssets = 1_500_000.0,
            totalLiabilities = 1_000_000.0,
            sharesDil = 100_000.0,
            price = 4.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ratio!!).isCloseTo(0.80, within(1e-6))
                assertThat(typed.ncavPerShare).isCloseTo(5.0, within(1e-6))
            },
        )
    }

    @Test
    fun `RED when price equals exactly two-thirds of ncavPerShare (boundary inclusive)`() {
        // ncavPerShare=6.0; threshold_price = 6 * 2/3 = 4.0 exactly
        // ratio=4.0/6.0 = 0.6666... = 2/3 → >= threshold → RED
        val dataset = dataset(
            currentAssets = 1_000_000.0,
            totalLiabilities = 400_000.0,
            sharesDil = 100_000.0,
            price = 4.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ratio!!).isCloseTo(2.0 / 3.0, within(1e-9))
            },
        )
    }

    @Test
    fun `RED when price greatly exceeds ncavPerShare (ratio above 1_0)`() {
        // price=15.0, ncavPerShare=5.0 → ratio=3.0 → RED
        val dataset = dataset(
            currentAssets = 1_500_000.0,
            totalLiabilities = 1_000_000.0,
            sharesDil = 100_000.0,
            price = 15.0,
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.RED)
    }

    // =========================================================================
    // Scenario 3 — INDETERMINATE: missing balance data
    // =========================================================================

    @Test
    fun `INDETERMINATE when balance sheet list is empty`() {
        val dataset = makeDataset(
            balance = emptyList(),
            income = listOf(IncomeStatementDto(date = "2024-12-31", weightedAverageShsOutDil = 100_000.0)),
            price = 5.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ratio).isNull()
            },
        )
    }

    @Test
    fun `INDETERMINATE when totalCurrentAssets is null — missing_balance_sheet path`() {
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = null,
                    totalLiabilities = 600_000.0,
                ),
            ),
            income = listOf(
                IncomeStatementDto(date = "2024-12-31", weightedAverageShsOutDil = 100_000.0),
            ),
            price = 5.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ncavPerShare).isNull()
                assertThat(typed.ratio).isNull()
            },
        )
    }

    // =========================================================================
    // Scenario 3 — INDETERMINATE: missing shares
    // =========================================================================

    @Test
    fun `INDETERMINATE when sharesOutstanding not available (missing_shares path)`() {
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = 600_000.0,
                ),
            ),
            income = listOf(
                IncomeStatementDto(
                    date = "2024-12-31",
                    weightedAverageShsOutDil = null,
                    weightedAverageShsOut = null,
                ),
            ),
            price = 5.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ncavPerShare).isNull()
                assertThat(typed.ratio).isNull()
            },
        )
    }

    // =========================================================================
    // Scenario 3 — INDETERMINATE: priceLatest null
    // =========================================================================

    @Test
    fun `INDETERMINATE when priceLatest is null even when NCAV calculable`() {
        // NCAV is fully calculable, but currentPrice is null → INDETERMINATE
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = 400_000.0,
                ),
            ),
            income = listOf(
                IncomeStatementDto(date = "2024-12-31", weightedAverageShsOutDil = 100_000.0),
            ),
            price = null,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ratio).isNull()
                assertThat(typed.priceLatest).isNull()
                // ncavPerShare is present even with price null
                assertThat(typed.ncavPerShare).isNotNull()
                assertThat(typed.ncavPerShare!!).isCloseTo(6.0, within(1e-6))
            },
        )
    }

    // =========================================================================
    // Scenario 4 — NOT_CALCULABLE: ncavTotal <= 0
    //   Distinct from NCAV_LATEST RED — semantics differ: "rule not applicable"
    // =========================================================================

    @Test
    fun `NOT_CALCULABLE when ncavTotal is negative (totalLiabilities exceeds totalCurrentAssets)`() {
        // ncavTotal = 300_000 - 700_000 = -400_000 → reason="negative" → NOT_CALCULABLE
        val dataset = dataset(
            currentAssets = 300_000.0,
            totalLiabilities = 700_000.0,
            sharesDil = 100_000.0,
            price = 5.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ratio).isNull()
                // ncavPerShare is available (≤ 0) even in NOT_CALCULABLE — ratio itself is null
                assertThat(typed.ncavPerShare).isNotNull()
                assertThat(typed.ncavPerShare!!).isLessThanOrEqualTo(0.0)
            },
        )
    }

    @Test
    fun `NOT_CALCULABLE when ncavTotal is exactly zero (boundary)`() {
        val dataset = dataset(
            currentAssets = 500_000.0,
            totalLiabilities = 500_000.0,
            sharesDil = 100_000.0,
            price = 2.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.NetNetRatio
                assertThat(typed.ratio).isNull()
            },
        )
    }

    @Test
    fun `NOT_CALCULABLE is NOT RED — semantic distinction ADR-029 paragraph 3`() {
        // ADR-029 §3: ncavPerShare ≤ 0 → NOT_CALCULABLE (not RED).
        // RED is reserved for ratio >= 0.6667 with valid positive ncavPerShare.
        val dataset = dataset(
            currentAssets = 200_000.0,
            totalLiabilities = 800_000.0,
            sharesDil = 50_000.0,
            price = 10.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.signal).isNotEqualTo(Signal.INDETERMINATE) },
        )
    }

    // =========================================================================
    // THRESHOLD_RATIO constant is 2.0/3.0
    // =========================================================================

    @Test
    fun `thresholdRatio is exactly two-thirds in all typed outputs`() {
        val green = rule.evaluate(dataset(1_000_000.0, 400_000.0, 100_000.0, 2.0)) as RuleSignal.NetNetRatio
        val red = rule.evaluate(dataset(1_000_000.0, 400_000.0, 100_000.0, 5.0)) as RuleSignal.NetNetRatio

        assertAll(
            { assertThat(green.thresholdRatio).isCloseTo(2.0 / 3.0, within(1e-9)) },
            { assertThat(red.thresholdRatio).isCloseTo(2.0 / 3.0, within(1e-9)) },
        )
    }

    // =========================================================================
    // ruleId invariant
    // =========================================================================

    @Test
    fun `ruleId is always NET_NET_RATIO regardless of signal`() {
        val green = rule.evaluate(dataset(1_000_000.0, 400_000.0, 100_000.0, 2.0))
        val red = rule.evaluate(dataset(1_000_000.0, 400_000.0, 100_000.0, 5.0))
        val indeterminate = rule.evaluate(makeDataset(emptyList(), emptyList(), null))
        val notCalculable = rule.evaluate(dataset(200_000.0, 600_000.0, 100_000.0, 5.0))

        assertAll(
            { assertThat(green.ruleId).isEqualTo("NET_NET_RATIO") },
            { assertThat(red.ruleId).isEqualTo("NET_NET_RATIO") },
            { assertThat(indeterminate.ruleId).isEqualTo("NET_NET_RATIO") },
            { assertThat(notCalculable.ruleId).isEqualTo("NET_NET_RATIO") },
        )
    }

    // --- helpers ---

    private fun dataset(
        currentAssets: Double,
        totalLiabilities: Double,
        sharesDil: Double,
        price: Double?,
    ): FinancialDataset = makeDataset(
        balance = listOf(
            BalanceSheetDto(
                date = "2024-12-31",
                calendarYear = "2024",
                totalCurrentAssets = currentAssets,
                totalLiabilities = totalLiabilities,
            ),
        ),
        income = listOf(
            IncomeStatementDto(
                date = "2024-12-31",
                calendarYear = "2024",
                weightedAverageShsOutDil = sharesDil,
            ),
        ),
        price = price,
    )

    private fun makeDataset(
        balance: List<BalanceSheetDto>,
        income: List<IncomeStatementDto>,
        price: Double?,
    ): FinancialDataset = FinancialDataset(
        ticker = "TEST",
        income = income,
        balance = balance,
        cashFlow = emptyList<CashFlowDto>(),
        keyMetrics = emptyList<KeyMetricsDto>(),
        dataSnapshotAt = Instant.parse("2026-06-05T00:00:00Z"),
        currentPrice = price,
    )
}
