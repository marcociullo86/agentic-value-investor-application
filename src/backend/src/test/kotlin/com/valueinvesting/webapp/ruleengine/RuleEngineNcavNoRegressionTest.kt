package com.valueinvesting.webapp.ruleengine

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.rules.CapexIntensityRule
import com.valueinvesting.webapp.ruleengine.rules.CurrentRatioRule
import com.valueinvesting.webapp.ruleengine.rules.DebtToIncomeRule
import com.valueinvesting.webapp.ruleengine.rules.DividendContinuityRule
import com.valueinvesting.webapp.ruleengine.rules.EarningsStabilityRule
import com.valueinvesting.webapp.ruleengine.rules.EpsGrowthRule
import com.valueinvesting.webapp.ruleengine.rules.GrossMarginRule
import com.valueinvesting.webapp.ruleengine.rules.NcavLatestRule
import com.valueinvesting.webapp.ruleengine.rules.NetMarginRule
import com.valueinvesting.webapp.ruleengine.rules.NetNetRatioRule
import com.valueinvesting.webapp.ruleengine.rules.PbLatestRule
import com.valueinvesting.webapp.ruleengine.rules.Pe3yAvgRule
import com.valueinvesting.webapp.ruleengine.rules.RoeRule
import com.valueinvesting.webapp.ruleengine.rules.RoicRule
import com.valueinvesting.webapp.ruleengine.rules.SizeRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// TSK-318 Scenario 5 — No-regression: RuleEngine with all 15 ruleId (pre-EP-023 + NCAV).
//
// Verifies that adding NCAV_LATEST + NET_NET_RATIO to the Rule Engine does NOT alter
// the signals produced by the pre-existing 13 ruleId on fixtures resembling AAPL/MSFT/KO.
//
// TSK-318 §Scenari obbligatori Scenario 5:
//   "Lanciare la suite completa del Rule Engine (13 ruleId esistenti) su fixture AAPL/MSFT/KO;
//    tutti i segnali e i campi tipati devono essere invariati rispetto al comportamento
//    pre-EP-023."
//
// Strategy:
//   1. Run the engine with 13 rules only → capture signals (pre-EP-023 baseline).
//   2. Run the engine with 15 rules (+ NcavLatestRule + NetNetRatioRule) → same dataset.
//   3. Assert the 13 pre-existing ruleId produce IDENTICAL signals in both runs.
//   4. Assert 2 additional ruleId are present (NCAV_LATEST + NET_NET_RATIO) and carry
//      deterministic typed values.
//   5. Assert lexicographic ordering over all 15 signals.
//
// Fixture semantics (analogous to GrahamRulesIntegrationTest AAPL/MSFT/KO):
//   AAPL-like: strong quality, positive ncavTotal → GREEN on NCAV_LATEST + ratio outcome.
//   MSFT-like: strong quality, positive ncavTotal, price barely under 2/3 threshold → GREEN NET_NET_RATIO.
//   KO-like: strong quality, negative ncavTotal (consumer staples leverage) → RED NCAV_LATEST +
//            NOT_CALCULABLE NET_NET_RATIO.
//
// Note: because this test does not use @SpringBootTest, it does NOT hit Postgres or FMP.
//       It is a pure unit test that wires the rules manually (same pattern RuleEngineServiceTest).
//
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/TSK-318.md §Scenari 5]
// [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/ruleengine/RuleEngineServiceTest.kt]
class RuleEngineNcavNoRegressionTest {

    // Pre-EP-023 set: 13 rules (7 Buffett + 6 Graham defensive).
    private val pre13Rules = listOf(
        RoeRule(),
        RoicRule(),
        GrossMarginRule(),
        NetMarginRule(),
        CurrentRatioRule(),
        DebtToIncomeRule(),
        CapexIntensityRule(),
        SizeRule(),
        EarningsStabilityRule(),
        EpsGrowthRule(),
        Pe3yAvgRule(),
        PbLatestRule(),
        DividendContinuityRule(),
    )

    // Post-EP-023 set: 15 rules (13 pre + 2 NCAV Graham enterprising).
    private val post15Rules = pre13Rules + listOf(
        NcavLatestRule(),
        NetNetRatioRule(),
    )

    // Expected lexicographic order for the full 15-ruleId set (ADR-029 §4 + RuleEngineService.sortedBy).
    private val expectedOrderedIds = listOf(
        "CAPEX_INTENSITY_10Y_AVG",
        "CURRENT_RATIO_LATEST",
        "DEBT_TO_INCOME_LATEST",
        "DIVIDEND_CONTINUITY_20Y",
        "EARNINGS_STABILITY_10Y",
        "EPS_GROWTH_10Y",
        "GROSS_MARGIN_10Y_AVG",
        "NCAV_LATEST",              // < "NET_MARGIN_10Y_AVG" (C < T)
        "NET_MARGIN_10Y_AVG",
        "NET_NET_RATIO",            // > "NET_MARGIN_10Y_AVG", < "PB_LATEST"
        "PB_LATEST",
        "PE_3Y_AVG",
        "ROE_10Y_AVG",
        "ROIC_10Y_AVG",
        "SIZE_LATEST",
    )

