package com.valueinvesting.webapp.service

/**
 * Closed set of verdict outcomes for the Munger cascade (US-044).
 * Order matters: evaluated top-to-bottom in the cascade.
 */
enum class VerdictClass {
    APPROVATO_PANIC_BUY,
    APPROVATO,
    WATCHLIST,
    BOCCIATO_NUMERICO,
    BOCCIATO_QUALITATIVO,
    BOCCIATO_VALUE_TRAP,
}
