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

// Unit tests for CapexIntensityRule (TSK-015, US-010).
//
// Covers US-010 DoD verbatim:
//   - CapEx/NI < 25%  -> GREEN
//   - CapEx/NI 27%    -> YELLOW
//   - CapEx/NI 35%    -> RED
//   - netIncome <= 0  -> INDETERMINATE (NOT RED)
//
// Plus the standard edges (NOT_CALCULABLE, null safety, latest-year fallback).
// FMP convention check: capitalExpenditure may be NEGATIVE (cash outflow). The
// rule must take |capex| so the ratio is a positive percentage either way.
class CapexIntensityRuleTest {

    private val rule = CapexIntensityRule()

    @Test
    fun `GREEN when 10y average CapEx over Net Income is below 25 percent`() {
        // ratio = |20| / 100 = 0.20 across 10 years.
        val dataset = datasetWith10y(capex = -20.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("CAPEX_INTENSITY_10Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.20, within(1e-9)) },
            { assertThat(result.threshold).contains("25%") },
            { assertThat(result.rationale).contains("Media 10y") },
        )
    }

    @Test
    fun `YELLOW when 10y average CapEx over Net Income is 27 percent`() {
        // ratio = 27 / 100 = 0.27 in the YELLOW band [25%, 30%].
        val dataset = datasetWith10y(capex = -27.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isCloseTo(0.27, within(1e-9)) },
        )
    }

    @Test
    fun `RED when 10y average CapEx over Net Income is 35 percent`() {
        val dataset = datasetWith10y(capex = -35.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isCloseTo(0.35, within(1e-9)) },
        )
    }

    @Test
    fun `YELLOW boundary closed on 25 percent (exactly 25 percent is YELLOW not GREEN)`() {
        // GREEN_THRESHOLD strict (<): exactly 0.25 is YELLOW.
        val dataset = datasetWith10y(capex = -25.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.YELLOW)
    }

    @Test
    fun `YELLOW boundary closed on 30 percent (exactly 30 percent is YELLOW not RED)`() {
        // YELLOW_UPPER_BOUND inclusive (<=): exactly 0.30 is YELLOW.
        val dataset = datasetWith10y(capex = -30.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.YELLOW)
    }

    @Test
    fun `INDETERMINATE when latest net income is zero (US-010 AC verbatim)`() {
        // Single year, netIncome = 0 — div-by-zero protection AND verbatim
        // US-010 AC: "se Utile Netto è nullo, segnale Indeterminato".
        val dataset = singleYearDataset(year = "2024", capex = -20.0, netIncome = 0.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("non positivo") },
        )
    }

    @Test
    fun `INDETERMINATE when latest net income is negative (US-010 AC verbatim NOT RED)`() {
        // Even with a huge capex, a loss-making year MUST NOT be RED.
        val dataset = singleYearDataset(year = "2024", capex = -1_000_000.0, netIncome = -50.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `INDETERMINATE when latest net income is null (never coerced to zero)`() {
        val dataset = singleYearDataset(year = "2024", capex = -20.0, netIncome = null)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("mancante") },
        )
    }

    @Test
    fun `NOT_CALCULABLE when cashFlow list is empty`() {
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
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = listOf(CashFlowDto(date = "2024-12-31", capitalExpenditure = -20.0)),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }

    @Test
    fun `mixed years null-safety - exclude loss years and use latest-year fallback when under 5 usable pairs`() {
        // 4 usable years (2021..2024) + 2 loss years (2019,2020) excluded.
        // < 5 usable -> falls back to latest year (2024) ratio = |20|/100 = 0.20 -> GREEN.
        val income = listOf(
            IncomeStatementDto(date = "2019-12-31", calendarYear = "2019", netIncome = -10.0),
            IncomeStatementDto(date = "2020-12-31", calendarYear = "2020", netIncome = 0.0),
            IncomeStatementDto(date = "2021-12-31", calendarYear = "2021", netIncome = 80.0),
            IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", netIncome = 90.0),
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", netIncome = 95.0),
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = 100.0),
        )
        val cashFlow = listOf(
            CashFlowDto(date = "2019-12-31", calendarYear = "2019", capitalExpenditure = -50.0),
            CashFlowDto(date = "2020-12-31", calendarYear = "2020", capitalExpenditure = -40.0),
            CashFlowDto(date = "2021-12-31", calendarYear = "2021", capitalExpenditure = -16.0),
            CashFlowDto(date = "2022-12-31", calendarYear = "2022", capitalExpenditure = -18.0),
            CashFlowDto(date = "2023-12-31", calendarYear = "2023", capitalExpenditure = -19.0),
            CashFlowDto(date = "2024-12-31", calendarYear = "2024", capitalExpenditure = -20.0),
        )
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = income,
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = cashFlow,
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.20, within(1e-9)) },
            { assertThat(result.rationale).contains("2024") },
            { assertThat(result.rationale).contains("storia parziale") },
        )
    }

    @Test
    fun `positive CapEx sign is handled via abs (defensive when FMP convention flips)`() {
        // Some providers / endpoints expose capex as a positive magnitude.
        // The rule must classify identically whether sign is +20 or -20.
        val dataset = datasetWith10y(capex = 20.0, netIncome = 100.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.20, within(1e-9)) },
        )
    }

    @Test
    fun `10y average uses only pairs with non-null capex and positive netIncome`() {
        // 5 usable years (2020..2024 all 27% YELLOW) + 5 nulls/loss filtered out.
        // Average should be exactly 0.27 -> YELLOW.
        val income = (0 until 10).map { i ->
            val year = 2024 - i
            val ni = if (year < 2020) -1.0 else 100.0 // loss for years <2020
            IncomeStatementDto(
                date = "$year-12-31",
                calendarYear = year.toString(),
                netIncome = ni,
            )
        }
        val cashFlow = (0 until 10).map { i ->
            val year = 2024 - i
            val capex = if (year < 2020) null else -27.0 // null capex for loss years anyway
            CashFlowDto(
                date = "$year-12-31",
                calendarYear = year.toString(),
                capitalExpenditure = capex,
            )
        }
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = income,
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = cashFlow,
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isCloseTo(0.27, within(1e-9)) },
            { assertThat(result.rationale).contains("5 esercizi") },
        )
    }

    // --- helpers ---

    private fun datasetWith10y(capex: Double, netIncome: Double): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = (0 until 10).map { i ->
                IncomeStatementDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    netIncome = netIncome,
                )
            },
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = (0 until 10).map { i ->
                CashFlowDto(
                    date = "${2024 - i}-12-31",
                    calendarYear = (2024 - i).toString(),
                    capitalExpenditure = capex,
                )
            },
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun singleYearDataset(year: String, capex: Double?, netIncome: Double?): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(
                    date = "$year-12-31",
                    calendarYear = year,
                    netIncome = netIncome,
                ),
            ),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = listOf(
                CashFlowDto(
                    date = "$year-12-31",
                    calendarYear = year,
                    capitalExpenditure = capex,
                ),
            ),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun within(eps: Double) = org.assertj.core.data.Offset.offset(eps)
}