    // =========================================================================
    // 1. 13 pre-existing signals are UNCHANGED when the 2 new rules are added
    // =========================================================================

    @Test
    fun `AAPL-like fixture — pre-EP-023 13 signals invariant after adding NCAV rules`() {
        val dataset = aaplLikeDataset()
        val serviceOld = RuleEngineService(rules = pre13Rules)
        val serviceNew = RuleEngineService(rules = post15Rules)

        val oldResults = serviceOld.evaluateAll(dataset)
        val newResults = serviceNew.evaluateAll(dataset)

        val oldMap = oldResults.associateBy { it.ruleId }
        val newMap = newResults.associateBy { it.ruleId }

        // Each of the 13 pre-existing ruleId must have the SAME signal
        CANONICAL_13_RULE_IDS.forEach { ruleId ->
            assertThat(newMap[ruleId]?.signal)
                .describedAs("Signal for $ruleId must be unchanged after EP-023")
                .isEqualTo(oldMap[ruleId]?.signal)
        }
    }

    @Test
    fun `MSFT-like fixture — pre-EP-023 13 signals invariant`() {
        val dataset = msftLikeDataset()
        val serviceOld = RuleEngineService(rules = pre13Rules)
        val serviceNew = RuleEngineService(rules = post15Rules)

        val oldResults = serviceOld.evaluateAll(dataset).associateBy { it.ruleId }
        val newResults = serviceNew.evaluateAll(dataset).associateBy { it.ruleId }

        CANONICAL_13_RULE_IDS.forEach { ruleId ->
            assertThat(newResults[ruleId]?.signal)
                .describedAs("Signal for $ruleId must be unchanged after EP-023")
                .isEqualTo(oldResults[ruleId]?.signal)
        }
    }

    @Test
    fun `KO-like fixture — pre-EP-023 13 signals invariant`() {
        val dataset = koLikeDataset()
        val serviceOld = RuleEngineService(rules = pre13Rules)
        val serviceNew = RuleEngineService(rules = post15Rules)

        val oldResults = serviceOld.evaluateAll(dataset).associateBy { it.ruleId }
        val newResults = serviceNew.evaluateAll(dataset).associateBy { it.ruleId }

        CANONICAL_13_RULE_IDS.forEach { ruleId ->
            assertThat(newResults[ruleId]?.signal)
                .describedAs("Signal for $ruleId must be unchanged after EP-023")
                .isEqualTo(oldResults[ruleId]?.signal)
        }
    }

    // =========================================================================
    // 2. Total count is 15 with the new rules
    // =========================================================================

    @Test
    fun `evaluateAll produces exactly 15 signals when NcavLatestRule and NetNetRatioRule are included`() {
        val service = RuleEngineService(rules = post15Rules)

        val results = service.evaluateAll(aaplLikeDataset())

        assertAll(
            { assertThat(results).hasSize(15) },
            { assertThat(results.map { it.ruleId }.toSet()).hasSize(15) }, // no duplicates
        )
    }

    // =========================================================================
    // 3. NCAV signals on AAPL-like fixture (positive ncavTotal)
    // =========================================================================

    @Test
    fun `AAPL-like — NCAV_LATEST GREEN (positive ncavTotal) and NET_NET_RATIO deterministic`() {
        val service = RuleEngineService(rules = post15Rules)
        val results = service.evaluateAll(aaplLikeDataset()).associateBy { it.ruleId }

        assertAll(
            { assertThat(results).containsKey("NCAV_LATEST") },
            { assertThat(results["NCAV_LATEST"]!!.signal).isEqualTo(Signal.GREEN) },
            { assertThat(results).containsKey("NET_NET_RATIO") },
            // ncavPerShare is positive → NET_NET_RATIO is GREEN or RED (not INDETERMINATE/NOT_CALCULABLE)
            {
                val netNetSignal = results["NET_NET_RATIO"]!!.signal
                assertThat(netNetSignal).isIn(Signal.GREEN, Signal.RED)
            },
        )
    }

    @Test
    fun `AAPL-like — NET_NET_RATIO typed fields populated when ncavPerShare positive`() {
        val service = RuleEngineService(rules = post15Rules)
        val results = service.evaluateAll(aaplLikeDataset())
        val netNetSignal = results.first { it.ruleId == "NET_NET_RATIO" } as RuleSignal.NetNetRatio

        assertAll(
            { assertThat(netNetSignal.ncavPerShare).isNotNull() },
            { assertThat(netNetSignal.priceLatest).isNotNull() },
            { assertThat(netNetSignal.ratio).isNotNull() },
            { assertThat(netNetSignal.thresholdRatio).isEqualTo(RuleSignal.NetNetRatio.THRESHOLD_RATIO) },
        )
    }

