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

// Unit tests for NcavCalculator (TSK-318, US-096, EP-023).
//
// Covers TSK-318 §"Test NcavCalculator":
//   - Input completo valido → reason="ok", ncavTotal e ncavPerShare corretti.
//   - totalCurrentAssets == null → reason="missing_balance_sheet".
//   - balance sheet vuoto → reason="missing_balance_sheet".
//   - sharesOutstanding null (diluted + basic entrambi null) → reason="missing_shares".
//   - ncavTotal <= 0 (passivo > attivo) → reason="negative", campi valorizzati.
//   - Multi-year balance: latest year selezionato per max(date).
//   - Fallback shares diluted→basic: preferisce weightedAverageShsOutDil,
//     fallback weightedAverageShsOut.
//
// Formula (ADR-029 §1):
//   ncavTotal     = totalCurrentAssets - totalLiabilities  (passività TOTALI)
//   ncavPerShare  = ncavTotal / sharesOutstanding
//
// Fonte sharesOutstanding (deviazione documentata in NcavCalculator.kt):
//   ADR-029 §1 indica KeyMetricsDto.sharesOutstanding (non esiste nel DTO reale).
//   Implementazione usa IncomeStatementDto.weightedAverageShsOutDil (diluted preferito)
//   con fallback weightedAverageShsOut (basic) — pattern canonico DcfCalculator.
//
// Idiomi: JUnit5 + AssertJ assertAll (coerente con GrahamNumberCalculatorTest).
// Nessuna dipendenza Spring/Mockito — pure unit test (NcavCalculator è un object puro).
//
// [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §1, §2]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/TSK-318.md §Test NcavCalculator]
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/ruleengine/calculators/NcavCalculator.kt]
class NcavCalculatorTest {

    // =========================================================================
    // 1. Caso valido — reason="ok"
    // =========================================================================

