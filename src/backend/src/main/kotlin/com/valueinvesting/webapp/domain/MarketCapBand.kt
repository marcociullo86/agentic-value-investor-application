package com.valueinvesting.webapp.domain

// Fasce di capitalizzazione di mercato (USD) esposte dallo screener US-002.
// Soglie hardcoded conformi a [[vi-07-risoluzione-q002-q003]] §Fasce di Capitalizzazione di Mercato.
//
// - `minUsd` inclusivo / `maxUsd` esclusivo (convenzione half-open).
// - `MEGA.maxUsd == null` → fascia aperta verso l'alto.
// - Nano Cap (< $50M) escluso per design (illiquidità + rischio frode).
//
// I valori sono in USD assoluti come Long: il payload FMP usa `marketCap`
// in unità intere USD (es. $3T = 3_000_000_000_000) → confronto diretto.
//
// [^src: wiki/sources/vi-07-risoluzione-q002-q003.md §Fasce di Capitalizzazione di Mercato]
// [^src: design_&_architecture/api/openapi.yaml §/api/screener parameters.marketCap]
// [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §Enum MarketCapBand]
enum class MarketCapBand(
    val minUsd: Long?,
    val maxUsd: Long?,
) {
    MICRO(minUsd = 50_000_000L,         maxUsd = 300_000_000L),
    SMALL(minUsd = 300_000_000L,        maxUsd = 2_000_000_000L),
    MID(minUsd = 2_000_000_000L,        maxUsd = 10_000_000_000L),
    LARGE(minUsd = 10_000_000_000L,     maxUsd = 200_000_000_000L),
    MEGA(minUsd = 200_000_000_000L,     maxUsd = null),
    ;

    // True se la capitalizzazione in USD ricade nel range della fascia.
    fun contains(marketCapUsd: Double): Boolean {
        val min = minUsd?.toDouble() ?: Double.NEGATIVE_INFINITY
        val max = maxUsd?.toDouble() ?: Double.POSITIVE_INFINITY
        return marketCapUsd >= min && marketCapUsd < max
    }
}
