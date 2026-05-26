package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// FMP `/stable/technical-indicators/{indicator}?symbol={ticker}&periodLength={n}&timeframe={tf}`
// response item.
//
// Shape FMP stable (verificato in raw/fmp_docs.md §Technical Indicators, riga 10385+):
//   {
//     "date": "2024-08-12 16:00:00",
//     "open": 220.5,
//     "high": 225.0,
//     "low": 219.8,
//     "close": 223.4,
//     "volume": 51234567,
//     "value": 45.6         // RSI 0..100 / SMA $ price / etc.
//   }
//
// `date` come `String?` (formato ISO `yyyy-MM-dd HH:mm:ss`) per coerenza con
// le altre DTO (DividendRecord, EodPriceRecord). Il consumer (RsiContextEvaluator,
// LongTermTrendEvaluator) usa max by string per ordinamento lex == cronologico.
//
// `value` è il valore dell'indicatore al record:
//   - RSI: 0..100 (oversold < 30, overbought > 70)
//   - SMA: prezzo medio in $ (es. SMA200 = media chiusure ultimi 200 giorni)
//
// Tutti i field nullable per regola US-004 ("campi mancanti = assenti, mai 0"):
// se FMP omette un campo (es. ticker IPO < periodLength giorni di storico),
// il consumer interpreta come INDETERMINATE senza NPE.
//
// `@JsonIgnoreProperties(ignoreUnknown = true)` per tolleranza schema.
//
// [^src: raw/fmp_docs.md §Technical Indicators]
// [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-164.md]
@JsonIgnoreProperties(ignoreUnknown = true)
data class TechnicalIndicatorRecord(
    val date: String? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val close: Double? = null,
    val volume: Long? = null,
    val value: Double? = null,
)
