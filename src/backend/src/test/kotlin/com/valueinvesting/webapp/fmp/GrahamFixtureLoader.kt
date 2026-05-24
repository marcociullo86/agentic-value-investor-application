package com.valueinvesting.webapp.fmp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto

/**
 * Loads Graham-rule-specific FMP fixtures for EP-010 E2E integration tests (TSK-090).
 *
 * Fixture naming convention:
 *   graham-income-{symbol}.json       — 10 fiscal years (explicitly listed, no expansion needed)
 *   graham-balance-{symbol}.json      — 2+ years (used with or without expansion)
 *   graham-cashflow-{symbol}.json     — 2+ years
 *   graham-keymetrics-{symbol}.json   — 10 years, bookValuePerShare populated for PbLatestRule
 *   graham-profile-{symbol}.json      — single object (not array)
 *   graham-dividends-{symbol}.json    — full dividend history
 *
 * All symbols are lowercase in file names (e.g. "aapl", "msft", "ko", "googl").
 *
 * AAPL uses the existing profile fixture (profile-aapl.json) and the dividend-20y fixture
 * (dividends-aapl-20y.json) carried over from TSK-086; the income and keymetrics fixtures
 * are Graham-specific overrides with bookValuePerShare and 10-year EPS series.
 *
 * MSFT/KO/GOOGL fixtures are self-contained with their own profile, income, balance,
 * cashflow, keymetrics and (for MSFT/KO) dividends files.
 *
 * The GOOGL balance/cashflow/keymetrics fixtures are present to satisfy the full
 * AnalyzeTickerService pipeline; its dividend fixture is intentionally absent —
 * the test stubs getDividendHistory returning emptyList() to exercise INDETERMINATE.
 *
 * [^src: management/kanban/EP-010-graham-defensive-completeness/TSK-090.md]
 */
object GrahamFixtureLoader {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    // ─────────────────────────────────────────
    // AAPL
    // ─────────────────────────────────────────

    fun aaplProfile(): ProfileDto =
        mapper.readValue(
            requireNotNull(javaClass.classLoader.getResource("fmp-fixtures/profile-aapl.json")) {
                "Missing fmp-fixtures/profile-aapl.json"
            },
        )

    fun aaplIncome(): List<IncomeStatementDto> =
        loadList("fmp-fixtures/graham-income-aapl.json")

    /**
     * Reuses the existing AAPL balance sheet fixture (2 rows); expanded to 10 via
     * FmpFixtureLoader to satisfy rules that require >= 5 PPE/Revenue years.
     */
    fun aaplBalance(): List<BalanceSheetDto> =
        FmpFixtureLoader.tenYearBalanceSheets("AAPL")

    fun aaplCashFlow(): List<CashFlowDto> =
        FmpFixtureLoader.tenYearCashFlows("AAPL")

    fun aaplKeyMetrics(): List<KeyMetricsDto> =
        loadList("fmp-fixtures/graham-keymetrics-aapl.json")

    /** Full dividend history for AAPL: 2005-2024 (20 consecutive years → GREEN). */
    fun aaplDividends(): List<DividendRecord> =
        loadList("fmp-fixtures/dividends-aapl-20y.json")

    // ─────────────────────────────────────────
    // MSFT
    // ─────────────────────────────────────────

    fun msftProfile(): ProfileDto =
        mapper.readValue(
            requireNotNull(javaClass.classLoader.getResource("fmp-fixtures/graham-profile-msft.json")),
        )

    fun msftIncome(): List<IncomeStatementDto> =
        loadList("fmp-fixtures/graham-income-msft.json")

    fun msftBalance(): List<BalanceSheetDto> =
        expandBalanceTo10("MSFT", loadList("fmp-fixtures/graham-balance-msft.json"))

    fun msftCashFlow(): List<CashFlowDto> =
        expandCashFlowTo10("MSFT", loadList("fmp-fixtures/graham-cashflow-msft.json"))

    fun msftKeyMetrics(): List<KeyMetricsDto> =
        loadList("fmp-fixtures/graham-keymetrics-msft.json")

