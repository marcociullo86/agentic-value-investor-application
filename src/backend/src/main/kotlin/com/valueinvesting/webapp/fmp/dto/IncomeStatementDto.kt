package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// FMP Income Statement DTO — nullable-aware per "campi mancanti = assenti, mai 0".
// Migrato a `/stable` API (TSK-050): `filingDate` rimpiazza il typo storico
// `fillingDate`, `fiscalYear` rimpiazza `calendarYear`, `epsDiluted` rimpiazza
// `epsdiluted`. I nomi Kotlin restano per back-compat downstream
// (FinancialYearAligner, HistoricalSeriesService, rule engine) via @JsonProperty.
// [^src: design_&_architecture/components/backend-components.md §Validazione null safety]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
// [^src: wiki/concepts/fmp-financial-statements-stable.md]
@JsonIgnoreProperties(ignoreUnknown = true)
data class IncomeStatementDto(
    val date: String? = null,
    val symbol: String? = null,
    val reportedCurrency: String? = null,
    val cik: String? = null,
    @JsonProperty("filingDate") val fillingDate: String? = null,
    val acceptedDate: String? = null,
    @JsonProperty("fiscalYear") val calendarYear: String? = null,
    val period: String? = null,
    val revenue: Double? = null,
    val costOfRevenue: Double? = null,
    val grossProfit: Double? = null,
    val grossProfitRatio: Double? = null,
    val researchAndDevelopmentExpenses: Double? = null,
    val generalAndAdministrativeExpenses: Double? = null,
    val sellingAndMarketingExpenses: Double? = null,
    val sellingGeneralAndAdministrativeExpenses: Double? = null,
    val otherExpenses: Double? = null,
    val operatingExpenses: Double? = null,
    val costAndExpenses: Double? = null,
    val interestIncome: Double? = null,
    val interestExpense: Double? = null,
    val depreciationAndAmortization: Double? = null,
    val ebitda: Double? = null,
    val ebitdaratio: Double? = null,
    val operatingIncome: Double? = null,
    val operatingIncomeRatio: Double? = null,
    val totalOtherIncomeExpensesNet: Double? = null,
    val incomeBeforeTax: Double? = null,
    val incomeBeforeTaxRatio: Double? = null,
    val incomeTaxExpense: Double? = null,
    val netIncome: Double? = null,
    val netIncomeRatio: Double? = null,
    val eps: Double? = null,
    @JsonProperty("epsDiluted") val epsdiluted: Double? = null,
    val weightedAverageShsOut: Double? = null,
    val weightedAverageShsOutDil: Double? = null,
)
