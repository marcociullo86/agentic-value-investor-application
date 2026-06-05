package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// Unit tests for NcavLatestRule (TSK-318, US-096 AC, EP-023).
//
// Covers TSK-318 §Scenari obbligatori for NCAV_LATEST:
//   Scenario 1 GREEN:  ncavTotal > 0, ncavPerShare valorizzato.
//   Scenario 3 INDETERMINATE: totalCurrentAssets == null (missing balance).
//   Scenario 3 INDETERMINATE: sharesOutstanding null (missing shares).
//   Scenario 4 edge NCAV<=0: RED, ncavTotal <= 0.
//   ruleId assertion.
//   Typed subtype: output is RuleSignal.NcavLatest.
//
// US-096 Acceptance Criteria (ADR-029 §2):
//   - dati mancanti (balance sheet / shares) → INDETERMINATE
//   - ncavTotal > 0 → GREEN (calcolo riuscito; decisione spetta a NET_NET_RATIO)
//   - ncavTotal <= 0 → RED (passivo totale > attivo corrente)
//
// Idiomi: JUnit5 + AssertJ assertAll (stesso pattern di CurrentRatioRuleTest).
// Nessuna dipendenza Spring/Mockito — pure unit test.
//
// [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §2]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/TSK-318.md §Scenari]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/US-096.md §AC]
@Suppress("DEPRECATION")
class NcavLatestRuleTest {

    private val rule = NcavLatestRule()

    // =========================================================================
    // Scenario 1 — GREEN: ncavTotal > 0
    // =========================================================================

    @Test
    fun `GREEN when currentAssets exceed totalLiabilities — ncavTotal positive and ncavPerShare populated`() {
        // currentAssets=1_000_000, liabilities=600_000 → ncavTotal=400_000
        // shares=200_000 → ncavPerShare=2.0
        val dataset = dataset(
            currentAssets = 1_000_000.0,
            totalLiabilities = 600_000.0,
            sharesDil = 200_000.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("NCAV_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.NcavLatest
                assertThat(typed.ncavTotal).isNotNull()
                assertThat(typed.ncavTotal!!).isCloseTo(400_000.0, within(1e-6))
                assertThat(typed.ncavPerShare).isNotNull()
                assertThat(typed.ncavPerShare!!).isCloseTo(2.0, within(1e-6))
            },
        )
    }

    @Test
    fun `GREEN returns RuleSignal NcavLatest subtype`() {
        val dataset = dataset(
            currentAssets = 500_000.0,
            totalLiabilities = 100_000.0,
            sharesDil = 100_000.0,
        )

        val result = rule.evaluate(dataset)

        assertThat(result).isInstanceOf(RuleSignal.NcavLatest::class.java)
        assertThat(result.signal).isEqualTo(Signal.GREEN)
    }

