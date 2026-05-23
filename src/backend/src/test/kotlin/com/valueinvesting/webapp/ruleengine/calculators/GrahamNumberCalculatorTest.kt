package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// Unit tests for GrahamNumberCalculator (TSK-016 DoD).
// Covers the 3 mandatory cases (EPS 5/BVPS 20, EPS -1, BVPS null) plus the
// edge cases documented in the TSK brief.
class GrahamNumberCalculatorTest {

    private val calc = GrahamNumberCalculator()

    @Test
    fun `canonical case - EPS 5 and BVPS 20 yields ~47 point 43`() {
        // sqrt(22.5 * 5 * 20) = sqrt(2250) = 47.4341649...
        val result = calc.calculate(eps = 5.0, bvps = 20.0)

        assertAll(
            { assertThat(result.applicable).isTrue() },
            { assertThat(result.value).isNotNull() },
            { assertThat(result.value!!).isCloseTo(47.43, within(0.01)) },
            { assertThat(result.rationale).contains("22.5") },
        )
    }

    @Test
    fun `precision case - EPS 10 and BVPS 100 yields exactly 150`() {
        // sqrt(22.5 * 10 * 100) = sqrt(22500) = 150.0 exactly.
        val result = calc.calculate(eps = 10.0, bvps = 100.0)

        assertAll(
            { assertThat(result.applicable).isTrue() },
            { assertThat(result.value!!).isCloseTo(150.0, within(1e-9)) },
        )
    }

