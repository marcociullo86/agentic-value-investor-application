package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// Unit tests for DividendContinuityRule (TSK-086, US-037, EP-010).
//
// Covers US-037 Acceptance Criteria verbatim:
//   - consecutiveYears >= 20                          -> GREEN,          observedValue = years
//   - consecutiveYears in 15..19 (span >= 20y)        -> YELLOW,         observedValue = years
//   - consecutiveYears < 15 (span >= 20y)             -> RED,            observedValue = years
//   - dividends.isEmpty()                             -> INDETERMINATE,  observedValue = null
//   - totalSpanYears < 20                             -> INDETERMINATE,  observedValue = consecutiveYears
//   - all dates null/unparseable                      -> INDETERMINATE,  observedValue = null
//
// Business logic verified against DividendContinuityRule.kt comments:
//   - ruleId = "DIVIDEND_CONTINUITY_20Y"
//   - totalSpanYears = maxYear - minYear + 1
//   - consecutiveYears = streak counting back from mostRecentYear
//   - "at least 1 payment per year" — same-year deduplication via groupBy(year)
//   - INDETERMINATE (not NOT_CALCULABLE) on empty list per design note in rule
//   - totalSpanYears < 20 check fires BEFORE GREEN/YELLOW/RED classify
//
// All tests are pure unit tests: no Spring/WireMock/Mockito dependency.
// Double assertions use within(1e-9) to avoid IEEE 754 rounding (see EpsGrowthRuleTest).
//
// Integration test (GET /api/analysis/{ticker} includes DIVIDEND_CONTINUITY_20Y)
// is out-of-scope here; deferred to TSK-090 (E2E) as directed by TSK-086 §Scope 3.
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-086.md]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 4 — Regolarita' dei Dividendi]
class DividendContinuityRuleTest {

    private val rule = DividendContinuityRule()

    // -------------------------------------------------------------------------
    // AC: GREEN
    // -------------------------------------------------------------------------

