package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.service.FinancialDataset

internal data class FinancialYearSlice(
    val calendarYear: Int,
    val income: IncomeStatementDto?,
    val balance: BalanceSheetDto?,
    val cashFlow: CashFlowDto?,
)

internal object FinancialYearAligner {

    fun align(dataset: FinancialDataset): List<FinancialYearSlice> {
        val years = mutableSetOf<Int>()
        dataset.income.mapNotNullTo(years) { it.calendarYear?.toIntOrNull() }
        dataset.balance.mapNotNullTo(years) { it.calendarYear?.toIntOrNull() }
        dataset.cashFlow.mapNotNullTo(years) { it.calendarYear?.toIntOrNull() }

        val incomeByYear = dataset.income.associateBy { it.calendarYear?.toIntOrNull() }
        val balanceByYear = dataset.balance.associateBy { it.calendarYear?.toIntOrNull() }
        val cashByYear = dataset.cashFlow.associateBy { it.calendarYear?.toIntOrNull() }

        return years
            .sortedDescending()
            .map { year ->
                FinancialYearSlice(
                    calendarYear = year,
                    income = incomeByYear[year],
                    balance = balanceByYear[year],
                    cashFlow = cashByYear[year],
                )
            }
    }
}
