package com.valueinvesting.webapp.contextflags

// Payload del context flag RSI per /api/analysis/{ticker}.
// EP-013 / US-056.
//
// Campi:
//   - flag: classificazione semantica (OVERSOLD / NEUTRAL / OVERBOUGHT / INDETERMINATE).
//   - rsiLatest: valore RSI puntuale dal record più recente (0..100). null se
//                INDETERMINATE per dato mancante.
//   - rsiTimestamp: timestamp ISO del record FMP (formato `yyyy-MM-dd HH:mm:ss`).
//                   String per coerenza con DTO FMP (DividendRecord, EodPriceRecord).
//   - periodLength: window dell'indicatore (default 14 — convenzione di settore).
//   - timeframe: granularità FMP (default "1day").
//
// `periodLength` e `timeframe` esposti come constant defaults sono parte del
// contract API (utile al frontend per labellare il badge "RSI(14) daily" senza
// hard-coding lato client).
//
// [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-165.md]
data class MrMarketRsiFlag(
    val flag: MrMarketRsiSignal,
    val rsiLatest: Double?,
    val rsiTimestamp: String?,
    val periodLength: Int = 14,
    val timeframe: String = "1day",
)
