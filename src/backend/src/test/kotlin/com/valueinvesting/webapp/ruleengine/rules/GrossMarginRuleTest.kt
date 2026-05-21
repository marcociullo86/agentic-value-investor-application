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

// Unit tests for GrossMarginRule. Covers the 3 outcome bands of US-008 plus the
// data-availability edge cases and the null-safety contract.
class GrossMarginRuleTest {

    private val rule = GrossMarginRule()

    @Test
    fun `GREEN when 10y average gross margin is above 40 percent`() {
        // ratio supplied directly by FMP (preferred source).
        val dataset = datasetWithGrossRatios(List(10) { 0.42 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("GROSS_MARGIN_10Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.42, within(1e-9)) },
            { assertThat(result.threshold).contains("40%") },
            { assertThat(result.rationale).contains("10") },
        )
    }

    @Test
    fun `YELLOW when 10y average gross margin is in the 30-40 percent band (35 percent)`() {
        val dataset = datasetWithGrossRatios(List(10) { 0.35 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isCloseTo(0.35, within(1e-9)) },
        )
    }

    @Test
    fun `RED when 10y average gross margin is below 30 percent (25 percent)`() {
        val dataset = datasetWithGrossRatios(List(10) { 0.25 })

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.RED)
    }

    @Test
    fun `INDETERMINATE when fewer than 5 years of usable gross margin`() {
        val dataset = datasetWithGrossRatios(listOf(0.50, 0.48, 0.52, 0.49))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.rationale).contains("4") },
            { assertThat(result.observedValue).isNotNull() },
        )
    }

    @Test
    fun `null safety - years with null ratio AND null grossProfit_revenue excluded`() {
        // 8 rows: 4 with FMP ratio = 0.42, 4 with everything null.
        // Effective sample = 4 -> INDETERMINATE. Mean must NOT include zeros.
        val mixed = listOf<Double?>(0.42, null, 0.42, null, 0.42, null, 0.42, null)
        val dataset = datasetWithGrossRatios(mixed)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue!!).isCloseTo(0.42, within(1e-9)) },
        )
    }

    @Test
    fun `derived gross margin from revenue and grossProfit when ratio is missing`() {
        // No grossProfitRatio supplied; revenue=1000, grossProfit=450 -> 0.45 each year.
        val rows = (0 until 10).map { i ->
            IncomeStatementDto(
                calendarYear = (2024 - i).toString(),
                revenue = 1000.0,
                grossProfit = 450.0,
                // grossProfitRatio intentionally null to exercise the fallback.
            )
        }
        val dataset = datasetWithIncome(rows)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.45, within(1e-9)) },
        )
    }

    @Test
    fun `revenue zero or negative is excluded - never used as a divisor`() {
        // 5 valid years at 0.42 + 5 years with revenue=0.0 (would NaN/inf if divided).
        // Effective sample must be exactly 5 -> classify normally.
        val good = (0 until 5).map { i ->
            IncomeStatementDto(
                calendarYear = (2024 - i).toString(),
                revenue = 1000.0,
                grossProfit = 420.0,
            )
        }
        val bad = (5 until 10).map { i ->
            IncomeStatementDto(
                calendarYear = (2024 - i).toString(),
                revenue = 0.0,
                grossProfit = 100.0,
            )
        }
        val dataset = datasetWithIncome(good + bad)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.42, within(1e-9)) },
            { assertThat(result.observedValue!!.isFinite()).isTrue() },
        )
    }

    @Test
    fun `NOT_CALCULABLE when income list is empty`() {
        val dataset = datasetWithIncome(emptyList())

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }

    @Test
    fun `NOT_CALCULABLE when every row has no usable gross margin source`() {
        val rows = List(6) { IncomeStatementDto(calendarYear = "2024") } // all nulls
        val dataset = datasetWithIncome(rows)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- helpers ---

    private fun datasetWithGrossRatios(values: List<Double?>): FinancialDataset {
        val rows = values.mapIndexed { i, v ->
            IncomeStatementDto(
                calendarYear = (2024 - i).toString(),
                grossProfitRatio = v,
            )
        }
        return datasetWithIncome(rows)
    }

    private fun datasetWithIncome(rows: List<IncomeStatementDto>): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = rows,
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun within(eps: Double) = org.assertj.core.data.Offset.offset(eps)
}
