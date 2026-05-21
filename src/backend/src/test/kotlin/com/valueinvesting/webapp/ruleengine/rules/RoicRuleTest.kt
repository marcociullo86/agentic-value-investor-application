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

// Unit tests for RoicRule, symmetric to RoeRuleTest but with the 12% / 8% bands.
class RoicRuleTest {

    private val rule = RoicRule()

    @Test
    fun `GREEN when 10y average ROIC is above 12 percent`() {
        val dataset = datasetWithRoic(List(10) { 0.20 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("ROIC_10Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isGreaterThan(0.12) },
        )
    }

    @Test
    fun `YELLOW when 10y average ROIC is in the 8-12 percent band`() {
        val dataset = datasetWithRoic(List(10) { 0.10 })

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isCloseTo(0.10, within(1e-9)) },
        )
    }

    @Test
    fun `RED when 10y average ROIC is below 8 percent`() {
        val dataset = datasetWithRoic(List(10) { 0.05 })

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.RED)
    }

    @Test
    fun `INDETERMINATE (not RED) when fewer than 5 years of non-null ROIC`() {
        val dataset = datasetWithRoic(listOf(0.02, 0.03, 0.04))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNotNull() },
        )
    }

    @Test
    fun `null safety - mixed null and non-null roic values`() {
        // Effective sample = 6 (>= 5) so we DO classify; mean of non-null = 0.15 -> GREEN.
        val dataset = datasetWithRoic(listOf(0.15, null, 0.15, 0.15, null, 0.15, 0.15, null, 0.15))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(0.15, within(1e-9)) },
        )
    }

    @Test
    fun `NOT_CALCULABLE when every roic is null`() {
        val dataset = datasetWithRoic(List(8) { null })

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }

    // --- helpers ---

    private fun datasetWithRoic(values: List<Double?>): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = values.mapIndexed { i, v ->
                KeyMetricsDto(symbol = "TEST", calendarYear = (2024 - i).toString(), roic = v)
            },
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun within(eps: Double) = org.assertj.core.data.Offset.offset(eps)
}
