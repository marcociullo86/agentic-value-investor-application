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

// Unit tests for EpsGrowthRule (TSK-078, US-034, EP-010).
//
// Covers US-034 Acceptance Criteria verbatim:
//   - growth >= 33%                          -> GREEN,         observedValue = growth ratio
//   - 0% <= growth < 33%                     -> YELLOW,        observedValue = growth ratio
//   - growth < 0%                            -> RED,           observedValue = growth ratio
//   - avgEpsInitial (anni 1-3) <= 0          -> INDETERMINATE, observedValue = null
//   - income.size < 10                       -> INDETERMINATE, observedValue = null
//   - income list empty                      -> NOT_CALCULABLE, observedValue = null
//
// Plus bonus edges mandated by TSK-078 instructions:
//   - 1 null EPS in triennale iniziale (2/3 non-null) -> proceeds normally (TSK-077 §Note)
//   - 2 null EPS in triennale iniziale (1/3 non-null) -> INDETERMINATE
//   - boundary growth = 0.33 exact           -> GREEN  (>= 0.33 inclusive)
//   - boundary growth = 0.0 exact            -> YELLOW (>= 0.0, < 0.33)
//   - ruleId = "EPS_GROWTH_10Y" for all signal outcomes
//   - threshold label contains GREEN/YELLOW/RED markers
//
// NOTE — Integration test (GET /api/analysis/{ticker} WireMock) is out-of-scope here.
// Deferred to TSK-090 (E2E) as per TSK-078 scope.
// All tests here are pure unit tests: no Spring/WireMock/Mockito dependency.
//
// Assertion library: AssertJ (already present — no new deps).
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-034-regola-crescita-eps-graham/TSK-078.md]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 5 — Crescita degli Utili]
// [^src: wiki/concepts/value-investing-rule-engine.md §Redditività]
class EpsGrowthRuleTest {

    private val rule = EpsGrowthRule()

    // --- AC1: GREEN — growth >= 33% ---

