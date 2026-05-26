package com.valueinvesting.webapp.universe

import org.springframework.boot.context.properties.ConfigurationProperties

// Properties di configurazione per `NewsScoutService` (TSK-128, EP-012 US-047
// Sprint 9 — Top Value Picks).
//
// Il news-scout LLM e' OPZIONALE: master switch `enabled` (default true) lo
// disattiva via @ConditionalOnProperty lasciando il
// NoopNewsScoutProvider (TSK-126) come fallback (utile per test/dev e per
// disabilitare quando ANTHROPIC_API_KEY non e' configurato o quando il budget
// LLM mensile e' esaurito).
//
// Cap:
//   - `maxInputSeeds` (default 200) → top-N candidati dalla pipeline screener.
//   - `maxResults`    (default 50)  → cap sull'output post-parsing JSON LLM.
//   - `newsDays`      (default 30)  → finestra news passata a FmpAdapter.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-128.md]
@ConfigurationProperties(prefix = "news-scout")
data class NewsScoutProperties(
    /**
     * Master switch del provider news-scout LLM. Quando `false`, lo @Component
     * @Primary `NewsScoutService` NON viene istanziato e il default
     * `NoopNewsScoutProvider` (TSK-126) resta attivo (ritorna emptyList).
     */
    val enabled: Boolean = true,

    /**
     * Cap superiore sul numero di ticker passati come seed al prompt LLM.
     * Default 200 (top-200 per market cap desc dal base screener).
     * Sotto soglia per controllare la dimensione del prompt aggregato (1
     * chiamata LLM per 200 ticker — non 200 chiamate).
     */
    val maxInputSeeds: Int = 200,

    /**
     * Cap superiore sul numero di candidati panic-buy ritornati dal parser
     * JSON LLM. Default 50 per controllare costi a valle.
     */
    val maxResults: Int = 50,

    /**
     * Finestra news (giorni) passata a `FmpAdapter.getStockNews(ticker, days)`.
     * Default 30gg — coerente con il prompt "calo >=20% nelle ultime 4
     * settimane".
     */
    val newsDays: Int = 30,

    /**
     * Cap sul numero di news per ticker incluse nel prompt aggregato (le piu'
     * recenti per `publishedDate` desc). Default 5 — evita prompt giganti su
     * ticker high-coverage (AAPL, TSLA...).
     */
    val maxNewsPerTicker: Int = 5,

    /**
     * Cap `max_tokens` della response LLM. Default 4000 — sufficiente per un
     * array JSON di 50 entry `{ticker, motivation}`.
     */
    val llmMaxTokens: Int = 4000,
)