    @Test
    fun `GREEN ncavPerShare equals ncavTotal divided by sharesOutstanding`() {
        // ncavTotal = 900_000 - 300_000 = 600_000; shares = 60_000 → per-share = 10.0
        val dataset = dataset(
            currentAssets = 900_000.0,
            totalLiabilities = 300_000.0,
            sharesDil = 60_000.0,
        )

        val result = rule.evaluate(dataset)
        val typed = result as RuleSignal.NcavLatest

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(typed.ncavPerShare!!).isCloseTo(10.0, within(1e-6)) },
        )
    }

    // =========================================================================
    // Scenario 3 — INDETERMINATE: balance sheet missing or null fields
    // =========================================================================

    @Test
    fun `INDETERMINATE when balance sheet list is empty`() {
        val dataset = datasetWithBalance(emptyList())

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("NCAV_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.NcavLatest
                assertThat(typed.ncavTotal).isNull()
                assertThat(typed.ncavPerShare).isNull()
            },
        )
    }

    @Test
    fun `INDETERMINATE when totalCurrentAssets is null — never coerced to zero (PATTERN §7 r13)`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = null,
                    totalLiabilities = 400_000.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.NcavLatest
                assertThat(typed.ncavTotal).isNull()
                assertThat(typed.ncavPerShare).isNull()
            },
        )
    }

    @Test
    fun `INDETERMINATE when totalLiabilities is null`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = null,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.INDETERMINATE)
    }

    // =========================================================================
    // Scenario 3 — INDETERMINATE: sharesOutstanding null
    // =========================================================================

    @Test
    fun `INDETERMINATE when sharesOutstanding not available (both weighted shares null)`() {
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = 600_000.0,
                ),
            ),
            income = listOf(
                IncomeStatementDto(
                    date = "2024-12-31",
                    weightedAverageShsOutDil = null,
                    weightedAverageShsOut = null,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.NcavLatest
                assertThat(typed.ncavTotal).isNull()
                assertThat(typed.ncavPerShare).isNull()
            },
            // Rationale must mention shares are unavailable
            { assertThat(result.rationale).containsIgnoringCase("shares") },
        )
    }

    @Test
    fun `INDETERMINATE when income list is empty`() {
        // No income rows → extractSharesOutstanding returns null
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = 600_000.0,
                ),
            ),
            income = emptyList(),
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.INDETERMINATE)
    }

    // =========================================================================
    // Scenario 4 — NCAV <= 0: RED (passivo > attivo corrente)
    // =========================================================================

    @Test
    fun `RED when ncavTotal is negative (totalLiabilities exceeds totalCurrentAssets)`() {
        // currentAssets=300_000, liabilities=700_000 → ncavTotal=-400_000
        val dataset = dataset(
            currentAssets = 300_000.0,
            totalLiabilities = 700_000.0,
            sharesDil = 100_000.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("NCAV_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.NcavLatest
                // ncavTotal and ncavPerShare are populated (not null) for RED
                assertThat(typed.ncavTotal).isNotNull()
                assertThat(typed.ncavTotal!!).isLessThanOrEqualTo(0.0)
                assertThat(typed.ncavPerShare).isNotNull()
                assertThat(typed.ncavPerShare!!).isLessThanOrEqualTo(0.0)
            },
        )
    }

    @Test
    fun `RED when ncavTotal is exactly zero (boundary — still not net-net possible)`() {
        val dataset = dataset(
            currentAssets = 500_000.0,
            totalLiabilities = 500_000.0,
            sharesDil = 100_000.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.NcavLatest
                assertThat(typed.ncavTotal!!).isCloseTo(0.0, within(1e-9))
            },
        )
    }

    @Test
    fun `RED signal is NOT NOT_CALCULABLE — ADR-029 paragraph 2 semantic`() {
        // ADR-029 §2: ncavTotal <= 0 → RED (deterministic).
        // NOT_CALCULABLE is reserved for NET_NET_RATIO (§3), not NCAV_LATEST.
        val dataset = dataset(
            currentAssets = 200_000.0,
            totalLiabilities = 600_000.0,
            sharesDil = 50_000.0,
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.signal).isNotEqualTo(Signal.NOT_CALCULABLE) },
        )
    }

    // =========================================================================
    // ruleId invariant
    // =========================================================================

    @Test
    fun `ruleId is always NCAV_LATEST regardless of signal`() {
        val green = rule.evaluate(dataset(1_000_000.0, 300_000.0, 100_000.0))
        val red = rule.evaluate(dataset(200_000.0, 600_000.0, 100_000.0))
        val indeterminate = rule.evaluate(datasetWithBalance(emptyList()))

        assertAll(
            { assertThat(green.ruleId).isEqualTo("NCAV_LATEST") },
            { assertThat(red.ruleId).isEqualTo("NCAV_LATEST") },
            { assertThat(indeterminate.ruleId).isEqualTo("NCAV_LATEST") },
        )
    }

    // --- helpers ---

    private fun dataset(
        currentAssets: Double,
        totalLiabilities: Double,
        sharesDil: Double,
    ): FinancialDataset = makeDataset(
        balance = listOf(
            BalanceSheetDto(
                date = "2024-12-31",
                calendarYear = "2024",
                totalCurrentAssets = currentAssets,
                totalLiabilities = totalLiabilities,
            ),
        ),
        income = listOf(
            IncomeStatementDto(
                date = "2024-12-31",
                calendarYear = "2024",
                weightedAverageShsOutDil = sharesDil,
            ),
        ),
    )

    private fun datasetWithBalance(rows: List<BalanceSheetDto>): FinancialDataset =
        makeDataset(balance = rows, income = emptyList())

    private fun makeDataset(
        balance: List<BalanceSheetDto> = emptyList(),
        income: List<IncomeStatementDto> = emptyList(),
    ): FinancialDataset = FinancialDataset(
        ticker = "TEST",
        income = income,
        balance = balance,
        cashFlow = emptyList<CashFlowDto>(),
        keyMetrics = emptyList<KeyMetricsDto>(),
        dataSnapshotAt = Instant.parse("2026-06-05T00:00:00Z"),
    )
}
