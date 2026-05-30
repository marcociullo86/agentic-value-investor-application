package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Outbound FMP rate limit (Resilience4j RateLimiter).
//
// LIMITE UNICO CONDIVISO — il rate limit FMP e' per-API-key (account-wide): una
// sola key => un solo budget per TUTTO il traffico (online UI + batch notturno).
// Il piano FMP Starter consente 300 req/min; usiamo 280 come default per lasciare
// un margine di sicurezza (~7%) che assorbe il disallineamento tra la finestra
// fissa Resilience4j e quella rolling lato FMP, e l'amplificazione HTTP del Retry
// (il limiter conta 1 token/chiamata-logica, ma il Retry puo' fare fino a 3
// tentativi HTTP per token). Online e batch condividono QUESTO bucket: nessuna
// quota sprecata quando un lato e' fermo e somma garantita <= cap dell'account.
// [^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §4. Throttling backend]
@ConfigurationProperties(prefix = "fmp")
data class FmpRateLimitProperties(
    /** Max logical FMP calls per 60s refresh window. Env: `FMP_RATE_LIMIT_PER_MINUTE`. */
    val rateLimitPerMinute: Int = DEFAULT_RATE_LIMIT_PER_MINUTE,
) {
    companion object {
        // FMP Starter = 300 req/min per API key; 280 lascia ~7% di margine.
        const val DEFAULT_RATE_LIMIT_PER_MINUTE = 280
    }
}
