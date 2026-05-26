package com.valueinvesting.webapp.contextflags

// Advisory signal (NON Rule Engine signal) per il context flag RSI 14-day.
// EP-013 / US-056: Mr. Market overreaction indicator.
//
// Semantica (RSI = Relative Strength Index, 0..100):
//   - OVERSOLD: rsi < 30   → mercato potenzialmente eccessivamente pessimista.
//   - OVERBOUGHT: rsi > 70 → mercato potenzialmente eccessivamente ottimista.
//   - NEUTRAL: 30 <= rsi <= 70 → nessuna segnalazione di estremi.
//   - INDETERMINATE: dato non disponibile (FMP empty list, network error,
//                    ticker IPO recente).
//
// IMPORTANTE: questi flag NON contribuiscono alla Margin of Safety né ai 13
// signal del Rule Engine. Sono input puramente advisory per la UI (badge in
// alto su /api/analysis/{ticker}, US-056 frontend TSK-168).
//
// [^src: wiki/syntheses/graham-investing-philosophy.md §Mr. Market]
// [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-165.md]
enum class MrMarketRsiSignal {
    OVERSOLD,
    NEUTRAL,
    OVERBOUGHT,
    INDETERMINATE,
}
