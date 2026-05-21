package com.valueinvesting.webapp.ruleengine

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.rules.CapexIntensityRule
import com.valueinvesting.webapp.ruleengine.rules.CurrentRatioRule
import com.valueinvesting.webapp.ruleengine.rules.DebtToIncomeRule
import com.valueinvesting.webapp.ruleengine.rules.GrossMarginRule
import com.valueinvesting.webapp.ruleengine.rules.NetMarginRule
import com.valueinvesting.webapp.ruleengine.rules.RoeRule
import com.valueinvesting.webapp.ruleengine.rules.RoicRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

// Unit tests for the RuleEngineService aggregator.
// We verify both the "fan-out across all injected ValuationRule beans" semantic
// AND the deterministic ordering by ruleId.
//
// TSK-015 update: the engine now fans out across 7 rules (ROE, ROIC, GrossMargin,
// NetMargin, CurrentRatio, DebtToIncome, CapexIntensity). Spring auto-injection
// in production guarantees all @Component ValuationRule beans are wired without
// a central registry edit. This is the COMPLETE set required by US-007..US-010.
class RuleEngineServiceTest {

    @Test
    fun `evaluateAll returns one RuleSignal per injected rule`() {
        val service = RuleEngineService(
            rules = listOf(
                RoeRule(),
                RoicRule(),
                GrossMarginRule(),
                NetMarginRule(),
                CurrentRatioRule(),
                DebtToIncomeRule(),
                CapexIntensityRule(),
            ),
        )
        val dataset = datasetWith(roe = 0.20, roic = 0.18, grossMarginRatio = 0.50, netMarginRatio = 0.20)

        val results = service.evaluateAll(dataset)

        assertThat(results).hasSize(7)
        assertThat(results.map { it.ruleId })
            .containsExactlyInAnyOrder(
                "ROE_10Y_AVG",
                "ROIC_10Y_AVG",
                "GROSS_MARGIN_10Y_AVG",
                "NET_MARGIN_10Y_AVG",
                "CURRENT_RATIO_LATEST",
                "DEBT_TO_INCOME_LATEST",
                "CAPEX_INTENSITY_10Y_AVG",
            )
        // ROE/ROIC/GM/NM are GREEN given the dataset. CurrentRatio, DebtToIncome
        // and CapexIntensity also see the populated balance / cashFlow / income.
        assertThat(results.filter { it.ruleId.endsWith("_10Y_AVG") })
            .allMatch { it.signal == Signal.GREEN }
    }

    @Test
    fun `evaluateAll produces exactly 7 signals on a complete dataset (US-010 DoD)`() {
        // Full set of @Component ValuationRule beans wired manually here to mirror
        // what Spring auto-injection delivers in production (TSK-012..015).
        val service = RuleEngineService(
            rules = listOf(
                RoeRule(),
                RoicRule(),
                GrossMarginRule(),
                NetMarginRule(),
                CurrentRatioRule(),
                DebtToIncomeRule(),
                CapexIntensityRule(),
            ),
        )
        val dataset = datasetWith(roe = 0.20, roic = 0.18, grossMarginRatio = 0.50, netMarginRatio = 0.20)

        val results = service.evaluateAll(dataset)

        // US-010 DoD verbatim: "RuleEngineService.evaluateAll() produce lista di
        // esattamente 7 segnali su dataset completo."
        assertThat(results).hasSize(7)
        assertThat(results.map { it.ruleId }.toSet()).hasSize(7) // no duplicates
    }

