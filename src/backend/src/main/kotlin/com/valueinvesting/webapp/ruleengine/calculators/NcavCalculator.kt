package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.service.FinancialDataset

// Pure calculator: Net Current Asset Value (Graham Cap.15 enterprising criterion).
//
// Formula (ADR-029 §1):
//   NCAV total      = totalCurrentAssets - totalLiabilities   (passività TOTALI: correnti + non correnti)
//   NCAV per share  = NCAV total / sharesOutstanding
//
// Source policy (ADR-029 §1 table + codebase audit):
//   totalCurrentAssets, totalLiabilities → BalanceSheetDto (latest fiscal year by ISO `date`)
//   sharesOutstanding                    → IncomeStatementDto (latest fiscal year):
//                                            preferred `weightedAverageShsOutDil`,
//                                            fallback `weightedAverageShsOut`
//
// Deviazione da ADR-029 §1 (fonte sharesOutstanding):
//   ADR-029 §1 menziona `KeyMetricsDto.sharesOutstanding` come fonte primaria e
//   `BalanceSheetStatementDto.weightedAverageShsOut` come fallback. Nel codice
//   reale di questa repo NESSUNO dei due esiste:
//     - KeyMetricsDto NON espone `sharesOutstanding` (campo derivato `netCurrentAssetValue`
//       e `grahamNetNet` sì, ma non il count azioni diretto).
//     - BalanceSheetDto NON espone `weightedAverageShsOut` (il campo vive su IncomeStatementDto).
//   Adottato il pattern canonico già usato da DcfCalculator.extractWeightedAverageShares
//   e GrahamNumberCalculator.deriveBvpsFromBalanceSheet: `IncomeStatementDto.weightedAverageShsOutDil`
//   (diluted preferito perché cattura SBC) con fallback su `weightedAverageShsOut` (basic).
//   Documentato in handoff TSK-316 + log entry develop. Possibile estensione futura
//   se FMP /stable/key-metrics o /stable/ratios espongono un campo `sharesOutstanding`
//   diretto via un nuovo adapter call (out-of-scope MVP US-096).
//
// Edge cases (PATTERN §7 r.13 — "campi mancanti = assenti, mai 0"):
//   - balance sheet vuoto                         → reason = "missing_balance_sheet"
//   - latest row con totalCurrentAssets == null   → reason = "missing_balance_sheet"
//   - latest row con totalLiabilities == null     → reason = "missing_balance_sheet"
//   - sharesOutstanding non recuperabile (income vuoto o entrambi i campi null/≤0)
//                                                 → reason = "missing_shares"
//   - sharesOutstanding ricavato ma ≤ 0            → reason = "missing_shares"
//   - ncavTotal calcolato ma ≤ 0                  → reason = "negative" (ncavTotal valorizzato,
//                                                     ncavPerShare valorizzato anch'esso —
//                                                     il consumer NcavLatestRule mapperà a RED)
//   - tutti i dati ok, ncavTotal > 0              → reason = "ok"
//
// Deterministic: no I/O, no time, no randomness — pure function over the dataset.
// Latest-year picker allineato a CurrentRatioRule / PbLatestRule (max by ISO-8601 `date`,
// fallback `calendarYear`, fallback `first()`).
//
// [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §1 + §4]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/TSK-316.md §Scope]
// [^src: wiki/concepts/net-net-stocks.md §Definizione]
// [^src: wiki/runbooks/enterprising-investor-checklist.md §Step 7 — Criterio Net-Net]
object NcavCalculator {

    /**
     * Outcome of [compute].
     *
     * - `ncavTotal`: NCAV in USD (currentAssets − totalLiabilities), or `null` when
     *   any of the two inputs is unavailable.
     * - `ncavPerShare`: NCAV / sharesOutstanding in USD per share, or `null` when
     *   either NCAV total is unavailable or shares outstanding is unavailable.
     * - `reason`: machine-readable explanation — one of:
     *   `"ok"`, `"negative"`, `"missing_balance_sheet"`, `"missing_shares"`.
     *
     * Invariant: when `reason == "ok"` both `ncavTotal` and `ncavPerShare` are non-null
     * and strictly positive. When `reason == "negative"` `ncavTotal` is non-null and
     * `≤ 0` (and `ncavPerShare` reflects the division, possibly ≤ 0 too). For the two
     * `missing_*` reasons both fields are `null`.
     */
    data class Result(
        val ncavTotal: Double?,
        val ncavPerShare: Double?,
        val reason: String,
    )

    fun compute(dataset: FinancialDataset): Result {
        val latestBalance = latestBalanceRow(dataset.balance)
            ?: return Result(ncavTotal = null, ncavPerShare = null, reason = "missing_balance_sheet")

        val currentAssets = latestBalance.totalCurrentAssets
        val totalLiabilities = latestBalance.totalLiabilities
        if (currentAssets == null || totalLiabilities == null) {
            return Result(ncavTotal = null, ncavPerShare = null, reason = "missing_balance_sheet")
        }

        val shares = extractSharesOutstanding(dataset)
        if (shares == null) {
            // NCAV total è derivabile ma per-share no: comunichiamo missing_shares
            // (downstream rule mapperà a INDETERMINATE — non vogliamo emettere un GREEN/RED
            // basato sul solo total quando il per-share resta indeterminato).
            return Result(ncavTotal = null, ncavPerShare = null, reason = "missing_shares")
        }

        val ncavTotal = currentAssets - totalLiabilities
        val ncavPerShare = ncavTotal / shares
        val reason = if (ncavTotal > 0.0) "ok" else "negative"
        return Result(ncavTotal = ncavTotal, ncavPerShare = ncavPerShare, reason = reason)
    }

    // Latest fiscal year picker — same idiom as CurrentRatioRule / PbLatestRule.
    private fun latestBalanceRow(rows: List<BalanceSheetDto>): BalanceSheetDto? {
        if (rows.isEmpty()) return null
        return rows.maxByOrNull { row -> row.date ?: row.calendarYear ?: "" } ?: rows.first()
    }

    // sharesOutstanding source — diluted preferito, fallback basic, entrambi
    // da IncomeStatementDto (vedi nota di deviazione in header). Filtra ≤ 0.
    private fun extractSharesOutstanding(dataset: FinancialDataset): Double? {
        val latestIncome = latestIncomeRow(dataset.income) ?: return null
        latestIncome.weightedAverageShsOutDil?.takeIf { it > 0.0 }?.let { return it }
        latestIncome.weightedAverageShsOut?.takeIf { it > 0.0 }?.let { return it }
        return null
    }

    private fun latestIncomeRow(rows: List<IncomeStatementDto>): IncomeStatementDto? {
        if (rows.isEmpty()) return null
        return rows.maxByOrNull { row -> row.date ?: row.calendarYear ?: "" } ?: rows.first()
    }
}