    /** Dividend history for MSFT: 2003-2024 (22 consecutive years → GREEN). */
    fun msftDividends(): List<DividendRecord> =
        loadList("fmp-fixtures/graham-dividends-msft.json")

    // ─────────────────────────────────────────
    // KO (Coca-Cola)
    // ─────────────────────────────────────────

    fun koProfile(): ProfileDto =
        mapper.readValue(
            requireNotNull(javaClass.classLoader.getResource("fmp-fixtures/graham-profile-ko.json")),
        )

    fun koIncome(): List<IncomeStatementDto> =
        loadList("fmp-fixtures/graham-income-ko.json")

    fun koBalance(): List<BalanceSheetDto> =
        expandBalanceTo10("KO", loadList("fmp-fixtures/graham-balance-ko.json"))

    fun koCashFlow(): List<CashFlowDto> =
        expandCashFlowTo10("KO", loadList("fmp-fixtures/graham-cashflow-ko.json"))

    fun koKeyMetrics(): List<KeyMetricsDto> =
        loadList("fmp-fixtures/graham-keymetrics-ko.json")

    /** Dividend history for KO: 1993-2024 (32 consecutive years → GREEN). */
    fun koDividends(): List<DividendRecord> =
        loadList("fmp-fixtures/graham-dividends-ko.json")

    // ─────────────────────────────────────────
    // GOOGL (no-dividend test case)
    // ─────────────────────────────────────────

    fun googlProfile(): ProfileDto =
        mapper.readValue(
            requireNotNull(javaClass.classLoader.getResource("fmp-fixtures/graham-profile-googl.json")),
        )

    fun googlIncome(): List<IncomeStatementDto> =
        loadList("fmp-fixtures/graham-income-googl.json")

    fun googlBalance(): List<BalanceSheetDto> =
        expandBalanceTo10("GOOGL", loadList("fmp-fixtures/graham-balance-googl.json"))

    fun googlCashFlow(): List<CashFlowDto> =
        expandCashFlowTo10("GOOGL", loadList("fmp-fixtures/graham-cashflow-googl.json"))

    fun googlKeyMetrics(): List<KeyMetricsDto> =
        loadList("fmp-fixtures/graham-keymetrics-googl.json")

    // ─────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────

    private inline fun <reified T> loadList(resource: String): List<T> =
        mapper.readValue(
            requireNotNull(javaClass.classLoader.getResource(resource)) {
                "Fixture resource not found: $resource"
            },
        )

    /**
     * Expands a 2-row balance sheet fixture to 10 rows by duplicating the first
     * (most recent, PPE-bearing) row with decreasing years. This satisfies rules
     * that require >= 5 PPE/Revenue balance sheet years (e.g. GreenwaldCapex).
     */
    private fun expandBalanceTo10(symbol: String, base: List<BalanceSheetDto>): List<BalanceSheetDto> {
        if (base.size >= 10) return base.map { it.copy(symbol = symbol) }
        val newest = base.first()
        val newestYear = newest.date?.substring(0, 4)?.toIntOrNull() ?: 2024
        val result = base.map { it.copy(symbol = symbol) }.toMutableList()
        var year = newestYear - base.size
        while (result.size < 10) {
            result.add(newest.copy(symbol = symbol, date = "$year-12-31", calendarYear = year.toString()))
            year--
        }
        return result
    }

    private fun expandCashFlowTo10(symbol: String, base: List<CashFlowDto>): List<CashFlowDto> {
        if (base.size >= 10) return base.map { it.copy(symbol = symbol) }
        val newest = base.first()
        val newestYear = newest.date?.substring(0, 4)?.toIntOrNull() ?: 2024
        val result = base.map { it.copy(symbol = symbol) }.toMutableList()
        var year = newestYear - base.size
        while (result.size < 10) {
            result.add(newest.copy(symbol = symbol, date = "$year-12-31", calendarYear = year.toString()))
            year--
        }
        return result
    }
}
