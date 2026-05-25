package com.valueinvesting.webapp.secedgar

import org.springframework.boot.context.properties.ConfigurationProperties

// Typed configuration backed by `sec.edgar.*` keys in application.yml.
//
// L'email è OBBLIGATORIA dal punto di vista SEC fair-access-policy: chiamate
// senza User-Agent identificativo (o con email palesemente di test) possono
// portare a 403 ban temporaneo. In dev il default `dev@example.com` permette di
// far girare la app localmente, ma il deployment prod DEVE settare la env var
// `SEC_EDGAR_USER_AGENT_EMAIL` a un indirizzo valido raggiungibile.
//
// `rateLimitPerSecond` = 10 è il cap SEC dichiarato; mai aumentare.
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-091.md §4,7]
// [^src: wiki/concepts/sec-filings-analysis.md §Accesso ai filing]
@ConfigurationProperties(prefix = "sec.edgar")
data class SecEdgarProperties(
    /** Base URL per submissions API. Default: `https://data.sec.gov`. */
    val baseUrl: String = "https://data.sec.gov",

    /**
     * Base URL per file pubblici (company_tickers.json e Archives/edgar/data).
     * Default: `https://www.sec.gov`.
     */
    val filesUrl: String = "https://www.sec.gov",

    val userAgent: UserAgent = UserAgent(),

    /** Cap richieste per secondo (fair-access SEC = 10, NON aumentare). */
    val rateLimitPerSecond: Int = DEFAULT_RATE_LIMIT_PER_SECOND,

    /** Timeout in secondi prima di fallire la richiesta di un token rate-limiter. */
    val rateLimitTimeoutSeconds: Long = DEFAULT_RATE_LIMIT_TIMEOUT_SECONDS,

    /**
     * TTL (giorni) della cache in-memory ticker→CIK (Caffeine expireAfterWrite).
     * Default 30: company_tickers.json SEC è quasi-statico, refresh raro (IPO/delist
     * settimanali). Alla scadenza la prima chiamata `resolveCikFromTicker` ri-fetcha
     * l'intero JSON ~3MB.
     * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-092.md §4]
     */
    val cikCacheTtlDays: Long = DEFAULT_CIK_CACHE_TTL_DAYS,

    val circuitBreaker: CircuitBreaker = CircuitBreaker(),
    val retry: Retry = Retry(),
) {
    data class UserAgent(
        /** Email valida per la fair-access-policy SEC. Env: `SEC_EDGAR_USER_AGENT_EMAIL`. */
        val email: String = "dev@example.com",
    )

    data class CircuitBreaker(
        val failureRateThreshold: Float = 50f,
        val slidingWindowSize: Int = 10,
        val minimumNumberOfCalls: Int = 5,
        val waitDurationInOpenStateSeconds: Long = 60L,
        val permittedNumberOfCallsInHalfOpenState: Int = 3,
    )

    data class Retry(
        val maxAttempts: Int = 3,
        val waitDurationMs: Long = 1000L,
    )

    companion object {
        const val DEFAULT_RATE_LIMIT_PER_SECOND = 10
        const val DEFAULT_RATE_LIMIT_TIMEOUT_SECONDS = 5L
        const val DEFAULT_CIK_CACHE_TTL_DAYS = 30L
    }
}
