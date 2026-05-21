package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// Unit tests for DebtToIncomeRule.
//
// Covers US-009 thresholds plus the critical "netIncome <= 0 -> INDETERMINATE"
// invariant (US-009 AC verbatim: NOT RED).
//
// Note: this rule chooses Option A (interpolated YELLOW band on [4, 5]) — see
// the design note in DebtToIncomeRule.kt.
class DebtToIncomeRuleTest {

    private val rule = DebtToIncomeRule()

    @Test
    fun `GREEN when ratio is below 4 (3_0)`() {
        val dataset = dataset(
            longTermDebt = 300.0,
            netIncome = 100.0,
            year = "2024",
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("DEBT_TO_INCOME_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(3.0, within(1e-9)) },
            { assertThat(result.threshold).contains("4") },
            { assertThat(result.rationale).contains("2024") },
        )
    }

    @Test
    fun `YELLOW when ratio is in the interpolated 4-5 band (4_5)`() {
        // Design decision: TSK-014 leaves [4, 5] unclassified. We interpolate
        // YELLOW here (Option A). See DebtToIncomeRule design note.
        val dataset = dataset(
            longTermDebt = 450.0,
            netIncome = 100.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isCloseTo(4.5, within(1e-9)) },
        )
    }

    @Test
    fun `YELLOW boundary closed on 4_0 (exactly 4 is YELLOW not GREEN)`() {
        // GREEN_THRESHOLD strict (<): exactly 4.0 is YELLOW.
        val dataset = dataset(longTermDebt = 400.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.YELLOW)
    }

    @Test
    fun `YELLOW boundary closed on 5_0 (exactly 5 is YELLOW not RED)`() {
        // YELLOW_UPPER_BOUND inclusive (<=): exactly 5.0 is still YELLOW.
        val dataset = dataset(longTermDebt = 500.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.YELLOW)
    }

    @Test
    fun `RED when ratio exceeds 5 (6_0)`() {
        val dataset = dataset(longTermDebt = 600.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isCloseTo(6.0, within(1e-9)) },
        )
    }

    @Test
    fun `INDETERMINATE (NOT RED) when net income is negative (US-009 AC verbatim)`() {
        // Even with a very high debt, a loss-making year MUST NOT be RED.
        val dataset = dataset(longTermDebt = 1_000_000.0, netIncome = -50.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("non positivo") },
        )
    }

    @Test
    fun `INDETERMINATE when net income is exactly zero (div-by-zero protection)`() {
        val dataset = dataset(longTermDebt = 400.0, netIncome = 0.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `INDETERMINATE when net income is null (never coerced to zero)`() {
        val dataset = dataset(longTermDebt = 400.0, netIncome = null)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("mancante") },
        )
    }

    @Test
    fun `INDETERMINATE when long-term debt is null`() {
        val dataset = dataset(longTermDebt = null, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `NOT_CALCULABLE when balance sheet list is empty`() {
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = listOf(IncomeStatementDto(date = "2024-12-31", netIncome = 100.0)),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }

    @Test
    fun `NOT_CALCULABLE when income statement list is empty`() {
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = listOf(BalanceSheetDto(date = "2024-12-31", longTermDebt = 400.0)),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }

    @Test
    fun `latest year is selected on both balance and income (max date)`() {
        // 2022 ratio 6.0 (RED) and 2024 ratio 3.0 (GREEN) — rule must use 2024.
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", netIncome = 50.0),
                IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = 100.0),
            ),
            balance = listOf(
                BalanceSheetDto(date = "2022-12-31", calendarYear = "2022", longTermDebt = 300.0),
                BalanceSheetDto(date = "2024-12-31", calendarYear = "2024", longTermDebt = 300.0),
            ),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(3.0, within(1e-9)) },
            { assertThat(result.rationale).contains("2024") },
        )
    }

    // --- helpers ---

    private fun dataset(
        longTermDebt: Double?,
        netIncome: Double?,
        year: String = "2024",
    ): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(
                    date = "$year-12-31",
                    calendarYear = year,
                    netIncome = netIncome,
                ),
            ),
            balance = listOf(
                BalanceSheetDto(
                    date = "$year-12-31",
                    calendarYear = year,
                    longTermDebt = longTermDebt,
                ),
            ),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun within(eps: Double) = org.assertj.core.data.Offset.offset(eps)
}
