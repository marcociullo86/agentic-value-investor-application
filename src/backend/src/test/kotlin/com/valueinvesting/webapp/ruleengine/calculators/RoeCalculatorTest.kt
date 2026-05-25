package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit tests for RoeCalculator.fiveYearAverage — porting of agent.py roe_medio_5y.
// Covers every edge case mandated by TSK-160 DoD + ADR-020 §TSK-EP011-A.
class RoeCalculatorTest {

    private val eps = Offset.offset(1e-9)

    // --- happy path ---

    @Test
    fun `5-year average ROE with full data (AAPL-like)`() {
        val income = incomes(100.0, 90.0, 80.0, 70.0, 60.0)
        val balance = balances(500.0, 450.0, 400.0, 350.0, 300.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(5) },
            {
                // ROE per year: 0.20, 0.20, 0.20, 0.20, 0.20 → avg = 0.20
                assertThat(result.average).isNotNull()
                assertThat(result.average!!).isCloseTo(0.20, eps)
            },
        )
    }

    @Test
    fun `uses only 5 most recent years when more are provided`() {
        val income = incomes(100.0, 90.0, 80.0, 70.0, 60.0, 50.0, 40.0)
        val balance = balances(500.0, 450.0, 400.0, 350.0, 300.0, 250.0, 200.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertThat(result.dataPoints).isEqualTo(5)
        // 100/500=0.20, 90/450=0.20, 80/400=0.20, 70/350=0.20, 60/300=0.20
        assertThat(result.average!!).isCloseTo(0.20, eps)
    }

    // --- IPO recente (< 5 anni disponibili) ---

    @Test
    fun `IPO with only 3 years of data computes average on available years`() {
        val income = incomes(50.0, 40.0, 30.0)
        val balance = balances(200.0, 180.0, 150.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(3) },
            {
                // 50/200=0.25, 40/180≈0.2222, 30/150=0.20 → avg ≈ 0.2241
                val expected = (50.0 / 200 + 40.0 / 180 + 30.0 / 150) / 3
                assertThat(result.average!!).isCloseTo(expected, eps)
            },
        )
    }

    @Test
    fun `single year of data returns that year's ROE`() {
        val income = incomes(25.0)
        val balance = balances(100.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(1) },
            { assertThat(result.average!!).isCloseTo(0.25, eps) },
        )
    }

    // --- equity ≤ 0: excluded ---

    @Test
    fun `year with equity equal to zero is excluded`() {
        val income = incomes(100.0, 50.0, 80.0)
        val balance = balances(500.0, 0.0, 400.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(2) },
            {
                // 100/500=0.20, skipped, 80/400=0.20 → avg = 0.20
                assertThat(result.average!!).isCloseTo(0.20, eps)
            },
        )
    }

    @Test
    fun `year with negative equity is excluded`() {
        val income = incomes(100.0, 50.0, 80.0)
        val balance = balances(500.0, -200.0, 400.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(2) },
            { assertThat(result.average!!).isCloseTo(0.20, eps) },
        )
    }

    @Test
    fun `all years with equity lte zero returns null average`() {
        val income = incomes(100.0, 50.0, 80.0)
        val balance = balances(-100.0, 0.0, -300.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(0) },
            { assertThat(result.average).isNull() },
        )
    }

    // --- ROE negativo (incluso nel calcolo) ---

    @Test
    fun `negative ROE included when net income is negative but equity positive`() {
        val income = incomes(-30.0, 50.0, 40.0)
        val balance = balances(200.0, 200.0, 200.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(3) },
            {
                // -30/200=-0.15, 50/200=0.25, 40/200=0.20 → avg = 0.10
                assertThat(result.average!!).isCloseTo(0.10, eps)
            },
        )
    }

    // --- null safety ---

    @Test
    fun `year with null netIncome is excluded`() {
        val income = listOf(
            incomeRow(100.0),
            incomeRow(null),
            incomeRow(80.0),
        )
        val balance = balances(500.0, 400.0, 400.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(2) },
            { assertThat(result.average!!).isCloseTo(0.20, eps) },
        )
    }

    @Test
    fun `year with null equity is excluded`() {
        val income = incomes(100.0, 50.0, 80.0)
        val balance = listOf(
            balanceRow(500.0),
            balanceRow(null),
            balanceRow(400.0),
        )

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(2) },
            { assertThat(result.average!!).isCloseTo(0.20, eps) },
        )
    }

    // --- empty / mismatched inputs ---

    @Test
    fun `empty income list returns null average`() {
        val result = RoeCalculator.fiveYearAverage(emptyList(), balances(500.0))

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(0) },
            { assertThat(result.average).isNull() },
        )
    }

    @Test
    fun `empty balance list returns null average`() {
        val result = RoeCalculator.fiveYearAverage(incomes(100.0), emptyList())

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(0) },
            { assertThat(result.average).isNull() },
        )
    }

    @Test
    fun `mismatched list sizes - fewer balance sheets than income statements`() {
        val income = incomes(100.0, 90.0, 80.0, 70.0, 60.0)
        val balance = balances(500.0, 450.0, 400.0)

        val result = RoeCalculator.fiveYearAverage(income, balance)

        assertAll(
            { assertThat(result.dataPoints).isEqualTo(3) },
            { assertThat(result.average!!).isCloseTo(0.20, eps) },
        )
    }

    // --- helpers ---

    private fun incomeRow(netIncome: Double?): IncomeStatementDto =
        IncomeStatementDto(netIncome = netIncome)

    private fun incomes(vararg netIncomes: Double): List<IncomeStatementDto> =
        netIncomes.map { incomeRow(it) }

    private fun balanceRow(equity: Double?): BalanceSheetDto =
        BalanceSheetDto(totalStockholdersEquity = equity)

    private fun balances(vararg equities: Double): List<BalanceSheetDto> =
        equities.map { balanceRow(it) }
}