    @Test
    fun `GREEN when EPS grows 40 percent (avgInitial 1 point 0, avgFinal 1 point 4)`() {
        // EPS = [1, 1, 1,  *, *, *, *,  1.4, 1.4, 1.4] (ASC = year 1..10)
        // avgEpsInitial = (1.0+1.0+1.0)/3 = 1.0
        // avgEpsFinal   = (1.4+1.4+1.4)/3 = 1.4
        // growth = (1.4-1.0)/1.0 = 0.40 >= 0.33 -> GREEN
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.4, 1.4, 1.4))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(0.4, org.assertj.core.api.Assertions.within(1e-9)) },
            { assertThat(result.threshold).contains("GREEN") },
        )
    }

    @Test
    fun `GREEN when EPS grows using TSK-078 canonical fixture (1_1_0p9 initial, 1p4_1p5_1p3 final)`() {
        // Canonical fixture from TSK-078 §Cosa fare:
        //   EPS = [1.0, 1.1, 0.9, *, *, *, *, 1.4, 1.5, 1.3]
        //   avgEpsInitial = (1.0+1.1+0.9)/3 = 1.0
        //   avgEpsFinal   = (1.4+1.5+1.3)/3 = 1.4
        //   growth = (1.4-1.0)/1.0 = 0.40 -> GREEN
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.1, 0.9), finalEps = listOf(1.4, 1.5, 1.3))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(0.4, org.assertj.core.api.Assertions.within(1e-9)) },
        )
    }

    // --- AC2: YELLOW — 0% <= growth < 33% ---

    @Test
    fun `YELLOW when EPS grows 20 percent (avgInitial 1 point 0, avgFinal 1 point 2)`() {
        // avgEpsInitial=1.0, avgEpsFinal=1.2, growth=0.20 -> YELLOW [0.0, 0.33)
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.2, 1.2, 1.2))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isEqualTo(0.2, org.assertj.core.api.Assertions.within(1e-9)) },
        )
    }

    @Test
    fun `YELLOW using TSK-078 canonical yellow fixture (1_1_1 initial, 1p1_1p2_1p1 final)`() {
        // Canonical fixture from TSK-078 §Cosa fare:
        //   EPS = [1.0, 1.0, 1.0, *, *, *, *, 1.1, 1.2, 1.1]
        //   avgEpsInitial = 1.0, avgEpsFinal = (1.1+1.2+1.1)/3 ≈ 1.133
        //   growth ≈ +13.3% -> YELLOW
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.1, 1.2, 1.1))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isNotNull() },
            { assertThat(result.observedValue!!).isGreaterThanOrEqualTo(0.0) },
            { assertThat(result.observedValue!!).isLessThan(0.33) },
        )
    }

    // --- AC3: RED — growth < 0% ---

    @Test
    fun `RED when EPS declines 10 percent (avgInitial 1 point 0, avgFinal 0 point 9)`() {
        // avgEpsInitial=1.0, avgEpsFinal=0.9, growth=-0.10 -> RED
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(0.9, 0.9, 0.9))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isEqualTo(-0.1, org.assertj.core.api.Assertions.within(1e-9)) },
        )
    }

    @Test
    fun `RED using TSK-078 canonical red fixture (1_1_1 initial, 0p8_0p9_0p7 final)`() {
        // Canonical fixture from TSK-078 §Cosa fare:
        //   EPS = [1.0, 1.0, 1.0, *, *, *, *, 0.8, 0.9, 0.7]
        //   avgEpsInitial=1.0, avgEpsFinal=(0.8+0.9+0.7)/3=0.8, growth=-20% -> RED
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(0.8, 0.9, 0.7))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNotNull() },
            { assertThat(result.observedValue!!).isLessThan(0.0) },
        )
    }

    // --- AC4: INDETERMINATE — avgEpsInitial <= 0 ---

    @Test
    fun `INDETERMINATE when avgEpsInitial is negative (denominatore non significativo)`() {
        // avgEpsInitial = (-0.5 + -0.5 + -0.5) / 3 = -0.5 <= 0 -> INDETERMINATE (NOT RED)
        // Rationale: growth% perde significato matematico su baseline negativa.
        val dataset = tenYearDataset(initialEps = listOf(-0.5, -0.5, -0.5), finalEps = listOf(1.0, 1.0, 1.0))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `INDETERMINATE when avgEpsInitial is exactly zero (boundary case)`() {
        // avgEpsInitial = 0.0 -> division undefined -> INDETERMINATE (uses <= 0 check)
        val dataset = tenYearDataset(initialEps = listOf(0.0, 0.0, 0.0), finalEps = listOf(1.0, 1.0, 1.0))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC5: INDETERMINATE — income.size < 10 ---

    @Test
    fun `INDETERMINATE when only 8 fiscal year records are available (less than 10)`() {
        val income = (2017..2024).map { year ->
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), eps = 1.0)
        }
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("8") },
        )
    }

    @Test
    fun `INDETERMINATE when only 1 fiscal year record is available`() {
        val income = listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = 2.0),
        )
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC6: NOT_CALCULABLE — income list empty ---

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
            { assertThat(result.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    // --- AC7 (edge): 1 null EPS in triennale iniziale — proceeds on 2/3 non-null ---

    @Test
    fun `GREEN when 1 of 3 initial-triennale EPS values is null (2 out of 3 non-null, proceeds)`() {
        // TSK-077 §Note: "se la triennale è calcolabile su 2 soli anni, procedere ugualmente".
        // initialEps = [1.0, null, 1.0] -> non-null list = [1.0, 1.0] -> avg = 1.0 (2/3 ok)
        // finalEps   = [1.4, 1.4, 1.4] -> avg = 1.4
        // growth = 0.40 -> GREEN
        val income = buildTenYearIncome(
            initialEps = listOf(1.0, null, 1.0),
            midEps = listOf(2.0, 2.0, 2.0, 2.0),
            finalEps = listOf(1.4, 1.4, 1.4),
        )
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(0.4, org.assertj.core.api.Assertions.within(1e-9)) },
        )
    }

    // --- AC8 (edge): 2 null EPS in triennale iniziale — only 1/3 non-null -> INDETERMINATE ---

    @Test
    fun `INDETERMINATE when 2 of 3 initial-triennale EPS values are null (only 1 out of 3 non-null)`() {
        // initialEps = [1.0, null, null] -> non-null list = [1.0] -> size=1 < MIN(2) -> INDETERMINATE
        val income = buildTenYearIncome(
            initialEps = listOf(1.0, null, null),
            midEps = listOf(2.0, 2.0, 2.0, 2.0),
            finalEps = listOf(1.4, 1.4, 1.4),
        )
        val dataset = buildDataset(income)

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("triennale iniziale") },
        )
    }

    // --- AC9 (edge): boundary growth values ---

    @Test
    fun `GREEN at boundary growth of exactly 0 point 33 (lower bound of GREEN inclusive)`() {
        // avgEpsInitial=1.0, avgEpsFinal=1.33 -> growth=0.33 exactly -> GREEN (>= 0.33)
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.33, 1.33, 1.33))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isEqualTo(0.33, org.assertj.core.api.Assertions.within(1e-9)) },
        )
    }

    @Test
    fun `YELLOW at boundary growth of exactly 0 point 0 (lower bound of YELLOW range inclusive)`() {
        // avgEpsInitial=1.0, avgEpsFinal=1.0 -> growth=0.0 exactly -> YELLOW [0.0, 0.33)
        val dataset = tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.0, 1.0, 1.0))

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue).isEqualTo(0.0) },
        )
    }

    // --- AC10: ruleId is stable for all signal outcomes ---

    @Test
    fun `ruleId is EPS_GROWTH_10Y for all signal outcomes`() {
        val green = rule.evaluate(tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.4, 1.4, 1.4)))
        val yellow = rule.evaluate(tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.2, 1.2, 1.2)))
        val red = rule.evaluate(tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(0.9, 0.9, 0.9)))
        val indeterminate = rule.evaluate(
            buildDataset(
                listOf(IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = 1.0))
            )
        )
        val notCalculable = rule.evaluate(buildDataset(emptyList()))

        assertAll(
            { assertThat(green.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(yellow.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(red.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(indeterminate.ruleId).isEqualTo("EPS_GROWTH_10Y") },
            { assertThat(notCalculable.ruleId).isEqualTo("EPS_GROWTH_10Y") },
        )
    }

    // --- AC11: threshold label contains GREEN/YELLOW/RED markers ---

    @Test
    fun `threshold label contains GREEN, YELLOW, and RED markers`() {
        val result = rule.evaluate(tenYearDataset(initialEps = listOf(1.0, 1.0, 1.0), finalEps = listOf(1.4, 1.4, 1.4)))

        assertAll(
            { assertThat(result.threshold).contains("GREEN") },
            { assertThat(result.threshold).contains("YELLOW") },
            { assertThat(result.threshold).contains("RED") },
        )
    }

    // --- helpers ---

    /**
     * Builds a FinancialDataset with exactly 10 fiscal years (2015–2024).
     *
     * [initialEps] = EPS for years 1-3 (2015–2017, oldest / baseline).
     * [finalEps]   = EPS for years 8-10 (2022–2024, newest / finale).
     * Middle years 4-7 (2018–2021) are given a neutral EPS of 2.0 so they do not
     * affect the triennial averages used by the rule.
     *
     * The rule sorts DESC by date then takes top-10 and reverses to ASC, so
     * date ordering here is the canonical anchor.
     */
    private fun tenYearDataset(initialEps: List<Double?>, finalEps: List<Double?>): FinancialDataset {
        val income = buildTenYearIncome(
            initialEps = initialEps,
            midEps = listOf(2.0, 2.0, 2.0, 2.0),
            finalEps = finalEps,
        )
        return buildDataset(income)
    }

    /**
     * Constructs the 10 IncomeStatementDto records with explicit EPS per window.
     * [initialEps] = years 2015-2017 (indices 0-2 ASC = anni 1-3).
     * [midEps]     = years 2018-2021 (indices 3-6 ASC = anni 4-7, exactly 4 items).
     * [finalEps]   = years 2022-2024 (indices 7-9 ASC = anni 8-10).
     */
    private fun buildTenYearIncome(
        initialEps: List<Double?>,
        midEps: List<Double?>,
        finalEps: List<Double?>,
    ): List<IncomeStatementDto> {
        require(initialEps.size == 3) { "initialEps must have exactly 3 elements" }
        require(midEps.size == 4) { "midEps must have exactly 4 elements" }
        require(finalEps.size == 3) { "finalEps must have exactly 3 elements" }

        val years = (2015..2024).toList()
        val allEps = initialEps + midEps + finalEps  // 10 total

        return years.zip(allEps).map { (year, eps) ->
            IncomeStatementDto(
                date = "$year-12-31",
                calendarYear = year.toString(),
                eps = eps,
            )
        }
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
