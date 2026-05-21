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

// Unit tests for RoeRule. Covers the 4 outcomes mandated by US-007 AC plus
// the null-safety contract documented in TenYearAverage.kt.
class RoeRuleTest {

    private val rule = RoeRule()

    @Test
    fun `GREEN when 10y average ROE is above 15 percent (AAPL-like)`() {
        val dataset = datasetWithRoe(
            // Apple-like 10y ROE history (fractions): well above 15%.
            listOf(0.40, 0.45, 0.55, 0.60, 0.74, 0.87, 1.50, 1.97, 1.71, 1.56),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("ROE_10Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isNotNull() },
            { assertThat(result.observedValue!!).isGreaterThan(0.15) },
            { assertThat(result.threshold).contains("15%") },
            { assertThat(result.rationale).contains("10") },
        )
    }

    @Test
    fun `YELLOW when 10y average ROE is in the 10-15 percent band (e g  12 percent)`() {
        val dataset = datasetWithRoe(List(10) { 0.12 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isCloseTo(0.12, within(1e-9)) },
        )
    }

    @Test
    fun `RED when 10y average ROE is below 10 percent`() {
        val dataset = datasetWithRoe(List(10) { 0.07 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isCloseTo(0.07, within(1e-9)) },
        )
    }

    @Test
    fun `INDETERMINATE (not RED) when fewer than 5 years of non-null ROE`() {
        // Only 4 non-null roe values; US-007 AC requires INDETERMINATE, NOT RED,
        // even if the partial average would have crossed the RED band.
        val dataset = datasetWithRoe(listOf(0.05, 0.06, 0.07, 0.04))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.rationale).contains("4") },
            { assertThat(result.observedValue).isNotNull() },
        )
    }

    @Test
    fun `null safety - years with null roe excluded from average and counted as missing`() {
        // 7 rows total, 3 of them with roe == null. Effective sample = 4 -> INDETERMINATE.
        // Critically: the null rows must NOT pull the mean down toward zero.
        val dataset = datasetWithRoe(listOf(0.20, null, 0.22, null, 0.18, null, 0.21))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            // average of the 4 non-null values = (0.20+0.22+0.18+0.21)/4 = 0.2025
            { assertThat(result.observedValue!!).isCloseTo(0.2025, within(1e-9)) },
        )
    }

    @Test
    fun `NOT_CALCULABLE when every roe is null`() {
        val dataset = datasetWithRoe(List(6) { null })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `NOT_CALCULABLE when keyMetrics list is empty`() {
        val dataset = datasetWithRoe(emptyList())

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }

    // --- helpers ---

    private fun datasetWithRoe(values: List<Double?>): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = values.mapIndexed { i, v ->
                KeyMetricsDto(symbol = "TEST", calendarYear = (2024 - i).toString(), roe = v)
            },
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun within(eps: Double) = org.assertj.core.data.Offset.offset(eps)
}