    @Test
    fun `negative EPS yields Not Applicable with null value and no exception`() {
        val result = calc.calculate(eps = -1.0, bvps = 20.0)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
            { assertThat(result.rationale).contains("EPS=-1.0") },
        )
    }

    @Test
    fun `null BVPS yields Not Applicable`() {
        val result = calc.calculate(eps = 5.0, bvps = null)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
        )
    }

    @Test
    fun `null EPS yields Not Applicable`() {
        val result = calc.calculate(eps = null, bvps = 20.0)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
        )
    }

    @Test
    fun `both null yields Not Applicable`() {
        val result = calc.calculate(eps = null, bvps = null)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
        )
    }

    @Test
    fun `EPS zero is Not Applicable (boundary - sqrt would be 0 which is meaningless)`() {
        val result = calc.calculate(eps = 0.0, bvps = 20.0)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
        )
    }

    @Test
    fun `BVPS zero is Not Applicable`() {
        val result = calc.calculate(eps = 5.0, bvps = 0.0)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
        )
    }

    @Test
    fun `negative BVPS yields Not Applicable`() {
        val result = calc.calculate(eps = 5.0, bvps = -3.0)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
        )
    }

    @Test
    fun `calculateFromDataset on empty dataset is Not Applicable`() {
        val dataset = emptyDataset()

        val result = calc.calculateFromDataset(dataset)

        assertAll(
            { assertThat(result.applicable).isFalse() },
            { assertThat(result.value).isNull() },
        )
    }

    @Test
    fun `calculateFromDataset on valid dataset matches direct calculate`() {
        // Latest year (first element): EPS=5.0, BVPS=20.0 -> ~47.434
        val dataset = datasetWith(eps = 5.0, bvps = 20.0)

        val viaDataset = calc.calculateFromDataset(dataset)
        val viaPure = calc.calculate(eps = 5.0, bvps = 20.0)

        assertAll(
            { assertThat(viaDataset.applicable).isTrue() },
            { assertThat(viaDataset.value!!).isCloseTo(viaPure.value!!, within(1e-9)) },
        )
    }

    @Test
    fun `calculateFromDataset uses LATEST year (first element) and ignores older rows`() {
        // First element (latest) has good values; older rows have garbage that, if
        // accidentally consumed, would change the outcome.
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(eps = 10.0, calendarYear = "2024"),
                IncomeStatementDto(eps = -99.0, calendarYear = "2023"),
            ),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = listOf(
                KeyMetricsDto(bookValuePerShare = 100.0, calendarYear = "2024"),
                KeyMetricsDto(bookValuePerShare = -50.0, calendarYear = "2023"),
            ),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = calc.calculateFromDataset(dataset)

        assertAll(
            { assertThat(result.applicable).isTrue() },
            { assertThat(result.value!!).isCloseTo(150.0, within(1e-9)) },
        )
    }

    @Test
    fun `calculateFromDataset propagates Not Applicable when latest EPS is null`() {
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = listOf(IncomeStatementDto(eps = null, calendarYear = "2024")),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = listOf(KeyMetricsDto(bookValuePerShare = 20.0, calendarYear = "2024")),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

        val result = calc.calculateFromDataset(dataset)

        assertThat(result.applicable).isFalse()
        assertThat(result.value).isNull()
    }

    // Audit 2026-05-23 regression: schema /stable/key-metrics non espone più
    // bookValuePerShare (è spostato in /stable/ratios). Il fallback derivato
    // BVPS = totalStockholdersEquity / weightedAverageShsOutDil deve impedire
    // l'avere "Graham Number Non Applicable" sistematico in produzione.
    @Test
    fun `calculateFromDataset derives BVPS from equity-over-shares when bookValuePerShare is null`() {
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(
                    eps = 10.0,
                    weightedAverageShsOutDil = 100.0,
                    calendarYear = "2024",
                ),
            ),
            balance = listOf(
                BalanceSheetDto(
                    totalStockholdersEquity = 1000.0,
                    calendarYear = "2024",
                ),
            ),
            cashFlow = emptyList(),
            keyMetrics = listOf(KeyMetricsDto(bookValuePerShare = null, calendarYear = "2024")),
            dataSnapshotAt = Instant.parse("2026-05-23T00:00:00Z"),
        )

        // Derived BVPS = 1000 / 100 = 10.0; sqrt(22.5 * 10 * 10) = sqrt(2250) ~ 47.43
        val result = calc.calculateFromDataset(dataset)

        assertAll(
            { assertThat(result.applicable).isTrue() },
            { assertThat(result.value!!).isCloseTo(47.43, within(0.01)) },
        )
    }

    @Test
    fun `calculateFromDataset prefers explicit bookValuePerShare over derivation`() {
        // Both sources present: explicit BVPS wins so legacy data sources keep working.
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(
                    eps = 10.0,
                    weightedAverageShsOutDil = 100.0,
                    calendarYear = "2024",
                ),
            ),
            balance = listOf(
                BalanceSheetDto(
                    // Would derive to 50 BVPS, but explicit field below should win.
                    totalStockholdersEquity = 5000.0,
                    calendarYear = "2024",
                ),
            ),
            cashFlow = emptyList(),
            keyMetrics = listOf(KeyMetricsDto(bookValuePerShare = 100.0, calendarYear = "2024")),
            dataSnapshotAt = Instant.parse("2026-05-23T00:00:00Z"),
        )

        val result = calc.calculateFromDataset(dataset)

        // Should be 150 (uses explicit BVPS=100) not the derived value
        assertAll(
            { assertThat(result.applicable).isTrue() },
            { assertThat(result.value!!).isCloseTo(150.0, within(1e-6)) },
        )
    }

    @Test
    fun `calculateFromDataset Not Applicable when both BVPS sources are missing`() {
        val dataset = FinancialDataset(
            ticker = "TEST",
            income = listOf(
                IncomeStatementDto(
                    eps = 10.0,
                    weightedAverageShsOutDil = null,
                    weightedAverageShsOut = null,
                    calendarYear = "2024",
                ),
            ),
            balance = listOf(BalanceSheetDto(totalStockholdersEquity = 1000.0, calendarYear = "2024")),
            cashFlow = emptyList(),
            keyMetrics = listOf(KeyMetricsDto(bookValuePerShare = null, calendarYear = "2024")),
            dataSnapshotAt = Instant.parse("2026-05-23T00:00:00Z"),
        )

        val result = calc.calculateFromDataset(dataset)

        assertThat(result.applicable).isFalse()
        assertThat(result.value).isNull()
    }

    // --- helpers ---

    private fun datasetWith(eps: Double?, bvps: Double?): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = listOf(IncomeStatementDto(eps = eps, calendarYear = "2024")),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = listOf(KeyMetricsDto(bookValuePerShare = bvps, calendarYear = "2024")),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun emptyDataset(): FinancialDataset =
        FinancialDataset(
            ticker = "TEST",
            income = emptyList<IncomeStatementDto>(),
            balance = emptyList<BalanceSheetDto>(),
            cashFlow = emptyList<CashFlowDto>(),
            keyMetrics = emptyList<KeyMetricsDto>(),
            dataSnapshotAt = Instant.parse("2026-05-21T00:00:00Z"),
        )

    private fun within(eps: Double): Offset<Double> = Offset.offset(eps)
}
