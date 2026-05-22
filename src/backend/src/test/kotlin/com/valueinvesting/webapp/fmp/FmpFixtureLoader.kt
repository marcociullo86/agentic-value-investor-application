package com.valueinvesting.webapp.fmp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto

/**
 * Loads deterministic FMP JSON fixtures from `src/test/resources/fmp-fixtures/`
 * and expands short samples to 10 fiscal years for integration tests (TSK-020).
 */
object FmpFixtureLoader {

    private val mapper: ObjectMapper = jacksonObjectMapper()
    private const val TARGET_YEARS = 10

    fun loadProfile(symbol: String = "AAPL"): ProfileDto {
        val base: ProfileDto = mapper.readValue(
            requireNotNull(javaClass.classLoader.getResource("fmp-fixtures/profile-aapl.json")),
        )
        return base.copy(symbol = symbol)
    }

    fun tenYearIncomeStatements(symbol: String = "AAPL"): List<IncomeStatementDto> =
        expandYears(loadList("fmp-fixtures/income-statement-aapl.json"), symbol) { year, template ->
            template.copy(
                symbol = symbol,
                calendarYear = year.toString(),
                date = "$year-09-28",
                revenue = template.revenue?.let { it * (1.0 + (2024 - year) * 0.01) },
            )
        }

    fun tenYearBalanceSheets(symbol: String = "AAPL"): List<BalanceSheetDto> {
        val base = loadList<BalanceSheetDto>("fmp-fixtures/balance-sheet-aapl.json")
        // Base JSON has PPE only on the newest year; expanded rows used to copy the
        // last entry (no PPE) and broke Greenwald feasibility (needs ≥5 PPE/Revenue years).
        val ppeRich = base.firstOrNull {
            it.propertyPlantEquipmentNet != null || it.grossPpe != null
        } ?: base.first()
        return expandYears(base, symbol) { year, template ->
            val source = template.takeIf {
                it.propertyPlantEquipmentNet != null || it.grossPpe != null
            } ?: ppeRich
            source.copy(
                symbol = symbol,
                calendarYear = year.toString(),
                date = "$year-09-28",
            )
        }
    }

    fun tenYearCashFlows(symbol: String = "AAPL"): List<CashFlowDto> =
        expandYears(loadList("fmp-fixtures/cash-flow-aapl.json"), symbol) { year, template ->
            template.copy(
                symbol = symbol,
                calendarYear = year.toString(),
                date = "$year-09-28",
            )
        }

    fun tenYearKeyMetrics(symbol: String = "AAPL"): List<KeyMetricsDto> =
        expandYears(loadList("fmp-fixtures/key-metrics-aapl.json"), symbol) { year, template ->
            template.copy(
                symbol = symbol,
                calendarYear = year.toString(),
                date = "$year-09-28",
                roe = template.roe ?: 0.20,
                roic = template.roic ?: 0.15,
                bookValuePerShare = template.bookValuePerShare ?: 20.0,
                currentRatio = template.currentRatio ?: 2.1,
            )
        }

    fun shortCashFlows(symbol: String = "SHORT"): List<CashFlowDto> =
        tenYearCashFlows(symbol).take(2)

    fun shortBalanceSheets(symbol: String = "LOWPPE"): List<BalanceSheetDto> =
        tenYearBalanceSheets(symbol).take(2)

    private inline fun <reified T> loadList(resource: String): List<T> =
        mapper.readValue(requireNotNull(javaClass.classLoader.getResource(resource)))

    private fun <T> expandYears(
        base: List<T>,
        symbol: String,
        transform: (year: Int, template: T) -> T,
    ): List<T> {
        if (base.isEmpty()) return base
        if (base.size >= TARGET_YEARS) {
            return base.map { item ->
                when (item) {
                    is IncomeStatementDto -> item.copy(symbol = symbol) as T
                    is BalanceSheetDto -> item.copy(symbol = symbol) as T
                    is CashFlowDto -> item.copy(symbol = symbol) as T
                    is KeyMetricsDto -> item.copy(symbol = symbol) as T
                    else -> item
                }
            }
        }
        val newestYear = base.mapNotNull { yearOf(it as Any) }.maxOrNull() ?: 2024
        val template = base.last()
        val result = base.map { transform(yearOf(it as Any) ?: newestYear, it) }.toMutableList()
        var year = newestYear - base.size
        while (result.size < TARGET_YEARS) {
            result.add(transform(year, template))
            year--
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun yearOf(item: Any): Int? = when (item) {
        is IncomeStatementDto -> item.calendarYear?.toIntOrNull()
        is BalanceSheetDto -> item.calendarYear?.toIntOrNull()
        is CashFlowDto -> item.calendarYear?.toIntOrNull()
        is KeyMetricsDto -> item.calendarYear?.toIntOrNull()
        else -> null
    }
}