    @Test
    fun `canonical case — valid inputs produce reason ok with correct ncavTotal and ncavPerShare`() {
        // currentAssets=1_000_000, totalLiabilities=600_000 → ncavTotal=400_000
        // shares=200_000 → ncavPerShare=2.0
        val dataset = dataset(
            currentAssets = 1_000_000.0,
            totalLiabilities = 600_000.0,
            sharesOutstandingDil = 200_000.0,
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("ok") },
            { assertThat(result.ncavTotal).isNotNull() },
            { assertThat(result.ncavTotal!!).isCloseTo(400_000.0, within(1e-6)) },
            { assertThat(result.ncavPerShare).isNotNull() },
            { assertThat(result.ncavPerShare!!).isCloseTo(2.0, within(1e-6)) },
        )
    }

    @Test
    fun `precision case — ncavPerShare 15 (ncavTotal=1_500_000, shares=100_000)`() {
        val dataset = dataset(
            currentAssets = 2_000_000.0,
            totalLiabilities = 500_000.0,
            sharesOutstandingDil = 100_000.0,
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("ok") },
            { assertThat(result.ncavTotal!!).isCloseTo(1_500_000.0, within(1e-6)) },
            { assertThat(result.ncavPerShare!!).isCloseTo(15.0, within(1e-6)) },
        )
    }

    // =========================================================================
    // 2. Balance sheet vuoto → reason="missing_balance_sheet"
    // =========================================================================

    @Test
    fun `empty balance sheet list produces reason missing_balance_sheet`() {
        val dataset = datasetWithBalance(emptyList())

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("missing_balance_sheet") },
            { assertThat(result.ncavTotal).isNull() },
            { assertThat(result.ncavPerShare).isNull() },
        )
    }

    // =========================================================================
    // 3. totalCurrentAssets == null → reason="missing_balance_sheet"
    //    (PATTERN §7 r.13: campi mancanti = assenti, mai 0)
    // =========================================================================

    @Test
    fun `totalCurrentAssets null in latest row produces reason missing_balance_sheet`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = null,
                    totalLiabilities = 400_000.0,
                ),
            ),
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("missing_balance_sheet") },
            { assertThat(result.ncavTotal).isNull() },
            { assertThat(result.ncavPerShare).isNull() },
        )
    }

    @Test
    fun `totalLiabilities null in latest row produces reason missing_balance_sheet`() {
        val dataset = datasetWithBalance(
            listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = null,
                ),
            ),
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("missing_balance_sheet") },
            { assertThat(result.ncavTotal).isNull() },
            { assertThat(result.ncavPerShare).isNull() },
        )
    }

    // =========================================================================
    // 4. sharesOutstanding null (diluted AND basic) → reason="missing_shares"
    // =========================================================================

    @Test
    fun `both weightedAverageShsOutDil and weightedAverageShsOut null produce reason missing_shares`() {
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

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("missing_shares") },
            { assertThat(result.ncavTotal).isNull() },
            { assertThat(result.ncavPerShare).isNull() },
        )
    }

    @Test
    fun `empty income list produces reason missing_shares`() {
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

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("missing_shares") },
            { assertThat(result.ncavTotal).isNull() },
        )
    }

    @Test
    fun `shares zero or negative in both fields produce reason missing_shares`() {
        // Both fields present but ≤ 0 → treated as unavailable (PATTERN §7 r.13)
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
                    weightedAverageShsOutDil = 0.0,
                    weightedAverageShsOut = -100.0,
                ),
            ),
        )

        val result = NcavCalculator.compute(dataset)

        assertThat(result.reason).isEqualTo("missing_shares")
    }

    // =========================================================================
    // 5. ncavTotal <= 0 (passivo totale > attivo corrente) → reason="negative"
    //    ncavTotal e ncavPerShare valorizzati (≤ 0), NOT null
    // =========================================================================

    @Test
    fun `ncavTotal negative when totalLiabilities exceeds totalCurrentAssets — reason negative, fields populated`() {
        // currentAssets=400_000, totalLiabilities=600_000 → ncavTotal=-200_000
        // shares=100_000 → ncavPerShare=-2.0
        val dataset = dataset(
            currentAssets = 400_000.0,
            totalLiabilities = 600_000.0,
            sharesOutstandingDil = 100_000.0,
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("negative") },
            { assertThat(result.ncavTotal).isNotNull() },
            { assertThat(result.ncavTotal!!).isCloseTo(-200_000.0, within(1e-6)) },
            { assertThat(result.ncavPerShare).isNotNull() },
            { assertThat(result.ncavPerShare!!).isCloseTo(-2.0, within(1e-6)) },
        )
    }

    @Test
    fun `ncavTotal exactly zero (boundary) produces reason negative`() {
        // currentAssets == totalLiabilities → ncavTotal = 0.0 (still <= 0)
        val dataset = dataset(
            currentAssets = 500_000.0,
            totalLiabilities = 500_000.0,
            sharesOutstandingDil = 100_000.0,
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("negative") },
            { assertThat(result.ncavTotal!!).isCloseTo(0.0, within(1e-9)) },
            { assertThat(result.ncavPerShare!!).isCloseTo(0.0, within(1e-9)) },
        )
    }

    // =========================================================================
    // 6. Latest-year picker: max by ISO-8601 date (multi-year balance sheet)
    // =========================================================================

    @Test
    fun `latest year selected by max date even when input is older-first order`() {
        // 2022: currentAssets=200, liabilities=500 → ncavTotal=-300 (would give "negative")
        // 2024: currentAssets=1_000_000, liabilities=400_000 → ncavTotal=600_000 (ok)
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2022-12-31",
                    totalCurrentAssets = 200.0,
                    totalLiabilities = 500.0,
                ),
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = 400_000.0,
                ),
                BalanceSheetDto(
                    date = "2023-12-31",
                    totalCurrentAssets = 300.0,
                    totalLiabilities = 600.0,
                ),
            ),
            income = listOf(
                IncomeStatementDto(
                    date = "2024-12-31",
                    weightedAverageShsOutDil = 100_000.0,
                ),
            ),
        )

        val result = NcavCalculator.compute(dataset)

        // Must pick 2024 data → reason="ok"
        assertAll(
            { assertThat(result.reason).isEqualTo("ok") },
            { assertThat(result.ncavTotal!!).isCloseTo(600_000.0, within(1e-6)) },
        )
    }

    // =========================================================================
    // 7. Shares fallback: weightedAverageShsOutDil null → weightedAverageShsOut used
    // =========================================================================

    @Test
    fun `shares fallback from diluted null to basic weightedAverageShsOut`() {
        // diluted null, basic 50_000 → ncavPerShare = ncavTotal / 50_000
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = 500_000.0,
                ),
            ),
            income = listOf(
                IncomeStatementDto(
                    date = "2024-12-31",
                    weightedAverageShsOutDil = null,
                    weightedAverageShsOut = 50_000.0,
                ),
            ),
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("ok") },
            { assertThat(result.ncavPerShare!!).isCloseTo(10.0, within(1e-6)) },
        )
    }

    @Test
    fun `diluted preferred over basic when both are present`() {
        // diluted=100_000 (higher — would give ncavPerShare=5), basic=50_000 (gives 10).
        // Diluted must be preferred.
        val dataset = makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    totalCurrentAssets = 1_000_000.0,
                    totalLiabilities = 500_000.0,
                ),
            ),
            income = listOf(
                IncomeStatementDto(
                    date = "2024-12-31",
                    weightedAverageShsOutDil = 100_000.0,
                    weightedAverageShsOut = 50_000.0,
                ),
            ),
        )

        val result = NcavCalculator.compute(dataset)

        assertAll(
            { assertThat(result.reason).isEqualTo("ok") },
            // ncavTotal=500_000 / diluted=100_000 → 5.0
            { assertThat(result.ncavPerShare!!).isCloseTo(5.0, within(1e-6)) },
        )
    }

    // --- helpers ---

    /** Convenience: single-year dataset with explicit balance + shares (diluted). */
    private fun dataset(
        currentAssets: Double,
        totalLiabilities: Double,
        sharesOutstandingDil: Double,
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
                weightedAverageShsOutDil = sharesOutstandingDil,
            ),
        ),
    )

    /** Convenience: supply balance rows; income is empty (triggers missing_shares). */
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

    private fun within(eps: Double): Offset<Double> = Offset.offset(eps)
}