    @Test
    fun `GREEN when exactly 20 consecutive years of dividends`() {
        // Span 2005-2024 (20 years), all present -> consecutiveYears = 20 -> GREEN.
        val dividends = consecutiveYears(2005, 2024)
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(20.0, within(1e-9)) },
            { assertThat(result.rationale).contains("20") },
        )
    }

    @Test
    fun `GREEN when 25 consecutive years — streak above threshold still GREEN`() {
        // Span 2000-2024 (25 years), all present -> consecutiveYears = 25 >= 20 -> GREEN.
        val dividends = consecutiveYears(2000, 2024)
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(25.0, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // AC: YELLOW
    // -------------------------------------------------------------------------

    @Test
    fun `YELLOW when 17 consecutive years with span at-least 20 years`() {
        // Span 2000-2024 (25y), gap in 2000-2006 -> most-recent streak = 2007-2024 = 18y.
        // Adjust: gap at 2007 -> streak from 2024 back: 2024,2023,...,2008 = 17 years.
        // Build: years 2000-2006 absent, 2008-2024 present.
        val years = (2008..2024).toList() // 17 years
        val dividends = years.flatMap { year ->
            listOf(divRecord("$year-03-15"), divRecord("$year-09-15"))
        }
        // Also add oldest record to push minYear to 2000 (span = 25y >= 20y).
        val withOldest = dividends + divRecord("2000-06-01")
        val result = rule.evaluate(dataset(withOldest))

        assertAll(
            { assertThat(result.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isEqualTo(17.0, within(1e-9)) },
        )
    }

    @Test
    fun `YELLOW when exactly 15 consecutive years — lower boundary of YELLOW range`() {
        // Span: oldest record 2000, streak 2010-2024 = 15 years, gap 2001-2009.
        val dividends = (2010..2024).map { year -> divRecord("$year-06-15") } +
            listOf(divRecord("2000-06-01"))
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isEqualTo(15.0, within(1e-9)) },
        )
    }

    @Test
    fun `YELLOW when exactly 19 consecutive years — upper boundary of YELLOW range`() {
        // Span: oldest record 2000, streak 2006-2024 = 19 years, gap 2001-2005.
        val dividends = (2006..2024).map { year -> divRecord("$year-06-15") } +
            listOf(divRecord("2000-06-01"))
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isEqualTo(19.0, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // AC: RED
    // -------------------------------------------------------------------------

    @Test
    fun `RED when span at-least 20 years but current streak only 12 years (gap in the middle)`() {
        // Span: 2000-2024 (25y). Present: 2000-2012 and 2013-2024 with 2012 missing.
        // Wait — need gap so streak < 15.
        // Present: 2000-2011 (old block), 2013-2024 (recent block). Gap at 2012.
        // mostRecentYear=2024, streak: 2024,2023,...,2013 = 12 years. -> RED.
        val dividends = (2000..2011).map { year -> divRecord("$year-06-15") } +
            (2013..2024).map { year -> divRecord("$year-06-15") }
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isEqualTo(12.0, within(1e-9)) },
        )
    }

    @Test
    fun `RED when span at-least 20 years but recent streak only 5 years`() {
        // Span: 2000-2024 (25y). Only 2020-2024 present plus 2000 anchor.
        val dividends = (2020..2024).map { year -> divRecord("$year-03-15") } +
            listOf(divRecord("2000-06-01"))
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isEqualTo(5.0, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // AC: INDETERMINATE — empty list
    // -------------------------------------------------------------------------

    @Test
    fun `INDETERMINATE when dividends list is empty`() {
        val result = rule.evaluate(dataset(emptyList()))

        assertAll(
            { assertThat(result.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // -------------------------------------------------------------------------
    // AC: INDETERMINATE — span too short (< 20 years)
    // -------------------------------------------------------------------------

    @Test
    fun `INDETERMINATE when span only 10 years even if all consecutive (too short for Graham 20y)`() {
        // Span 2015-2024 = 10 years. Streak = 10 years. totalSpanYears=10 < 20 -> INDETERMINATE.
        val dividends = consecutiveYears(2015, 2024)
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            // observedValue is set (consecutiveYears=10) even for INDETERMINATE on short span
            { assertThat(result.observedValue).isNotNull() },
        )
    }

    @Test
    fun `INDETERMINATE when span is exactly 19 years (one year below threshold)`() {
        // Span 2006-2024 = 19 years. All consecutive. totalSpanYears=19 < 20 -> INDETERMINATE.
        val dividends = consecutiveYears(2006, 2024)
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
        )
    }

    // -------------------------------------------------------------------------
    // AC: INDETERMINATE — all dates null
    // -------------------------------------------------------------------------

    @Test
    fun `INDETERMINATE when all dividend records have null date`() {
        val dividends = listOf(
            DividendRecord(date = null, dividend = 0.25),
            DividendRecord(date = null, dividend = 0.25),
            DividendRecord(date = null, dividend = 0.25),
            DividendRecord(date = null, dividend = 0.25),
            DividendRecord(date = null, dividend = 0.25),
        )
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `INDETERMINATE when all dividend records have malformed date strings`() {
        val dividends = listOf(
            DividendRecord(date = "not-a-date", dividend = 0.25),
            DividendRecord(date = "2024/08/12", dividend = 0.25),  // wrong separator
            DividendRecord(date = "12-08-2024", dividend = 0.25),  // wrong order
            DividendRecord(date = "", dividend = 0.25),
            DividendRecord(date = "INVALID", dividend = 0.25),
        )
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // -------------------------------------------------------------------------
    // Edge: multiple payments in same year = 1 year (no double-counting)
    // -------------------------------------------------------------------------

    @Test
    fun `multiple quarterly payments in same year count as only one year in streak`() {
        // 4 quarterly records per year for 20 years -> 80 records total,
        // but consecutiveYears must be 20, not 80.
        val dividends = (2005..2024).flatMap { year ->
            listOf(
                divRecord("$year-02-15"),
                divRecord("$year-05-15"),
                divRecord("$year-08-15"),
                divRecord("$year-11-15"),
            )
        }
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(20.0, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // Edge: gap in most-recent year — streak counts from max present year
    // -------------------------------------------------------------------------

    @Test
    fun `streak starts from max year present in data, not from current system year`() {
        // Series 2000-2023 (no 2024). totalSpanYears = 2023-2000+1 = 24 >= 20.
        // consecutiveYears = 24 (all present from 2000-2023). Should be GREEN.
        val dividends = consecutiveYears(2000, 2023)
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(24.0, within(1e-9)) },
        )
    }

    @Test
    fun `streak stops at first gap from most recent year downward`() {
        // Series 2005-2024 with 2020 absent. mostRecentYear=2024.
        // Streak: 2024, 2023, 2022, 2021 -> stops (2020 absent) -> consecutiveYears=4.
        // totalSpanYears = 2024-2005+1 = 20 >= 20. -> RED (4 < 15).
        val dividends = ((2005..2019) + (2021..2024)).map { year -> divRecord("$year-06-15") }
        val result = rule.evaluate(dataset(dividends))

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isEqualTo(4.0, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // ruleId invariant across all signal outcomes
    // -------------------------------------------------------------------------

    @Test
    fun `ruleId is DIVIDEND_CONTINUITY_20Y for all signal outcomes`() {
        val green = rule.evaluate(dataset(consecutiveYears(2005, 2024)))
        val yellow = rule.evaluate(dataset(
            (2010..2024).map { divRecord("$it-06-15") } + listOf(divRecord("2000-06-01"))
        ))
        val red = rule.evaluate(dataset(
            (2000..2011).map { divRecord("$it-06-15") } + (2013..2024).map { divRecord("$it-06-15") }
        ))
        val indeterminate = rule.evaluate(dataset(emptyList()))
        val indeterminateShortSpan = rule.evaluate(dataset(consecutiveYears(2015, 2024)))

        assertAll(
            { assertThat(green.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(yellow.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(red.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(indeterminate.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
            { assertThat(indeterminateShortSpan.ruleId).isEqualTo("DIVIDEND_CONTINUITY_20Y") },
        )
    }

    // -------------------------------------------------------------------------
    // threshold label contains required markers
    // -------------------------------------------------------------------------

    @Test
    fun `threshold label contains 20 and 15 markers and signal words`() {
        val result = rule.evaluate(dataset(consecutiveYears(2005, 2024)))

        assertAll(
            { assertThat(result.threshold).contains("20") },
            { assertThat(result.threshold).contains("15") },
            { assertThat(result.threshold).contains("GREEN") },
            { assertThat(result.threshold).contains("YELLOW") },
            { assertThat(result.threshold).contains("RED") },
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a DividendRecord with a valid ISO date. Single annual payment.
     */
    private fun divRecord(date: String, dividend: Double = 0.25): DividendRecord =
        DividendRecord(
            date = date,
            dividend = dividend,
            adjDividend = dividend,
            frequency = "Quarterly",
        )

    /**
     * Builds one annual payment per year for the closed range [fromYear, toYear].
     */
    private fun consecutiveYears(fromYear: Int, toYear: Int): List<DividendRecord> =
        (fromYear..toYear).map { year -> divRecord("$year-06-15") }

    /**
     * Wraps a dividend list in a minimal FinancialDataset for the rule under test.
     */
    private fun dataset(dividends: List<DividendRecord>): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = emptyList(),
            balance = emptyList(),
            cashFlow = emptyList(),
            keyMetrics = emptyList(),
            dataSnapshotAt = Instant.parse("2024-12-31T00:00:00Z"),
            dividends = dividends,
        )
}
