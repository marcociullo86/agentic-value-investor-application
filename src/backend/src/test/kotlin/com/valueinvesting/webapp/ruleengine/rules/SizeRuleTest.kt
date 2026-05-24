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

// Unit tests for SizeRule (TSK-074, US-032, EP-010).
//
// Covers US-032 Acceptance Criteria verbatim:
//   - revenue >= $100M  -> GREEN
//   - revenue <  $100M  -> RED    (data present, below threshold)
//   - income empty      -> INDETERMINATE (NOT RED)
//   - revenue == null   -> INDETERMINATE (NOT RED)
//
// Plus bonus edges mandated by TSK-074:
//   - revenue == 0.0    -> RED   (PATTERN §7 r.13: literal zero is not a placeholder)
//   - multi-year picker  -> latest fiscal year selected by max(date)
//   - ruleId assertion
//
// Shape follows CapexIntensityRuleTest.kt (single-year rule, same package).
// Assertion library: AssertJ (already present in the test classpath — no new deps).
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-032-regola-dimensioni-graham/TSK-074.md]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 1]
class SizeRuleTest {

    private val rule = SizeRule()

    // --- AC1: GREEN ---

    @Test
    fun `GREEN when revenue is 500 million (well above 100M threshold)`() {
        val dataset = singleYearDataset(year = "2024", revenue = 500_000_000.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("SIZE_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(500_000_000.0) },
            { assertThat(result.threshold).contains("100M") },
        )
    }

    @Test
    fun `GREEN at exactly 100 million (boundary inclusive)`() {
        // The rule uses >= so exactly $100M must be GREEN.
        val dataset = singleYearDataset(year = "2024", revenue = 100_000_000.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(100_000_000.0) },
        )
    }

    // --- AC2: RED ---

    @Test
    fun `RED when revenue is 50 million (below 100M threshold)`() {
        val dataset = singleYearDataset(year = "2024", revenue = 50_000_000.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("SIZE_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isEqualTo(50_000_000.0) },
        )
    }

    @Test
    fun `RED at 99999999 point 99 (one cent below boundary)`() {
        val dataset = singleYearDataset(year = "2024", revenue = 99_999_999.99)

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.RED)
    }

    // --- AC3: INDETERMINATE when income list is empty ---

    @Test
    fun `INDETERMINATE when income list is empty (NOT RED)`() {
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = emptyList(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC4 (bonus): INDETERMINATE when revenue field is null ---

    @Test
    fun `INDETERMINATE when revenue is null (missing field, not coerced to zero)`() {
        val dataset = singleYearDataset(year = "2024", revenue = null)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("mancante") },
        )
    }

    // --- AC5 (bonus): edge revenue=0.0 is RED ---

    @Test
    fun `RED when revenue is 0 point 0 (literal zero is not a placeholder per PATTERN r13)`() {
        // FMP missing field convention = JSON null; 0.0 is a real data point (defunct / shell
        // company). The rule must NOT treat this as INDETERMINATE.
        val dataset = singleYearDataset(year = "2024", revenue = 0.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.signal).isNotEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isEqualTo(0.0) },
        )
    }

    // --- AC6 (bonus): multi-year picker selects latest date ---

    @Test
    fun `latest fiscal year selected when dataset has multiple years`() {
        // Three years. 2022 and 2023 are below $100M (RED territory), 2024 is $200M (GREEN).
        // The rule must pick the row with the lexicographically maximum `date`.
        val income = listOf(
            IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", revenue = 50_000_000.0),
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", revenue = 80_000_000.0),
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", revenue = 200_000_000.0),
        )
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = income,
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(200_000_000.0) },
            { assertThat(result.rationale).contains("2024") },
        )
    }

    @Test
    fun `latest fiscal year selected also when list is in reverse chronological order`() {
        // Same scenario but list reversed — maxByOrNull must not rely on list order.
        val income = listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", revenue = 200_000_000.0),
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", revenue = 80_000_000.0),
            IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", revenue = 50_000_000.0),
        )
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = income,
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(200_000_000.0) },
        )
    }

    // --- AC7 (bonus): ruleId ---

    @Test
    fun `ruleId is SIZE_LATEST for all signal outcomes`() {
        val green = rule.evaluate(singleYearDataset(year = "2024", revenue = 500_000_000.0))
        val red = rule.evaluate(singleYearDataset(year = "2024", revenue = 50_000_000.0))
        val indeterminate = rule.evaluate(singleYearDataset(year = "2024", revenue = null))

        assertAll(
            { assertThat(green.ruleId).isEqualTo("SIZE_LATEST") },
            { assertThat(red.ruleId).isEqualTo("SIZE_LATEST") },
            { assertThat(indeterminate.ruleId).isEqualTo("SIZE_LATEST") },
        )
    }

    // --- threshold label ---

    @Test
    fun `threshold label contains 100M marker in GREEN result`() {
        val result = rule.evaluate(singleYearDataset(year = "2024", revenue = 500_000_000.0))

        assertThat(result.threshold).contains("100M")
    }

    // --- helper ---

    private fun singleYearDataset(year: String, revenue: Double?): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(
                    date = "$year-12-31",
                    calendarYear = year,
                    revenue = revenue,
                ),
            ),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
        )
}
