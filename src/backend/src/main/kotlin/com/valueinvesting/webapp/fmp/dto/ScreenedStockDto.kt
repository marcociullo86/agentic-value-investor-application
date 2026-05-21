package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// FMP `/api/v3/stock-screener` response item.
// FMP restituisce un array di candidati con il subset di campi necessari allo
// screener parametrico (US-002): symbol/companyName + sector + market cap +
// price/currency. I campi extra sono ignorati via @JsonIgnoreProperties.
//
// [^src: wiki/sources/vi-07-risoluzione-q002-q003.md §Classificazione Settoriale (GICS)]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
@JsonIgnoreProperties(ignoreUnknown = true)
data class ScreenedStockDto(
    val symbol: String? = null,
    val companyName: String? = null,
    val marketCap: Double? = null,
    val sector: String? = null,
    val industry: String? = null,
    val price: Double? = null,
    val beta: Double? = null,
    val volume: Long? = null,
    val exchange: String? = null,
    val exchangeShortName: String? = null,
    val country: String? = null,
    val isEtf: Boolean? = null,
    val isActivelyTrading: Boolean? = null,
)
