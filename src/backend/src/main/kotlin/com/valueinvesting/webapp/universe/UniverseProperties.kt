package com.valueinvesting.webapp.universe

import org.springframework.boot.context.properties.ConfigurationProperties

// Properties di configurazione per UniverseScreenerService (EP-012, US-047,
// TSK-126). I default puntano allo scenario "Top Value Picks NASDAQ + NYSE >3B
// USD market cap" definito in raw/. Override via application.yml prefisso
// `universe.*`.
//
// Spring Boot 3.5 / Kotlin: @ConstructorBinding e' ridondante con costruttore
// primario (auto-bind dal Spring Boot 3.0+), quindi solo l'annotazione
// @ConfigurationProperties e' necessaria. La registrazione e' automatica
// grazie a @ConfigurationPropertiesScan in ValueInvestingWebappApplication.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-126.md §UniverseProperties]
@ConfigurationProperties(prefix = "universe")
data class UniverseProperties(
    /** Soglia minima market cap (USD) per il base screener FMP. Default 3B. */
    val marketCapMoreThan: Long = 3_000_000_000L,

    /**
     * Listing venues filtrate. FMP `/company-screener` accetta comma-separated.
     * Default "NASDAQ,NYSE" — US-047 EP-012 batch.
     */
    val exchanges: String = "NASDAQ,NYSE",

    /** Country code ISO. Default "US". */
    val country: String = "US",

    /** Cap upper-bound sulla pagina di risposta FMP. */
    val fmpMaxResults: Int = 1000,

    /** Cap finale sui candidati post-merge (13F + SCREENER + NEWS_SCOUT). */
    val capCandidates: Int = 500,

    /**
     * TTL target della cache `/company-screener` (ore). NB: la versione
     * corrente di `FmpCacheService.getOrFetch` usa TTL fisso 24h (constante
     * `FINANCIAL_TTL`) e non accetta TTL custom per chiamata; questo valore
     * documenta l'intento ma non e' wired finche' FmpCacheService non viene
     * esteso (out-of-scope TSK-126, future scope).
     */
    val cacheTtlHours: Long = 6,

    /**
     * Numero di top candidati (per market cap desc) passati a NewsScoutProvider
     * come seed. Default 200.
     */
    val newsScoutSeedTop: Int = 200,
)
