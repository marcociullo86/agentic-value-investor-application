package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// FMP Cash Flow Statement DTO — nullable-aware per US-004 AC.
// Migrato a `/stable` API (TSK-050): @JsonProperty mappa `filingDate`/`fiscalYear`
// dell'API stable sui nomi Kotlin storici per evitare rinomi downstream.
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
// [^src: wiki/concepts/fmp-financial-statements-stable.md]
@JsonIgnoreProperties(ignoreUnknown = true)
data class CashFlowDto(
    val date: String? = null,
    val symbol: String? = null,
    val reportedCurrency: String? = null,
    val cik: String? = null,
    @JsonProperty("filingDate") val fillingDate: String? = null,
    val acceptedDate: String? = null,
    @JsonProperty("fiscalYear") val calendarYear: String? = null,
    val period: String? = null,
    val netIncome: Double? = null,
    val depreciationAndAmortization: Double? = null,
    val deferredIncomeTax: Double? = null,
    val stockBasedCompensation: Double? = null,
    val changeInWorkingCapital: Double? = null,
    val accountsReceivables: Double? = null,
    val inventory: Double? = null,
    val accountsPayables: Double? = null,
    val otherWorkingCapital: Double? = null,
    val otherNonCashItems: Double? = null,
    val nonCashCharges: Double? = null,
    val netCashProvidedByOperatingActivities: Double? = null,
    val operatingCashFlow: Double? = null,
    val investmentsInPropertyPlantAndEquipment: Double? = null,
    val acquisitionsNet: Double? = null,
    val purchasesOfInvestments: Double? = null,
    val salesMaturitiesOfInvestments: Double? = null,
    val otherInvestingActivites: Double? = null,
    val netCashUsedForInvestingActivites: Double? = null,
    val debtRepayment: Double? = null,
    val commonStockIssued: Double? = null,
    val commonStockRepurchased: Double? = null,
    val dividendsPaid: Double? = null,
    val otherFinancingActivites: Double? = null,
    val netCashUsedProvidedByFinancingActivities: Double? = null,
    val effectOfForexChangesOnCash: Double? = null,
    val netChangeInCash: Double? = null,
    val cashAtEndOfPeriod: Double? = null,
    val cashAtBeginningOfPeriod: Double? = null,
    val capitalExpenditure: Double? = null,
    val freeCashFlow: Double? = null,
)
