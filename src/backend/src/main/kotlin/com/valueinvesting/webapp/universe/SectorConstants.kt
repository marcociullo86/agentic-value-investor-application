package com.valueinvesting.webapp.universe

// Sector universe filter per Sprint 9 EP-012 — Top Value Picks batch.
// SETTORI_BUFFETT_OK = settori GICS dove la Graham/Buffett checklist e' applicabile.
// SECTOR_BLACKLIST = settori esclusi perche' modello finanziario non si adatta:
//   - "Financial Services" / "Financials": balance sheet dominato da deposits, ROE
//     gonfiato da leverage, P/E non comparabile con altri settori. Per coerenza
//     semantica i settori "financial" sono SOLO in blacklist (non in whitelist).
//   - "Biotechnology": pre-revenue per anni, P/E negativo, fortemente binario su
//     FDA approvals.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-129.md]
// [^src: wiki/runbooks/defensive-investor-checklist.md §Universe screening]
val SETTORI_BUFFETT_OK: Set<String> = setOf(
    "Consumer Staples",
    "Consumer Discretionary",
    "Healthcare",
    "Industrials",
    "Technology",
    "Communication Services",
    "Energy",
    "Materials",
    "Real Estate",
    "Utilities",
)

val SECTOR_BLACKLIST: Set<String> = setOf(
    "Financial Services",
    "Financials",          // alias FMP/Yahoo
    "Biotechnology",
)
