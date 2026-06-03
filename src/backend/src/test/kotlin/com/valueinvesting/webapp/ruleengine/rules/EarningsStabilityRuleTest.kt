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

// Unit tests for EarningsStabilityRule (TSK-076, US-033, EP-010).
//
// Covers US-033 Acceptance Criteria verbatim:
//   - 10/10 anni con netIncome > 0                      -> GREEN, observedValue=10.0
//   -  9/10 anni positivi (1 anno perdita/n.d.)         -> YELLOW, observedValue=9.0, rationale contiene anno
//   - ≤8/10 anni positivi (2+ perdite)                  -> RED, observedValue=8.0
//   - < 10 record disponibili                           -> INDETERMINATE, observedValue=null
//   - income lista vuota                                -> NOT_CALCULABLE, observedValue=null
//
// Plus bonus edges mandated by TSK-076:
//   - netIncome=null     -> YELLOW (trattato come non-positivo; rationale contiene "n/d")
//   - netIncome=0.0      -> contato come non-positivo (rule usa strict > 0)
//   - 12 record (> 10)   -> presi i 10 più recenti, i 2 più vecchi scartati -> GREEN
//   - ruleId = "EARNINGS_STABILITY_10Y" per tutti gli esiti
//
// NOTE — Integration test (GET /api/analysis/{ticker} WireMock) è out-of-scope qui.
// Demandato a TSK-090 (E2E finale) come da istruzioni TSK-076 §drift corrections.
// I test presenti sono pure unit test: nessuna dipendenza Spring/WireMock/Mockito.
//
// Assertion library: AssertJ (already present — no new deps).
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-033-regola-stabilita-utili-graham/TSK-076.md]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 3 — Stabilita' degli Utili]
// [^src: wiki/concepts/value-investing-rule-engine.md §Redditività]
class EarningsStabilityRuleTest {

    private val rule = EarningsStabilityRule()

    // --- AC1: GREEN — 10/10 anni positivi ---

