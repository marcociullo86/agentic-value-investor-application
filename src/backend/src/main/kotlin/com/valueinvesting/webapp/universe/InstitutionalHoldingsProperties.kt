package com.valueinvesting.webapp.universe

import org.springframework.boot.context.properties.ConfigurationProperties

// Properties di configurazione per `InstitutionalHoldingsService` (TSK-127,
// EP-012 US-047 Sprint 9 — Top Value Picks).
//
// La lista `topValueFunds` e' hardcoded in application.yml — sono i 13-F filer
// dei "superinvestor" canonici (Buffett, Klarman, Markel, Pabrai, Ackman, ecc.).
// Per ognuno serve il CIK SEC 10-digit zero-padded (es. Berkshire = 0001067983),
// reperibile via EDGAR full-text search o https://www.sec.gov/cgi-bin/browse-edgar.
//
// `enabled = false` disattiva il provider via `@ConditionalOnProperty` lasciando
// il `NoopInstitutionalHoldingsProvider` come fallback (utile per test e per
// disabilitare temporaneamente quando SEC ha downtime esteso o il cron 13-F
// quarterly e' fresco e quindi inutile re-fetchare nello stesso giorno).
//
// `cacheTtlDays = 7` riflette la frequenza trimestrale di pubblicazione 13-F
// (45 giorni dopo fine trimestre): cache 7gg evita refresh frequenti e bilancia
// freschezza vs costo SEC fair-access. Il bean Caffeine
// `institutionalHoldingsCache` legge questo valore.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-127.md §Step 3]
// [^src: wiki/concepts/superinvestors-graham-doddsville.md §Top value fund holdings]
@ConfigurationProperties(prefix = "universe.institutional")
data class InstitutionalHoldingsProperties(
    /**
     * Master switch del provider 13-F. Quando `false`, lo @Component
     * @Primary `InstitutionalHoldingsService` NON viene istanziato e il
     * default `NoopInstitutionalHoldingsProvider` (TSK-126) resta attivo.
     */
    val enabled: Boolean = true,

    /**
     * Lista hardcoded dei top value fund SEC 13-F filer. CIK obbligatorio
     * 10-digit zero-padded. Override completo via application.yml.
     */
    val topValueFunds: List<ValueFund> = emptyList(),

    /**
     * TTL (giorni) della cache in-memory holdings per CIK. Default 7gg —
     * i 13-F SEC sono pubblicati trimestralmente, cache 7gg evita storm
     * SEC e CUSIP lookup ripetuti senza sacrificare freschezza.
     */
    val cacheTtlDays: Long = 7,
) {
    data class ValueFund(
        /** Nome leggibile (es. "Berkshire Hathaway"). Solo per logging. */
        val name: String,
        /** CIK SEC a 10 cifre zero-padded (es. "0001067983"). */
        val cik: String,
    )
}
