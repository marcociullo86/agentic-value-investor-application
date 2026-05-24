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

// Unit tests for PbLatestRule (TSK-082, US-036, EP-010).
//
// Covers US-036 Acceptance Criteria verbatim:
//   - pb <= 1.5            -> GREEN
//   - pb in (1.5, 3.0]    -> YELLOW
//   - pb > 3.0             -> RED
//   - bvps <= 0 or null    -> INDETERMINATE
//   - currentPrice null    -> INDETERMINATE
//   - keyMetrics empty     -> NOT_CALCULABLE
//
// Plus bonus edges mandated by TSK-082:
//   - pb = 1.5 exact       -> GREEN  (boundary inclusive)
//   - pb = 3.0 exact       -> YELLOW (boundary inclusive)
//   - multi-year picker    -> latest fiscal year selected by max(date)
//   - date-null fallback   -> calendarYear used for ordering
//   - ruleId assertion
//   - threshold label markers GREEN/YELLOW/RED
//
// Shape follows SizeRuleTest.kt (same package, same assertion library).
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-036-regola-pb-moderato-graham/TSK-082.md]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 7 — Rapporto P/Book Moderato]
class PbLatestRuleTest {

    private val rule = PbLatestRule()

    // --- AC: GREEN ---

    @Test
    fun `GREEN when price 15 and bvps 12 gives pb 1 point 25`() {
        // pb = 15 / 12.0 = 1.25 <= 1.5 -> GREEN
        val dataset = singleYearDataset(currentPrice = 15.0, bookValuePerShare = 12.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("PB_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isCloseTo(1.25, within(0.001)) },
        )
    }

    // --- AC: YELLOW ---

    @Test
    fun `YELLOW when price 30 and bvps 12 gives pb 2 point 5`() {
        // pb = 30 / 12.0 = 2.5, in (1.5, 3.0] -> YELLOW
        val dataset = singleYearDataset(currentPrice = 30.0, bookValuePerShare = 12.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("PB_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isCloseTo(2.5, within(0.001)) },
        )
    }

    // --- AC: RED ---

    @Test
    fun `RED when price 60 and bvps 12 gives pb 5 point 0`() {
        // pb = 60 / 12.0 = 5.0 > 3.0 -> RED
        val dataset = singleYearDataset(currentPrice = 60.0, bookValuePerShare = 12.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("PB_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isCloseTo(5.0, within(0.001)) },
        )
    }

    // --- AC: INDETERMINATE — negative equity ---

    @Test
    fun `INDETERMINATE when bvps is negative (negative equity)`() {
        val dataset = singleYearDataset(currentPrice = 30.0, bookValuePerShare = -5.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC: INDETERMINATE — zero equity ---

    @Test
    fun `INDETERMINATE when bvps is 0 point 0 (zero equity not interpretable as value signal)`() {
        val dataset = singleYearDataset(currentPrice = 30.0, bookValuePerShare = 0.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC: INDETERMINATE — bvps null ---

    @Test
    fun `INDETERMINATE when bvps is null (schema stable field absent)`() {
        val dataset = singleYearDataset(currentPrice = 30.0, bookValuePerShare = null)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC: INDETERMINATE — currentPrice null ---

    @Test
    fun `INDETERMINATE when currentPrice is null`() {
        val dataset = singleYearDataset(currentPrice = null, bookValuePerShare = 12.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC: NOT_CALCULABLE — empty keyMetrics ---

    @Test
    fun `NOT_CALCULABLE when keyMetrics list is empty`() {
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
            currentPrice = 30.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.signal).isNotEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- Boundary: pb = 1.5 exact -> GREEN (inclusive upper boundary) ---

    @Test
    fun `GREEN at exactly pb 1 point 5 (boundary inclusive)`() {
        // price = 18.0, bvps = 12.0 -> pb = 1.5 exactly
        val dataset = singleYearDataset(currentPrice = 18.0, bookValuePerShare = 12.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isCloseTo(1.5, within(0.001)) },
        )
    }

    // --- Boundary: pb = 3.0 exact -> YELLOW (inclusive upper boundary) ---

    @Test
    fun `YELLOW at exactly pb 3 point 0 (boundary inclusive — not RED)`() {
        // price = 36.0, bvps = 12.0 -> pb = 3.0 exactly
        val dataset = singleYearDataset(currentPrice = 36.0, bookValuePerShare = 12.0)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isCloseTo(3.0, within(0.001)) },
        )
    }

    // --- Edge: multi-year picker selects the latest date ---

    @Test
    fun `latest fiscal year selected when dataset has multiple key-metrics rows in ascending order`() {
        // 2022: bvps=20.0 -> would be GREEN (pb=15/20=0.75)
        // 2023: bvps=6.0  -> would be RED   (pb=15/6=2.5, actually YELLOW)
        // 2024: bvps=4.0  -> pb = 15/4 = 3.75 -> RED
        // Latest date "2024-12-31" must win.
        val keyMetrics = listOf(
            KeyMetricsDto(date = "2022-12-31", calendarYear = "2022", bookValuePerShare = 20.0),
            KeyMetricsDto(date = "2023-12-31", calendarYear = "2023", bookValuePerShare = 6.0),
            KeyMetricsDto(date = "2024-12-31", calendarYear = "2024", bookValuePerShare = 4.0),
        )
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = keyMetrics,
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
            currentPrice = 15.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            // pb = 15 / 4.0 = 3.75 -> RED (2024 bvps used, not 2022 or 2023)
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isCloseTo(3.75, within(0.001)) },
        )
    }

    @Test
    fun `latest fiscal year selected also when key-metrics list is in reverse chronological order`() {
        // Same scenario, list reversed — maxByOrNull must not rely on list order.
        val keyMetrics = listOf(
            KeyMetricsDto(date = "2024-12-31", calendarYear = "2024", bookValuePerShare = 4.0),
            KeyMetricsDto(date = "2023-12-31", calendarYear = "2023", bookValuePerShare = 6.0),
            KeyMetricsDto(date = "2022-12-31", calendarYear = "2022", bookValuePerShare = 20.0),
        )
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = keyMetrics,
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
            currentPrice = 15.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isCloseTo(3.75, within(0.001)) },
        )
    }

    // --- Edge: date null fallback to calendarYear for ordering ---

    @Test
    fun `calendarYear used as fallback when date field is null on all rows`() {
        // All rows have date=null; ordering falls back to calendarYear.
        // 2024 row has bvps=4.0 -> pb = 15/4 = 3.75 -> RED (latest calendarYear wins).
        val keyMetrics = listOf(
            KeyMetricsDto(date = null, calendarYear = "2022", bookValuePerShare = 20.0),
            KeyMetricsDto(date = null, calendarYear = "2023", bookValuePerShare = 6.0),
            KeyMetricsDto(date = null, calendarYear = "2024", bookValuePerShare = 4.0),
        )
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = keyMetrics,
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
            currentPrice = 15.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isCloseTo(3.75, within(0.001)) },
        )
    }

    // --- ruleId consistent across all signal outcomes ---

    @Test
    fun `ruleId is PB_LATEST for all signal outcomes`() {
        val green = rule.evaluate(singleYearDataset(currentPrice = 15.0, bookValuePerShare = 12.0))
        val yellow = rule.evaluate(singleYearDataset(currentPrice = 30.0, bookValuePerShare = 12.0))
        val red = rule.evaluate(singleYearDataset(currentPrice = 60.0, bookValuePerShare = 12.0))
        val indeterminate = rule.evaluate(singleYearDataset(currentPrice = null, bookValuePerShare = 12.0))
        val notCalculable = rule.evaluate(
            FinancialDataset(
                ticker = "TEST",
                income = emptyList<IncomeStatementDto>(),
                balance = emptyList<BalanceSheetDto>(),
                cashFlow = emptyList<CashFlowDto>(),
                keyMetrics = emptyList<KeyMetricsDto>(),
                dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
                currentPrice = 30.0,
            )
        )

        assertAll(
            { assertThat(green.ruleId).isEqualTo("PB_LATEST") },
            { assertThat(yellow.ruleId).isEqualTo("PB_LATEST") },
            { assertThat(red.ruleId).isEqualTo("PB_LATEST") },
            { assertThat(indeterminate.ruleId).isEqualTo("PB_LATEST") },
            { assertThat(notCalculable.ruleId).isEqualTo("PB_LATEST") },
        )
    }

    // --- threshold label contains GREEN / YELLOW / RED markers ---

    @Test
    fun `threshold label contains GREEN YELLOW and RED zone markers`() {
        val result = rule.evaluate(singleYearDataset(currentPrice = 15.0, bookValuePerShare = 12.0))

        assertAll(
            { assertThat(result.threshold).containsIgnoringCase("GREEN") },
            { assertThat(result.threshold).containsIgnoringCase("YELLOW") },
            { assertThat(result.threshold).containsIgnoringCase("RED") },
        )
    }

    // --- helper ---

    private fun singleYearDataset(
        currentPrice: Double?,
        bookValuePerShare: Double?,
        year: String = "2024",
    ): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = listOf(
                KeyMetricsDto(
                    date = "$year-12-31",
                    calendarYear = year,
                    bookValuePerShare = bookValuePerShare,
                ),
            ),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
            currentPrice = currentPrice,
        )
}
