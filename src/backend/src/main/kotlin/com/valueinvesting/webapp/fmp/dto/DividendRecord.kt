package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// FMP `/stable/dividends?symbol={ticker}` response item.
//
// Shape FMP stable (campioni reali dalla doc canonica):
//   {
//     "symbol": "AAPL",
//     "date": "2024-08-12",
//     "recordDate": "2024-08-12",
//     "paymentDate": "2024-08-15",
//     "declarationDate": "2024-08-01",
//     "dividend": 0.25,
//     "adjDividend": 0.25,
//     "yield": 0.45,
//     "frequency": "Quarterly"
//   }
//
// Date come `String?` (formato ISO `yyyy-MM-dd`) per coerenza con il pattern
// delle altre DTO (vedi IncomeStatementDto.date / filingDate / acceptedDate):
// il parsing temporale è demandato al consumer (DividendContinuityRule, TSK-085)
// che farà `LocalDate.parse` al volo. Decisione conservativa per evitare
// deserialization errors su payload FMP con formati date non canonici.
//
// Tutti i field nullable per regola US-004 ("campi mancanti = assenti, mai 0"):
// se FMP omette un campo, il consumer interpreta come "assente" senza NPE.
//
// `@JsonIgnoreProperties(ignoreUnknown = true)` per tolleranza schema:
// FMP può aggiungere/rimuovere field senza rompere la deserializzazione.
//
// Lex ordering ISO `yyyy-MM-dd` == ordering cronologico → l'adapter ordina
// DESC by `date` (string) per convenience, equivalente a ordering temporale.
//
// [^src: raw/fmp_docs.md §Earnings, Dividends, Splits — Dividends Company API]
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-083.md]
@JsonIgnoreProperties(ignoreUnknown = true)
data class DividendRecord(
    val symbol: String? = null,
    val date: String? = null,
    val recordDate: String? = null,
    val paymentDate: String? = null,
    val declarationDate: String? = null,
    val dividend: Double? = null,
    val adjDividend: Double? = null,
    val yield: Double? = null,
    val frequency: String? = null,
)
