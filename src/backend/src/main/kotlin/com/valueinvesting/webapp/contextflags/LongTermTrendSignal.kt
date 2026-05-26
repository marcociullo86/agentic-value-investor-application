package com.valueinvesting.webapp.contextflags

// Advisory signal (NON Rule Engine signal) per il context flag long-term trend
// basato su SMA200 (Simple Moving Average 200-day).
// EP-013 / US-057.
//
// Semantica (priceVsSmaPct = (currentPrice - sma200) / sma200):
//   - BELOW_TREND: pct < -5%  → prezzo significativamente sotto la media 200-day.
//   - NEAR_TREND: -5% <= pct <= +20% → prezzo in linea con il trend.
//   - ABOVE_TREND: pct > +20% → prezzo significativamente sopra la media 200-day.
//   - INDETERMINATE: dato non disponibile (currentPrice null, sma null,
//                    ticker IPO < 200 giorni storico).
//
// Soglie asimmetriche by design: i mercati tendono a salire over time, quindi
// la soglia "ABOVE_TREND" è più larga (+20%) della "BELOW_TREND" (-5%) per
// evitare falsi positivi su trend rialzisti normali.
//
// IMPORTANTE: questi flag NON contribuiscono alla Margin of Safety né ai 13
// signal del Rule Engine. Sono input puramente advisory per la UI.
//
// [^src: wiki/concepts/value-investing-rule-engine.md]
// [^src: management/kanban/EP-013-mr-market-context-flags/US-057-sma200-trend-context-flag/TSK-166.md]
enum class LongTermTrendSignal {
    BELOW_TREND,
    NEAR_TREND,
    ABOVE_TREND,
    INDETERMINATE,
}
