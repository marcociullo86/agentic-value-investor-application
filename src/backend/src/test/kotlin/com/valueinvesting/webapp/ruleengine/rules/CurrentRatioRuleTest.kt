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

// Unit tests for CurrentRatioRule.
//
// Covers the three outcome bands of US-009 + the data-availability edge cases.
// Snapshot semantics: rule picks the latest year (max `date`), NOT a 10y avg.
class CurrentRatioRuleTest {

    private val rule = CurrentRatioRule()

    @Test
    fun `GREEN when latest current ratio is above 2 (2_5)`() {
        // Two years: older 2023 ratio 1.0 (would be RED if picked) and latest
        // 2024 ratio 2.5. The rule must pick 2024 -> GREEN.
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2023-12-31",
                    calendarYear = "2023",
                    totalCurrentAssets = 100.0,
                    totalCurrentLiabilities = 100.0,
                ),
                BalanceSheetDto(
                    date = "2024-12-31",
                    calendarYear = "2024",
                    totalCurrentAssets = 250.0,
                    totalCurrentLiabilities = 100.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.ruleId).isEqualTo("CURRENT_RATIO_LATEST") },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(2.5, within(1e-9)) },
            { assertThat(result.threshold).contains("2.0") },
            { assertThat(result.rationale).contains("2024") },
        )
    }

    @Test
    fun `YELLOW when latest current ratio is in the 1_5 to 2_0 stabile-friendly band (1_7)`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    calendarYear = "2024",
                    totalCurrentAssets = 170.0,
                    totalCurrentLiabilities = 100.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            { assertThat(result.observedValue!!).isCloseTo(1.7, within(1e-9)) },
            // US-009 AC verbatim: "stabile-friendly" must appear in the rationale.
            { assertThat(result.rationale).contains("stabile-friendly") },
        )
    }

    @Test
    fun `YELLOW boundary on 1_5 (closed on YELLOW)`() {
        // ratio exactly 1.5: GREEN_THRESHOLD strict (>), YELLOW threshold closed (>=).
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 150.0,
                    totalCurrentLiabilities = 100.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.YELLOW)
    }

    @Test
    fun `RED when latest current ratio is below 1_5 (1_0)`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 100.0,
                    totalCurrentLiabilities = 100.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            { assertThat(result.observedValue!!).isCloseTo(1.0, within(1e-9)) },
        )
    }

    @Test
    fun `NOT_CALCULABLE when balance sheet list is empty`() {
        val dataset = datasetWithBalance(emptyList())

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `INDETERMINATE when current liabilities is zero (div-by-zero protection)`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 200.0,
                    totalCurrentLiabilities = 0.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
            { assertThat(result.rationale).contains("non positive") },
        )
    }

    @Test
    fun `INDETERMINATE when current liabilities is negative`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 200.0,
                    totalCurrentLiabilities = -50.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertThat(result.signal).isEqualTo(Signal.INDETERMINATE)
    }

    @Test
    fun `INDETERMINATE when current assets is null (never coerced to zero)`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = null,
                    totalCurrentLiabilities = 100.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.observedValue).isNull() },
        )
    }

    @Test
    fun `latest year selected by date even when input order is older-first`() {
        // Force input order to be reverse-chronological then chronological mix:
        // 2022 (1.0 RED) first, 2024 (2.5 GREEN) last. Rule must pick 2024.
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2022-12-31",
                    totalCurrentAssets = 100.0,
                    totalCurrentLiabilities = 100.0,
                ),
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 300.0,
                    totalCurrentLiabilities = 100.0,
                ),
                BalanceSheetDto(
                    date = "2023-12-31",
                    totalCurrentAssets = 160.0,
                    totalCurrentLiabilities = 100.0,
                ),
            ),
        )

        val result = rule.evaluate(dataset)

        assertAll(
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue!!).isCloseTo(3.0, within(1e-9)) },
            { assertThat(result.rationale).contains("2024") },
        )
    }

    // --- helpers ---

    private fun datasetWithBalance(rows: List<BalanceSheetDto>): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = rows,
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun within(eps: Double) = org.assertj.core.data.Offset.offset(eps)
}
