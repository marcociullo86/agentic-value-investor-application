package com.valueinvesting.webapp.contextflags

// Payload del context flag long-term trend (SMA200) per /api/analysis/{ticker}.
// EP-013 / US-057.
//
// Campi:
//   - flag: classificazione semantica.
//   - sma200Latest: valore SMA200 in $ dal record più recente FMP.
//   - currentPrice: prezzo corrente del ticker (passato esplicitamente
//                   dall'AnalyzeTickerService, non letto dal FinancialDataset).
//   - priceVsSmaPct: scarto percentuale prezzo vs SMA, in formato decimale
//                    (es. -0.20 per -20%, +0.05 per +5%). null se INDETERMINATE.
//   - smaTimestamp: timestamp ISO del record FMP.
//   - periodLength: 200 by convention.
//   - timeframe: "1day" by convention.
//
// [^src: management/kanban/EP-013-mr-market-context-flags/US-057-sma200-trend-context-flag/TSK-166.md]
data class LongTermTrendFlag(
    val flag: LongTermTrendSignal,
    val sma200Latest: Double?,
    val currentPrice: Double?,
    val priceVsSmaPct: Double?,
    val smaTimestamp: String?,
    val periodLength: Int = 200,
    val timeframe: String = "1day",
)
