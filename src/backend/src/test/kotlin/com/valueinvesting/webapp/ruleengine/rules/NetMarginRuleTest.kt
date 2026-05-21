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

// Unit tests for NetMarginRule. Binary classification per TSK-013:
//   > 10% -> GREEN, <= 10% -> RED. INDETERMINATE / NOT_CALCULABLE for data gaps.
class NetMarginRuleTest {

    private val rule = NetMarginRule()

    @Test
    fun `GREEN when 10y average net margin is above 10 percent`() {
        val dataset = datasetWithNetRatios(List(10) { 0.18 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("NET_MARGIN_10Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.18, within(1e-9)) },
            { assertThat(result.threshold).contains("10%") },
        )
    }

    @Test
    fun `RED when 10y average net margin is exactly at the 10 percent threshold (boundary closed on RED)`() {
        // > 10% is GREEN; exactly 10% falls into RED per the verbatim spec.
        val dataset = datasetWithNetRatios(List(10) { 0.10 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isCloseTo(0.10, within(1e-9)) },
        )
    }

    @Test
    fun `RED when 10y average net margin is below 10 percent`() {
        val dataset = datasetWithNetRatios(List(10) { 0.05 })

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.RED)
    }

    @Test
    fun `INDETERMINATE when fewer than 5 years of usable net margin`() {
        val dataset = datasetWithNetRatios(listOf(0.20, 0.21, 0.19, 0.22))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.rationale).contains("4") },
        )
    }

    @Test
    fun `null safety - mixed null and non-null net margin values`() {
        // 6 non-null at 0.20 -> effective >= 5 -> GREEN, average untouched by nulls.
        val mixed = listOf<Double?>(0.20, null, 0.20, null, 0.20, 0.20, null, 0.20, 0.20)
        val dataset = datasetWithNetRatios(mixed)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.20, within(1e-9)) },
        )
    }

    @Test
    fun `derived net margin from revenue and netIncome when ratio is missing`() {
        val rows = (0 until 10).map { i ->
            IncomeStatementDto(
                calendarYear = (2024 - i).toString(),
                revenue = 1000.0,
                netIncome = 150.0,
                // netIncomeRatio intentionally null to exercise fallback.
            )
        }
        val dataset = datasetWithIncome(rows)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.15, within(1e-9)) },
        )
    }

    @Test
    fun `NOT_CALCULABLE when income list is empty`() {
        val dataset = datasetWithIncome(emptyList())

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }

    // --- helpers ---

    private fun datasetWithNetRatios(values: List<Double?>): FinancialDataset {
        val rows = values.mapIndexed { i, v ->
            IncomeStatementDto(
                calendarYear = (2024 - i).toString(),
                netIncomeRatio = v,
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
