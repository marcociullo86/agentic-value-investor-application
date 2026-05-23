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
    // Audit 2026-05-23: schema /stable usa `otherInvestingActivities` (i corretta).
    // Mantenuto nome Kotlin storico con typo per back-compat downstream.
    @JsonProperty("otherInvestingActivities") val otherInvestingActivites: Double? = null,
    // Audit 2026-05-23: schema /stable rinomina `netCashUsedForInvestingActivites`
    // → `netCashProvidedByInvestingActivities` (typo fixato + naming positivo).
    @JsonProperty("netCashProvidedByInvestingActivities") val netCashUsedForInvestingActivites: Double? = null,
    // Audit 2026-05-23: schema /stable usa `netDebtIssuance` (issuance netta) al posto di
    // `debtRepayment` (v3). Mantenuto nome Kotlin storico.
    @JsonProperty("netDebtIssuance") val debtRepayment: Double? = null,
    // Audit 2026-05-23: schema /stable rinomina `commonStockIssued` → `commonStockIssuance`.
    @JsonProperty("commonStockIssuance") val commonStockIssued: Double? = null,
    val commonStockRepurchased: Double? = null,
    // Audit 2026-05-23: schema /stable splitta `dividendsPaid` in
    // `netDividendsPaid` / `commonDividendsPaid` / `preferredDividendsPaid`.
    // Per back-compat e per il futuro US-037 (DIVIDEND_CONTINUITY_20Y) mappiamo
    // il field Kotlin storico al `netDividendsPaid` (cassa effettiva pagata in dividendi).
    @JsonProperty("netDividendsPaid") val dividendsPaid: Double? = null,
    // Audit 2026-05-23: schema /stable usa `otherFinancingActivities`.
    @JsonProperty("otherFinancingActivities") val otherFinancingActivites: Double? = null,
    // Audit 2026-05-23: schema /stable usa `netCashProvidedByFinancingActivities`.
    @JsonProperty("netCashProvidedByFinancingActivities") val netCashUsedProvidedByFinancingActivities: Double? = null,
    val effectOfForexChangesOnCash: Double? = null,
    val netChangeInCash: Double? = null,
    val cashAtEndOfPeriod: Double? = null,
    val cashAtBeginningOfPeriod: Double? = null,
    val capitalExpenditure: Double? = null,
    val freeCashFlow: Double? = null,
    // Audit 2026-05-23: campi nuovi /stable utili per analisi future
    // (es. interest coverage, tax rate effettivo). Opt-in nullable-aware.
    val incomeTaxesPaid: Double? = null,
    val interestPaid: Double? = null,
)
