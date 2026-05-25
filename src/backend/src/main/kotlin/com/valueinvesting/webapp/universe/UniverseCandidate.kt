package com.valueinvesting.webapp.universe

// DTO ritornato da UniverseScreenerService.screen() — rappresenta un candidato
// dell'universo di partenza per il batch "Top Value Picks" (EP-012, US-047).
//
// `source` tagga la provenienza del candidato per il dedupe a valle (priorità:
// 13F > SCREENER > NEWS_SCOUT) e per il logging del breakdown.
//
// I campi sono tutti nullable a parte `ticker` e `source` perché:
//   - FMP `/company-screener` può ritornare entry con `marketCap`/`sector`/
//     `exchange` mancanti (osservato in pre-IPO, OTC, ticker delisted).
//   - I provider 13-F e news-scout (TSK-127/128) potrebbero arrivare con set
//     diverso di campi (es. 13-F ha CIK ma non sector).
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-126.md]
data class UniverseCandidate(
    val ticker: String,
    val source: CandidateSource,
    val marketCapUsd: Long? = null,
    val sector: String? = null,
    val exchange: String? = null,
    val companyName: String? = null,
)

// Provenienza del candidato — usata sia per dedupe ordinato sia per il
// breakdown loggato a fine `screen()`.
enum class CandidateSource {
    /** FMP /company-screener (NASDAQ + NYSE, marketCap > 3B USD). */
    SCREENER,

    /** Overlay 13-F SEC filing dei top value fund (TSK-127). */
    THIRTEEN_F,

    /** News scout opzionale post-screener su top-200 (TSK-128). */
    NEWS_SCOUT,
}