    @Test
    fun `evaluateAll is sorted by ruleId regardless of input order`() {
        // Inject in reverse order; the service must still emit lexicographically sorted ids.
        val service = RuleEngineService(
            rules = listOf(
                DebtToIncomeRule(),
                RoicRule(),
                NetMarginRule(),
                RoeRule(),
                CapexIntensityRule(),
                GrossMarginRule(),
                CurrentRatioRule(),
            ),
        )
        val dataset = datasetWith(roe = 0.20, roic = 0.18, grossMarginRatio = 0.50, netMarginRatio = 0.20)

        val results = service.evaluateAll(dataset)

        // Lexicographic order (TSK-015 update — CAPEX_ sorts before CURRENT_):
        //   CAPEX_INTENSITY_10Y_AVG < CURRENT_RATIO_LATEST < DEBT_TO_INCOME_LATEST
        //   < GROSS_MARGIN_... < NET_MARGIN_... < ROE_... < ROIC_...
        assertThat(results.map { it.ruleId })
            .containsExactly(
                "CAPEX_INTENSITY_10Y_AVG",
                "CURRENT_RATIO_LATEST",
                "DEBT_TO_INCOME_LATEST",
                "GROSS_MARGIN_10Y_AVG",
                "NET_MARGIN_10Y_AVG",
                "ROE_10Y_AVG",
                "ROIC_10Y_AVG",
            )
    }

    @Test
    fun `evaluateAll returns empty list when no rules are registered`() {
        val service = RuleEngineService(rules = emptyList())
        val dataset = datasetWith(roe = 0.20, roic = 0.18, grossMarginRatio = 0.50, netMarginRatio = 0.20)

        assertThat(service.evaluateAll(dataset)).isEmpty()
    }

    @Test
    fun `evaluateAll emits two distinct signals for the pricing-power rules (US-008 DoD)`() {
        val service = RuleEngineService(
            rules = listOf(RoeRule(), RoicRule(), GrossMarginRule(), NetMarginRule()),
        )
        val dataset = datasetWith(roe = 0.20, roic = 0.18, grossMarginRatio = 0.50, netMarginRatio = 0.20)

        val results = service.evaluateAll(dataset)
        val pricingPowerIds = results.map { it.ruleId }.filter {
            it == "GROSS_MARGIN_10Y_AVG" || it == "NET_MARGIN_10Y_AVG"
        }

        assertThat(pricingPowerIds)
            .containsExactlyInAnyOrder("GROSS_MARGIN_10Y_AVG", "NET_MARGIN_10Y_AVG")
    }

    @Test
    fun `evaluateAll emits two distinct signals for the financial-solidity rules (US-009 DoD)`() {
        val service = RuleEngineService(
            rules = listOf(CurrentRatioRule(), DebtToIncomeRule()),
        )
        val dataset = datasetWith(roe = 0.20, roic = 0.18, grossMarginRatio = 0.50, netMarginRatio = 0.20)

        val results = service.evaluateAll(dataset)
        val solidityIds = results.map { it.ruleId }

        assertThat(solidityIds)
            .containsExactlyInAnyOrder("CURRENT_RATIO_LATEST", "DEBT_TO_INCOME_LATEST")
    }

    // --- helpers ---

    // Builds a populated dataset across all 4 lists so every rule (including the
    // capital-intensity rule from TSK-015) has the inputs it needs to classify
    // GREEN. capitalExpenditure = -20 with netIncome = 100 -> ratio 20% < 25% GREEN.
    private fun datasetWith(
        roe: Double,
        roic: Double,
        grossMarginRatio: Double,
        netMarginRatio: Double,
    ): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = (0 until 10).map { i ->
                IncomeStatementDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    grossProfitRatio = grossMarginRatio,
                    netIncomeRatio = netMarginRatio,
                    netIncome = 100.0,
                )
            },
            balance = (0 until 10).map { i ->
                BalanceSheetDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    totalCurrentAssets = 300.0,
                    totalCurrentLiabilities = 100.0,
                    longTermDebt = 200.0,
                )
            },
            cashFlow = (0 until 10).map { i ->
                CashFlowDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    capitalExpenditure = -20.0,
                )
            },
            keyMetrics = (0 until 10).map { i ->
                KeyMetricsDto(
                    symbol = "TEST",
                    calendarYear = (2024 - i).toString(),
                    roe = roe,
                    roic = roic,
                )
            },
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )
}
