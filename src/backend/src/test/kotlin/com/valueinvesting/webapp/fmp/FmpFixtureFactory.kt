package com.valueinvesting.webapp.fmp

import io.mockk.every

object FmpFixtureFactory {

    fun stubSuccessfulFmp(adapter: FmpAdapter, symbol: String = "AAPL") {
        every { adapter.getIncomeStatement(symbol, any()) } returns FmpFixtureLoader.tenYearIncomeStatements(symbol)
        every { adapter.getBalanceSheet(symbol, any()) } returns FmpFixtureLoader.tenYearBalanceSheets(symbol)
        every { adapter.getCashFlow(symbol, any()) } returns FmpFixtureLoader.tenYearCashFlows(symbol)
        every { adapter.getKeyMetrics(symbol, any()) } returns FmpFixtureLoader.tenYearKeyMetrics(symbol)
        every { adapter.getProfile(symbol) } returns FmpFixtureLoader.loadProfile(symbol)
    }

    fun stubShortCashFlow(adapter: FmpAdapter, symbol: String = "SHORT") {
        every { adapter.getIncomeStatement(symbol, any()) } returns FmpFixtureLoader.tenYearIncomeStatements(symbol)
        every { adapter.getBalanceSheet(symbol, any()) } returns FmpFixtureLoader.tenYearBalanceSheets(symbol)
        every { adapter.getCashFlow(symbol, any()) } returns FmpFixtureLoader.shortCashFlows(symbol)
        every { adapter.getKeyMetrics(symbol, any()) } returns FmpFixtureLoader.tenYearKeyMetrics(symbol)
        every { adapter.getProfile(symbol) } returns FmpFixtureLoader.loadProfile(symbol)
    }

    fun stubAllUnavailable(adapter: FmpAdapter, symbol: String) {
        val ex = FmpUnavailableException("FMP down in test")
        every { adapter.getIncomeStatement(symbol, any()) } throws ex
        every { adapter.getBalanceSheet(symbol, any()) } throws ex
        every { adapter.getCashFlow(symbol, any()) } throws ex
        every { adapter.getKeyMetrics(symbol, any()) } throws ex
        every { adapter.getProfile(symbol) } throws ex
    }
}
