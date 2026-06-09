package com.valueinvesting.webapp.backtest

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

// Tests TSK-346 / TSK-349: point-in-time correctness — filing successivo a `t`
// non viene mai usato; filing/accepted prima di `t` viene incluso.
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-346.md §"Filtro filingDate"]
// [^src: management/kanban/EP-024-.../US-105-.../TSK-349.md §"Test point-in-time no look-ahead"]
class PointInTimeFinancialFilterTest {

    private val filter = PointInTimeFinancialFilter()

    private fun baseDataset(
        income: List<IncomeStatementDto> = emptyList(),
        balance: List<BalanceSheetDto> = emptyList(),
        cashFlow: List<CashFlowDto> = emptyList(),
        keyMetrics: List<KeyMetricsDto> = emptyList(),
        dividends: List<DividendRecord> = emptyList(),
    ) = FinancialDataset(
        ticker = "TEST",
        income = income,
        balance = balance,
        cashFlow = cashFlow,
        keyMetrics = keyMetrics,
        dataSnapshotAt = Instant.now(),
        isStale = false,
        staleReason = null,
        currentPrice = null,
        dividends = dividends,
    )

    @Test
    fun `filing with acceptedDate after asOf is excluded from filtered dataset`() {
        val futureIncome = IncomeStatementDto(
            date = "2024-12-31",
            symbol = "TEST",
            // acceptedDate t+1 month: NOT yet known at asOf=2025-01-31
            acceptedDate = "2025-03-15 10:00:00",
            fillingDate = "2025-03-15",
            netIncome = 1000.0,
        )
        val pastIncome = IncomeStatementDto(
            date = "2023-12-31",
            symbol = "TEST",
            acceptedDate = "2024-02-15 10:00:00",
            fillingDate = "2024-02-15",
            netIncome = 800.0,
        )
        val dataset = baseDataset(income = listOf(futureIncome, pastIncome))

        val asOf = LocalDate.of(2025, 1, 31)
        val filtered = filter.filter(dataset, asOf, currentPrice = 50.0)

        assertThat(filtered.income).hasSize(1)
        assertThat(filtered.income[0].netIncome).isEqualTo(800.0)
        assertThat(filtered.currentPrice).isEqualTo(50.0)
    }

    @Test
    fun `filing with filingDate after asOf is excluded when acceptedDate absent`() {
        val futureFilingOnly = BalanceSheetDto(
            date = "2024-12-31",
            symbol = "TEST",
            acceptedDate = null,
            fillingDate = "2025-03-15", // filing date in the future relative to asOf
            totalAssets = 5000.0,
        )
        val pastFilingOnly = BalanceSheetDto(
            date = "2023-12-31",
            symbol = "TEST",
            acceptedDate = null,
            fillingDate = "2024-02-15",
            totalAssets = 4500.0,
        )
        val dataset = baseDataset(balance = listOf(futureFilingOnly, pastFilingOnly))

        val asOf = LocalDate.of(2025, 1, 31)
        val filtered = filter.filter(dataset, asOf, currentPrice = 50.0)

        assertThat(filtered.balance).hasSize(1)
        assertThat(filtered.balance[0].totalAssets).isEqualTo(4500.0)
    }

    @Test
    fun `filing with no date at all is conservatively excluded`() {
        val noDate = CashFlowDto(
            date = "2024-12-31",
            symbol = "TEST",
            acceptedDate = null,
            fillingDate = null,
            netIncome = 100.0,
        )
        val dataset = baseDataset(cashFlow = listOf(noDate))

        val filtered = filter.filter(dataset, LocalDate.of(2025, 6, 1), currentPrice = 10.0)

        assertThat(filtered.cashFlow).isEmpty()
    }

    @Test
    fun `key metrics filtered by date when filingDate not exposed by FMP`() {
        val future = KeyMetricsDto(symbol = "TEST", date = "2025-03-31", roe = 0.15)
        val past = KeyMetricsDto(symbol = "TEST", date = "2024-03-31", roe = 0.12)
        val dataset = baseDataset(keyMetrics = listOf(future, past))

        val filtered = filter.filter(dataset, LocalDate.of(2025, 1, 31), currentPrice = 10.0)

        assertThat(filtered.keyMetrics).hasSize(1)
        assertThat(filtered.keyMetrics[0].roe).isEqualTo(0.12)
    }

    @Test
    fun `dividends with paymentDate after asOf are excluded`() {
        val future = DividendRecord(date = "2025-03-15", paymentDate = "2025-03-20", dividend = 0.5)
        val past = DividendRecord(date = "2024-03-15", paymentDate = "2024-03-20", dividend = 0.4)
        val dataset = baseDataset(dividends = listOf(future, past))

        val filtered = filter.filter(dataset, LocalDate.of(2025, 1, 31), currentPrice = 10.0)

        assertThat(filtered.dividends).hasSize(1)
        assertThat(filtered.dividends[0].dividend).isEqualTo(0.4)
    }

    @Test
    fun `parseLooseDate accepts both ISO and timestamp formats`() {
        assertThat(filter.parseLooseDate("2024-02-15")).isEqualTo(LocalDate.of(2024, 2, 15))
        assertThat(filter.parseLooseDate("2024-02-15 10:30:00")).isEqualTo(LocalDate.of(2024, 2, 15))
        assertThat(filter.parseLooseDate(null)).isNull()
        assertThat(filter.parseLooseDate("")).isNull()
        assertThat(filter.parseLooseDate("not-a-date")).isNull()
    }

    @Test
    fun `acceptedDate takes precedence over filingDate when both present`() {
        // acceptedDate dopo asOf, ma filingDate prima → escluso (acceptedDate vince).
        val record = IncomeStatementDto(
            acceptedDate = "2025-03-15 10:00:00",
            fillingDate = "2024-12-31",
            netIncome = 100.0,
        )
        val asOf = LocalDate.of(2025, 1, 31)
        assertThat(filter.acceptedOrFilingOnOrBefore(record.acceptedDate, record.fillingDate, asOf))
            .isFalse()
    }
}
