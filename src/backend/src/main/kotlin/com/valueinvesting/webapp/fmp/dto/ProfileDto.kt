package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// FMP `/profile/{ticker}` DTO — nullable-aware per US-004 AC.
// Minimal subset used by FmpCacheService.getOrFetchProfile (price, marketCap,
// sector/industry, companyName).  Extra FMP fields are tolerated via
// @JsonIgnoreProperties.
// [^src: wiki/concepts/fmp-financial-statements.md §Profile]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProfileDto(
    val symbol: String? = null,
    val price: Double? = null,
    val marketCap: Double? = null,
    val companyName: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val currency: String? = null,
    val exchange: String? = null,
    val exchangeShortName: String? = null,
    val country: String? = null,
    val description: String? = null,
    val image: String? = null,
    val ipoDate: String? = null,
    val ceo: String? = null,
    val website: String? = null,
)
