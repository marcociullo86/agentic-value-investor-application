package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// FMP Balance Sheet DTO — nullable-aware per US-004 AC "campi mancanti = assenti, mai 0".
// Migrato a `/stable` API (TSK-050): @JsonProperty mappa `filingDate`/`fiscalYear`
// dell'API stable sui nomi Kotlin storici per evitare rinomi downstream.
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
// [^src: wiki/concepts/fmp-financial-statements-stable.md]
@JsonIgnoreProperties(ignoreUnknown = true)
data class BalanceSheetDto(
    val date: String? = null,
    val symbol: String? = null,
    val reportedCurrency: String? = null,
    val cik: String? = null,
    @JsonProperty("filingDate") val fillingDate: String? = null,
    val acceptedDate: String? = null,
    @JsonProperty("fiscalYear") val calendarYear: String? = null,
    val period: String? = null,
    val cashAndCashEquivalents: Double? = null,
    val shortTermInvestments: Double? = null,
    val cashAndShortTermInvestments: Double? = null,
    val netReceivables: Double? = null,
    val inventory: Double? = null,
    val otherCurrentAssets: Double? = null,
    val totalCurrentAssets: Double? = null,
    val propertyPlantEquipmentNet: Double? = null,
    val goodwill: Double? = null,
    val intangibleAssets: Double? = null,
    val goodwillAndIntangibleAssets: Double? = null,
    val longTermInvestments: Double? = null,
    val taxAssets: Double? = null,
    val otherNonCurrentAssets: Double? = null,
    val totalNonCurrentAssets: Double? = null,
    val otherAssets: Double? = null,
    val totalAssets: Double? = null,
    val accountPayables: Double? = null,
    val shortTermDebt: Double? = null,
    val taxPayables: Double? = null,
    val deferredRevenue: Double? = null,
    val otherCurrentLiabilities: Double? = null,
    val totalCurrentLiabilities: Double? = null,
    val longTermDebt: Double? = null,
    val deferredRevenueNonCurrent: Double? = null,
    val deferredTaxLiabilitiesNonCurrent: Double? = null,
    val otherNonCurrentLiabilities: Double? = null,
    val totalNonCurrentLiabilities: Double? = null,
    val otherLiabilities: Double? = null,
    val capitalLeaseObligations: Double? = null,
    val totalLiabilities: Double? = null,
    val preferredStock: Double? = null,
    val commonStock: Double? = null,
    val retainedEarnings: Double? = null,
    val accumulatedOtherComprehensiveIncomeLoss: Double? = null,
    // Audit 2026-05-23: schema /stable usa `otherTotalStockholdersEquity` (T maiuscola).
    @JsonProperty("otherTotalStockholdersEquity") val othertotalStockholdersEquity: Double? = null,
    val totalStockholdersEquity: Double? = null,
    val totalEquity: Double? = null,
    val totalLiabilitiesAndStockholdersEquity: Double? = null,
    val minorityInterest: Double? = null,
    val totalLiabilitiesAndTotalEquity: Double? = null,
    val totalInvestments: Double? = null,
    val totalDebt: Double? = null,
    val netDebt: Double? = null,
    // Useful derivation: PP&E gross is sometimes returned as separate field.
    val grossPpe: Double? = null,
)
