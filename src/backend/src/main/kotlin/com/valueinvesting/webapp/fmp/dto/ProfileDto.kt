package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// FMP `/stable/profile?symbol={ticker}` DTO — nullable-aware per US-004 AC.
// Minimal subset used by FmpCacheService.getOrFetchProfile (price, marketCap,
// sector/industry, companyName).  Extra FMP fields are tolerated via
// @JsonIgnoreProperties.
//
// Audit 2026-05-23: schema /stable/profile espone `exchange` (es. "NASDAQ") +
// `exchangeFullName` (es. "NASDAQ Global Select"). NON espone più
// `exchangeShortName` (v3 legacy). Il field Kotlin `exchangeShortName` è
// mantenuto per back-compat downstream e popolato via @JsonProperty("exchange").
// `exchange` (full name) viene popolato via @JsonProperty("exchangeFullName").
//
// [^src: wiki/concepts/fmp-company-information.md]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
// [^src: raw/fmp_docs.json §profile-symbol response_example]
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProfileDto(
    val symbol: String? = null,
    val price: Double? = null,
    val marketCap: Double? = null,
    val companyName: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val currency: String? = null,
    @JsonProperty("exchangeFullName") val exchange: String? = null,
    @JsonProperty("exchange") val exchangeShortName: String? = null,
    val country: String? = null,
    val description: String? = null,
    val image: String? = null,
    val ipoDate: String? = null,
    val ceo: String? = null,
    val website: String? = null,
    // Audit 2026-05-23: campi /stable/profile aggiuntivi (utili per future feature).
    val beta: Double? = null,
    val lastDividend: Double? = null,
    val volume: Long? = null,
    val averageVolume: Long? = null,
    val isin: String? = null,
    val cusip: String? = null,
    val cik: String? = null,
    val isEtf: Boolean? = null,
    val isActivelyTrading: Boolean? = null,
    val isAdr: Boolean? = null,
    val isFund: Boolean? = null,
)