    @Test
    fun `GREEN when all 10 fiscal years have positive netIncome`() {
        val dataset = tenYearDataset(losses = emptyList())

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("EARNINGS_STABILITY_10Y") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(10.0) },
            { assertThat(result.threshold).contains("10/10") },
        )
    }

    // --- AC2: YELLOW — 9/10 anni positivi (1 anno di perdita) ---

    @Test
    fun `YELLOW when 9 of 10 fiscal years are positive (1 loss year)`() {
        // Year 2020 has a loss.
        val dataset = tenYearDataset(losses = listOf(2020))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isEqualTo(9.0) },
            { assertThat(result.rationale).contains("2020") },
            { assertThat(result.rationale).contains("9/10") },
        )
    }

    // --- AC3: RED — 8/10 anni positivi (2 anni di perdita) ---

    @Test
    fun `RED when only 8 of 10 fiscal years are positive (2 loss years)`() {
        val dataset = tenYearDataset(losses = listOf(2020, 2019))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isEqualTo(8.0) },
            { assertThat(result.rationale).contains("2020") },
            { assertThat(result.rationale).contains("2019") },
        )
    }

    // --- AC4: INDETERMINATE — serie storica < 10 record ---

    @Test
    fun `INDETERMINATE when only 8 fiscal year records are available (less than 10)`() {
        val income = (2017..2024).map { year ->
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), netIncome = 1_000_000.0)
        }
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("8 esercizi disponibili") },
        )
    }

    // --- AC5: NOT_CALCULABLE — income lista vuota ---

    @Test
    fun `NOT_CALCULABLE when income list is empty`() {
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
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- Bonus: netIncome=null contato come non-positivo, rationale contiene "n/d" ---

    @Test
    fun `YELLOW when 1 of 10 fiscal years has null netIncome (treated as non-positive, labeled n slash d)`() {
        // 9 records with positive netIncome + 1 record with netIncome=null.
        val income = (2015..2023).map { year ->
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), netIncome = 1_000_000.0)
        } + listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = null),
        )
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isEqualTo(9.0) },
            { assertThat(result.rationale).contains("n/d") },
        )
    }

    // --- Bonus: netIncome=0.0 contato come non-positivo (rule usa strict > 0) ---

    @Test
    fun `zero netIncome is counted as non-positive because rule uses strict greater-than`() {
        // 9 records positive + 1 record with netIncome=0.0 (not null, not negative — but not > 0).
        // Per PATTERN §7 r.13: 0.0 is real data, not a placeholder. Rule uses netIncome > 0, so
        // zero is counted as "not positive" and the result must be YELLOW.
        val income = (2015..2023).map { year ->
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), netIncome = 1_000_000.0)
        } + listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = 0.0),
        )
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isEqualTo(9.0) },
            { assertThat(result.rationale).contains("2024") },
        )
    }

    // --- Bonus: edge ordering — 12 record presenti, presi i 10 più recenti ---

    @Test
    fun `GREEN when 12 records present but top-10 most-recent all positive (2 oldest discarded)`() {
        // 12 fiscal years: 2013-2024. All have positive netIncome.
        // The two oldest (2013, 2014) are beyond the 10-year window and must be discarded.
        // Result: 10/10 positive -> GREEN.
        val income = (2013..2024).map { year ->
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), netIncome = 1_000_000.0)
        }
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(10.0) },
        )
    }

    @Test
    fun `RED not triggered when 2 oldest of 12 records are losses (outside the 10-year window)`() {
        // 12 records: 2013 and 2014 have negative netIncome; 2015-2024 all positive.
        // The rule takes the 10 most recent (2015-2024), which are all GREEN -> GREEN outcome.
        // This verifies the "scarta i 2 più vecchi" ordering requirement.
        val income = listOf(
            IncomeStatementDto(date = "2013-12-31", calendarYear = "2013", netIncome = -5_000_000.0),
            IncomeStatementDto(date = "2014-12-31", calendarYear = "2014", netIncome = -3_000_000.0),
        ) + (2015..2024).map { year ->
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), netIncome = 1_000_000.0)
        }
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(10.0) },
        )
    }

    // --- Bonus: ruleId stabile per tutti gli esiti ---

    @Test
    fun `ruleId is EARNINGS_STABILITY_10Y for all signal outcomes`() {
        val green = rule.evaluate(tenYearDataset(losses = emptyList()))
        val yellow = rule.evaluate(tenYearDataset(losses = listOf(2020)))
        val red = rule.evaluate(tenYearDataset(losses = listOf(2020, 2019)))
        val indeterminate = rule.evaluate(buildDataset(
            listOf(IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = 1_000_000.0))
        ))
        val notCalculable = rule.evaluate(buildDataset(emptyList()))

        assertAll(
            { assertThat(green.ruleId).isEqualTo("EARNINGS_STABILITY_10Y") },
            { assertThat(yellow.ruleId).isEqualTo("EARNINGS_STABILITY_10Y") },
            { assertThat(red.ruleId).isEqualTo("EARNINGS_STABILITY_10Y") },
            { assertThat(indeterminate.ruleId).isEqualTo("EARNINGS_STABILITY_10Y") },
            { assertThat(notCalculable.ruleId).isEqualTo("EARNINGS_STABILITY_10Y") },
        )
    }

    // --- threshold label ---

    @Test
    fun `threshold label contains GREEN and YELLOW markers`() {
        val result = rule.evaluate(tenYearDataset(losses = emptyList()))

        assertAll(
            { assertThat(result.threshold).contains("GREEN") },
            { assertThat(result.threshold).contains("YELLOW") },
            { assertThat(result.threshold).contains("RED") },
        )
    }

    // --- helpers ---

    /**
     * Builds a dataset with exactly 10 fiscal years (2015–2024).
     * Years in [losses] are given netIncome = -1_000_000.0; all others get +1_000_000.0.
     */
    private fun tenYearDataset(losses: List<Int>): FinancialDataset {
        val income = (2015..2024).map { year ->
            val ni = if (year in losses) -1_000_000.0 else 1_000_000.0
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), netIncome = ni)
        }
        return buildDataset(income)
    }

    private fun buildDataset(income: List<IncomeStatementDto>): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = income,
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
        )
}