    // =========================================================================
    // 4. NCAV signals on KO-like fixture (negative ncavTotal — consumer leverage)
    // =========================================================================

    @Test
    fun `KO-like — NCAV_LATEST RED (ncavTotal negative) and NET_NET_RATIO NOT_CALCULABLE`() {
        val service = RuleEngineService(rules = post15Rules)
        val results = service.evaluateAll(koLikeDataset()).associateBy { it.ruleId }

        assertAll(
            { assertThat(results["NCAV_LATEST"]!!.signal).isEqualTo(Signal.RED) },
            { assertThat(results["NET_NET_RATIO"]!!.signal).isEqualTo(Signal.NOT_CALCULABLE) },
        )
    }

    @Test
    fun `KO-like — NCAV_LATEST RED has populated ncavTotal and ncavPerShare (not null)`() {
        val service = RuleEngineService(rules = post15Rules)
        val results = service.evaluateAll(koLikeDataset())
        val ncavLatest = results.first { it.ruleId == "NCAV_LATEST" } as RuleSignal.NcavLatest

        assertAll(
            { assertThat(ncavLatest.ncavTotal).isNotNull() },
            { assertThat(ncavLatest.ncavTotal!!).isLessThanOrEqualTo(0.0) },
            { assertThat(ncavLatest.ncavPerShare).isNotNull() },
        )
    }

    // =========================================================================
    // 5. Lexicographic ordering of all 15 ruleId
    // =========================================================================

    @Test
    fun `evaluateAll 15 signals are sorted lexicographically including NCAV_LATEST and NET_NET_RATIO`() {
        val service = RuleEngineService(rules = post15Rules)

        val results = service.evaluateAll(aaplLikeDataset())

        assertThat(results.map { it.ruleId })
            .containsExactly(*expectedOrderedIds.toTypedArray())
    }

