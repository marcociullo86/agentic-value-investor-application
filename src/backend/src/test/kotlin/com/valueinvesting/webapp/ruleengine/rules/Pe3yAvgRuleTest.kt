package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// Unit tests for Pe3yAvgRule (TSK-080, US-035, EP-010).
//
// Covers US-035 Acceptance Criteria verbatim:
//   - pe3yAvg <= 15.0        -> GREEN
//   - pe3yAvg in (15.0, 20]  -> YELLOW
//   - pe3yAvg > 20.0         -> RED
//   - avgEps3y <= 0          -> INDETERMINATE
//   - currentPrice == null   -> INDETERMINATE
//   - income.size < 3        -> INDETERMINATE
//   - income.isEmpty()       -> NOT_CALCULABLE
//
// Plus bonus edges mandated by TSK-080 prompt:
//   - 1 null EPS in top-3 (2/3 non-null)  -> proceeds on partial avg
//   - 2 null EPS in top-3 (1/3 non-null)  -> INDETERMINATE
//   - boundary pe == 15.0 exact            -> GREEN (inclusive)
//   - boundary pe == 20.0 exact            -> YELLOW (inclusive)
//   - ordering: 5 records, only latest 3 used (verifies maxBy date picker)
//   - ruleId assertion
//   - threshold label marker
//
// Style follows SizeRuleTest.kt (same package, AssertJ, JUnit 5, assertAll).
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-035-regola-pe-moderato-graham/TSK-080.md]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 6 — Rapporto P/E Moderato]
class Pe3yAvgRuleTest {

    private val rule = Pe3yAvgRule()

    // ------------------------------------------------------------------ GREEN

