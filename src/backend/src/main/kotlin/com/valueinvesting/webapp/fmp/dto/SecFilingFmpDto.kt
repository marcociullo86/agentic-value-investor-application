package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// FMP `/stable/sec-filings-search/symbol?symbol={ticker}` response item.
//
// Shape FMP stable (campioni reali derivati dalla doc canonica SEC Filings By
// Symbol — vedi raw/fmp_docs.md §Sec Filings):
//   {
//     "symbol": "AAPL",
//     "cik": "0000320193",
//     "filingDate": "2024-11-01",
//     "acceptedDate": "2024-11-01 06:01:36",
//     "formType": "10-K",
//     "link": "https://www.sec.gov/Archives/edgar/data/320193/.../primary-doc.htm",
//     "finalLink": "https://www.sec.gov/Archives/edgar/data/320193/.../aapl-20240928.htm"
//   }
//
// `link` = URL EDGAR alla pagina indice del filing (filing browser).
// `finalLink` = URL canonico al primary document HTML dopo redirect, candidato
// preferito per la pipeline download (Filing10KQDownloaderService, TSK-096).
//
// Date come `String?` (formato ISO `yyyy-MM-dd` per `filingDate`, ISO datetime
// `yyyy-MM-dd HH:mm:ss` per `acceptedDate`) per coerenza con il pattern delle
// altre DTO FMP (DividendRecord.date, IncomeStatementDto.filingDate): il parsing
// temporale e' demandato al consumer. Decisione conservativa per evitare
// deserialization errors su payload FMP con formati datetime non canonici.
//
// Tutti i field nullable per regola US-004 ("campi mancanti = assenti, mai 0"):
// se FMP omette un campo, il consumer interpreta come "assente" senza NPE.
//
// `@JsonIgnoreProperties(ignoreUnknown = true)` per tolleranza schema: FMP puo'
// aggiungere/rimuovere field senza rompere la deserializzazione.
//
// NOTA: questa DTO e' un indice secondario di cross-validation. Il download
// HTML del filing avviene via SecEdgarAdapter (US-038, SEC EDGAR raw API),
// fonte autoritativa. FmpAdapter.getSecFilings serve come discovery rapida
// con metadata gia' parsati (CIK, link canonical) per Filing10KQDownloaderService
// (TSK-096) che potra' usare entrambi per coerenza.
//
// [^src: raw/fmp_docs.md §Sec Filings — SEC Filings By Symbol API]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-094.md]
@JsonIgnoreProperties(ignoreUnknown = true)
data class SecFilingFmpDto(
    val symbol: String? = null,
    val cik: String? = null,
    val filingDate: String? = null,
    val acceptedDate: String? = null,
    val formType: String? = null,
    val link: String? = null,
    val finalLink: String? = null,
)