    @Test
    fun `evaluateAll sorting is stable regardless of rule injection order`() {
        // Inject rules in reverse order — output must still be sorted
        val serviceReversed = RuleEngineService(rules = post15Rules.reversed())

        val results = serviceReversed.evaluateAll(aaplLikeDataset())

        assertThat(results.map { it.ruleId })
            .containsExactly(*expectedOrderedIds.toTypedArray())
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /**
     * AAPL-like: strong quality metrics, 10y data, positive NCAV (currentAssets >> liabilities),
     * price below 2/3 × ncavPerShare for GREEN NET_NET_RATIO.
     */
    @Suppress("DEPRECATION")
    private fun aaplLikeDataset(): FinancialDataset {
        // ncavTotal = 200_000_000_000 - 100_000_000_000 = 100_000_000_000
        // shares = 15_550_000_000 → ncavPerShare ≈ 6.43
        // 2/3 × 6.43 ≈ 4.29; price=3.5 → ratio ≈ 0.545 → GREEN
        val shares = 15_550_000_000.0
        val price = 3.5
        return FinancialDataset(
            ticker = "AAPL",
            income = (0 until 10).map { i ->
                IncomeStatementDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    grossProfitRatio = 0.44,
                    netIncomeRatio = 0.25,
                    netIncome = 100_000_000_000.0,
                    revenue = 400_000_000_000.0,
                    eps = 6.0,
                    weightedAverageShsOutDil = shares,
                )
            },
            balance = (0 until 10).map { i ->
                BalanceSheetDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    totalCurrentAssets = 200_000_000_000.0,
                    totalCurrentLiabilities = 80_000_000_000.0,
                    totalLiabilities = 100_000_000_000.0,
                    longTermDebt = 50_000_000_000.0,
                    totalStockholdersEquity = 70_000_000_000.0,
                )
            },
            cashFlow = (0 until 10).map { i ->
                CashFlowDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    capitalExpenditure = -10_000_000_000.0,
                )
            },
            keyMetrics = (0 until 10).map { i ->
                KeyMetricsDto(
                    symbol = "AAPL",
                    calendarYear = (2024 - i).toString(),
                    roe = 0.20,
                    roic = 0.18,
                    bookValuePerShare = 4.5,
                )
            },
            dividends = consecutiveDividends(2005, 2024),
            dataSnapshotAt = Instant.parse("2026-06-05T00:00:00Z"),
            currentPrice = price,
        )
    }

    /**
     * MSFT-like: high-quality metrics, 10y data, positive NCAV, price set so that
     * ratio lands just above 2/3 (RED NET_NET_RATIO — realistic for MSFT at market prices).
     */
    @Suppress("DEPRECATION")
    private fun msftLikeDataset(): FinancialDataset {
        // ncavTotal = 100_000_000_000 - 60_000_000_000 = 40_000_000_000
        // shares = 7_500_000_000 → ncavPerShare ≈ 5.33
        // 2/3 × 5.33 ≈ 3.56; price=400.0 → ratio ≈ 75.05 >> 0.6667 → RED
        val shares = 7_500_000_000.0
        val price = 400.0
        return FinancialDataset(
            ticker = "MSFT",
            income = (0 until 10).map { i ->
                IncomeStatementDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    grossProfitRatio = 0.70,
                    netIncomeRatio = 0.36,
                    netIncome = 90_000_000_000.0,
                    revenue = 250_000_000_000.0,
                    eps = 12.0,
                    weightedAverageShsOutDil = shares,
                )
            },
            balance = (0 until 10).map { i ->
                BalanceSheetDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    totalCurrentAssets = 100_000_000_000.0,
                    totalCurrentLiabilities = 30_000_000_000.0,
                    totalLiabilities = 60_000_000_000.0,
                    longTermDebt = 40_000_000_000.0,
                    totalStockholdersEquity = 80_000_000_000.0,
                )
            },
            cashFlow = (0 until 10).map { i ->
                CashFlowDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    capitalExpenditure = -20_000_000_000.0,
                )
            },
            keyMetrics = (0 until 10).map { i ->
                KeyMetricsDto(
                    symbol = "MSFT",
                    calendarYear = (2024 - i).toString(),
                    roe = 0.35,
                    roic = 0.25,
                    bookValuePerShare = 11.0,
                )
            },
            dividends = consecutiveDividends(2005, 2024),
            dataSnapshotAt = Instant.parse("2026-06-05T00:00:00Z"),
            currentPrice = price,
        )
    }

    /**
     * KO-like: consumer staples, high leverage (totalLiabilities >> totalCurrentAssets),
     * ncavTotal negative → NCAV_LATEST RED + NET_NET_RATIO NOT_CALCULABLE.
     */
    @Suppress("DEPRECATION")
    private fun koLikeDataset(): FinancialDataset {
        // ncavTotal = 10_000_000_000 - 30_000_000_000 = -20_000_000_000 → negative
        val shares = 4_300_000_000.0
        val price = 60.0
        return FinancialDataset(
            ticker = "KO",
            income = (0 until 10).map { i ->
                IncomeStatementDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    grossProfitRatio = 0.60,
                    netIncomeRatio = 0.23,
                    netIncome = 9_500_000_000.0,
                    revenue = 43_000_000_000.0,
                    eps = 2.2,
                    weightedAverageShsOutDil = shares,
                )
            },
            balance = (0 until 10).map { i ->
                BalanceSheetDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    totalCurrentAssets = 10_000_000_000.0,
                    totalCurrentLiabilities = 18_000_000_000.0,
                    // totalLiabilities >> currentAssets → ncavTotal negative
                    totalLiabilities = 30_000_000_000.0,
                    longTermDebt = 24_000_000_000.0,
                    totalStockholdersEquity = 25_000_000_000.0,
                )
            },
            cashFlow = (0 until 10).map { i ->
                CashFlowDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    capitalExpenditure = -2_000_000_000.0,
                )
            },
            keyMetrics = (0 until 10).map { i ->
                KeyMetricsDto(
                    symbol = "KO",
                    calendarYear = (2024 - i).toString(),
                    roe = 0.40,
                    roic = 0.18,
                    bookValuePerShare = 5.0,
                )
            },
            dividends = consecutiveDividends(2000, 2024),
            dataSnapshotAt = Instant.parse("2026-06-05T00:00:00Z"),
            currentPrice = price,
        )
    }

    private fun consecutiveDividends(fromYear: Int, toYear: Int): List<DividendRecord> =
        (fromYear..toYear).map { year ->
            DividendRecord(
                date = "$year-06-15",
                adjDividend = 0.44,
                dividend = 0.44,
                recordDate = "$year-06-13",
                paymentDate = "$year-07-01",
                declarationDate = "$year-05-01",
            )
        }

    companion object {
        /** The 13 ruleId present before EP-023. Used for no-regression assertions. */
        private val CANONICAL_13_RULE_IDS = setOf(
            "CAPEX_INTENSITY_10Y_AVG",
            "CURRENT_RATIO_LATEST",
            "DEBT_TO_INCOME_LATEST",
            "DIVIDEND_CONTINUITY_20Y",
            "EARNINGS_STABILITY_10Y",
            "EPS_GROWTH_10Y",
            "GROSS_MARGIN_10Y_AVG",
            "NET_MARGIN_10Y_AVG",
            "PB_LATEST",
            "PE_3Y_AVG",
            "ROE_10Y_AVG",
            "ROIC_10Y_AVG",
            "SIZE_LATEST",
        )
    }
}