    @Test
    fun `GREEN when pe3yAvg is 12 point 5 — well below 15 threshold`() {
        // price=150, eps=[12,12,12], avgEps=12, pe=12.5
        val dataset = uniformEpsDataset(currentPrice = 150.0, eps = 12.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isCloseTo(12.5, within(0.001)) },
            { assertThat(result.threshold).contains("15") },
        )
    }

    // ------------------------------------------------------------------ YELLOW

    @Test
    fun `YELLOW when pe3yAvg is 16 point 67 — between 15 and 20`() {
        // price=150, eps=[9,9,9], avgEps=9, pe≈16.67
        val dataset = uniformEpsDataset(currentPrice = 150.0, eps = 9.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isCloseTo(16.667, within(0.001)) },
        )
    }

    // ------------------------------------------------------------------ RED

    @Test
    fun `RED when pe3yAvg is 25 — above 20 threshold`() {
        // price=200, eps=[8,8,8], avgEps=8, pe=25.0
        val dataset = uniformEpsDataset(currentPrice = 200.0, eps = 8.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isCloseTo(25.0, within(0.001)) },
        )
    }

    // ------------------------------------------------------------------ INDETERMINATE: negative avgEps

    @Test
    fun `INDETERMINATE when avgEps3y is negative — PE not meaningful`() {
        // eps=[-2,-2,-2], avgEps=-2 -> INDETERMINATE (avgEps <= 0)
        val dataset = uniformEpsDataset(currentPrice = 150.0, eps = -2.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // ------------------------------------------------------------------ INDETERMINATE: null price

    @Test
    fun `INDETERMINATE when currentPrice is null`() {
        val dataset = uniformEpsDataset(currentPrice = null, eps = 10.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // ------------------------------------------------------------------ INDETERMINATE: fewer than 3 income records

    @Test
    fun `INDETERMINATE when income has only 2 records — below 3-year requirement`() {
        val income = listOf(
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", eps = 10.0),
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = 10.0),
        )
        val dataset = makeDataset(currentPrice = 150.0, income = income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // ------------------------------------------------------------------ NOT_CALCULABLE: empty income

    @Test
    fun `NOT_CALCULABLE when income list is empty`() {
        val dataset = makeDataset(currentPrice = 150.0, income = emptyList())

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // ------------------------------------------------------------------ Edge: 1 null EPS in top-3 (2/3 non-null) — proceeds

    @Test
    fun `GREEN when 1 of 3 EPS is null — partial average over 2 non-null values`() {
        // top-3: eps=[15,null,15] -> nonNull=[15,15] -> avg=15, price=150, pe=10.0 -> GREEN
        val income = listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = 15.0),
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", eps = null),
            IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", eps = 15.0),
        )
        val dataset = makeDataset(currentPrice = 150.0, income = income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isCloseTo(10.0, within(0.001)) },
        )
    }

    // ------------------------------------------------------------------ Edge: 2 null EPS in top-3 (1/3 non-null) — INDETERMINATE

    @Test
    fun `INDETERMINATE when 2 of 3 top EPS are null — insufficient non-null count`() {
        // top-3: eps=[null,null,10] -> nonNull=[10] -> count 1 < MIN_NON_NULL_EPS(2) -> INDETERMINATE
        val income = listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = null),
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", eps = null),
            IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", eps = 10.0),
        )
        val dataset = makeDataset(currentPrice = 150.0, income = income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // ------------------------------------------------------------------ Edge boundary: pe == 15.0 -> GREEN

    @Test
    fun `GREEN at exactly pe3yAvg equals 15 point 0 — boundary inclusive`() {
        // price=150, eps=[10,10,10] -> avgEps=10, pe=15.0 -> GREEN (<=15)
        val dataset = uniformEpsDataset(currentPrice = 150.0, eps = 10.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isCloseTo(15.0, within(0.001)) },
        )
    }

    // ------------------------------------------------------------------ Edge boundary: pe == 20.0 -> YELLOW

    @Test
    fun `YELLOW at exactly pe3yAvg equals 20 point 0 — boundary inclusive`() {
        // price=200, eps=[10,10,10] -> avgEps=10, pe=20.0 -> YELLOW (<=20)
        val dataset = uniformEpsDataset(currentPrice = 200.0, eps = 10.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isCloseTo(20.0, within(0.001)) },
        )
    }

    // ------------------------------------------------------------------ Edge: ordering — 5 records, latest 3 must be chosen

    @Test
    fun `picks the 3 most recent fiscal years when dataset has 5 income records`() {
        // years 2020-2024. If we pick correctly (2024/2023/2022: eps=10 -> pe=15 -> GREEN).
        // If we accidentally picked the 3 oldest (2020/2021/2022 with eps=1) we'd get pe=150 -> RED.
        // This validates descending sort + take(3).
        val income = listOf(
            IncomeStatementDto(date = "2020-12-31", calendarYear = "2020", eps = 1.0),
            IncomeStatementDto(date = "2021-12-31", calendarYear = "2021", eps = 1.0),
            IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", eps = 10.0),
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", eps = 10.0),
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = 10.0),
        )
        val dataset = makeDataset(currentPrice = 150.0, income = income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            // pe = 150 / 10 = 15.0 (only if correct years were picked)
            { assertThat(result.observedValue).isCloseTo(15.0, within(0.001)) },
        )
    }

    @Test
    fun `picks the 3 most recent years also when list is in reverse chronological order`() {
        // Same as above but list is reversed — must not rely on list insertion order
        val income = listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = 10.0),
            IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", eps = 10.0),
            IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", eps = 10.0),
            IncomeStatementDto(date = "2021-12-31", calendarYear = "2021", eps = 1.0),
            IncomeStatementDto(date = "2020-12-31", calendarYear = "2020", eps = 1.0),
        )
        val dataset = makeDataset(currentPrice = 150.0, income = income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isCloseTo(15.0, within(0.001)) },
        )
    }

    // ------------------------------------------------------------------ ruleId check

    @Test
    fun `ruleId is PE_3Y_AVG across all signal outcomes`() {
        val green = rule.evaluate(uniformEpsDataset(currentPrice = 150.0, eps = 10.0))
        val yellow = rule.evaluate(uniformEpsDataset(currentPrice = 150.0, eps = 9.0))
        val red = rule.evaluate(uniformEpsDataset(currentPrice = 200.0, eps = 8.0))
        val indeterminate = rule.evaluate(uniformEpsDataset(currentPrice = null, eps = 10.0))
        val notCalculable = rule.evaluate(makeDataset(currentPrice = 150.0, income = emptyList()))

        assertAll(
            { assertThat(green.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(yellow.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(red.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(indeterminate.ruleId).isEqualTo("PE_3Y_AVG") },
            { assertThat(notCalculable.ruleId).isEqualTo("PE_3Y_AVG") },
        )
    }

    // ------------------------------------------------------------------ threshold label

    @Test
    fun `threshold label contains GREEN boundary marker 15`() {
        val result = rule.evaluate(uniformEpsDataset(currentPrice = 150.0, eps = 10.0))

        assertThat(result.threshold).contains("15")
    }

    @Test
    fun `threshold label contains YELLOW boundary marker 20`() {
        val result = rule.evaluate(uniformEpsDataset(currentPrice = 150.0, eps = 10.0))

        assertThat(result.threshold).contains("20")
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds a dataset with 3 income records, each with the same eps, and the given currentPrice.
     * Years: 2022/2023/2024. Sufficient to satisfy the 3-year requirement.
     */
    private fun uniformEpsDataset(currentPrice: Double?, eps: Double): FinancialDataset =
        makeDataset(
            currentPrice = currentPrice,
            income = listOf(
                IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = eps),
                IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", eps = eps),
                IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", eps = eps),
            ),
        )

    private fun makeDataset(
        currentPrice: Double?,
        income: List<IncomeStatementDto>,
    ): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = income,
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
            currentPrice = currentPrice,
        )
}
