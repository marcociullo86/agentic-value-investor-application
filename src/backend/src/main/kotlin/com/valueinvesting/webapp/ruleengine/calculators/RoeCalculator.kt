package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto

// Pure calculator: 5-year average ROE (Net Income / Shareholders Equity) ported
// from agent.py v2.6.1 `roe_medio_5y`.  Coexists with the 10-year ROE signal
// produced by RoeRule (EP-010) — ADR-020 formalises the dual-lookback policy.
//
// Stateless, no I/O, no Spring wiring.  Intended consumer: Deep Analysis
// pipeline (US-045 payload field `roe.fiveYearAvg`).
//
// [^src: design_&_architecture/decisions/ADR-020-roe-lookback-policy-deep-analysis.md §TSK-EP011-A]
// [^src: raw/agent.py §node_estrai_dati — ROE medio 5 anni]
object RoeCalculator {

    private const val LOOKBACK_5Y = 5
    private const val LOOKBACK_10Y = 10

    /**
     * Computes the arithmetic mean of yearly ROE values over the most recent
     * [LOOKBACK_5Y] fiscal years.
     *
     * ROE per year = `netIncome / totalStockholdersEquity` (fraction, NOT %).
     *
     * Edge-case contract (TSK-160):
     *  - Year with `netIncome == null` → excluded (cannot compute ROE).
     *  - Year with `totalStockholdersEquity` null or ≤ 0 → excluded.
     *  - Negative ROE (positive equity, negative net income) → included.
     *  - IPO < 5 years available → average computed on available years;
     *    [RoeAverageResult.dataPoints] reflects the real count.
     *  - All years excluded → [RoeAverageResult.average] is `null`.
     *
     * Income and balance-sheet rows are paired positionally (FMP returns both
     * newest-first, same ordering used by every other rule/calculator).
     */
    fun fiveYearAverage(
        incomeStatements: List<IncomeStatementDto>,
        balanceSheets: List<BalanceSheetDto>,
    ): RoeAverageResult = computeAverage(incomeStatements, balanceSheets, LOOKBACK_5Y)

    /**
     * 10-year counterpart of [fiveYearAverage].  Same edge-case contract,
     * wider window.  Maps to ADR-020 §Specifica payload Deep Analysis field
     * `roe.tenYearAvg` (Graham defensive stability signal).
     */
    fun tenYearAverage(
        incomeStatements: List<IncomeStatementDto>,
        balanceSheets: List<BalanceSheetDto>,
    ): RoeAverageResult = computeAverage(incomeStatements, balanceSheets, LOOKBACK_10Y)

    private fun computeAverage(
        incomeStatements: List<IncomeStatementDto>,
        balanceSheets: List<BalanceSheetDto>,
        lookback: Int,
    ): RoeAverageResult {
        val incomeWindow = incomeStatements.take(lookback)
        val balanceWindow = balanceSheets.take(lookback)

        val roeValues = mutableListOf<Double>()

        for ((inc, bal) in incomeWindow.zip(balanceWindow)) {
            val netIncome = inc.netIncome ?: continue
            val equity = bal.totalStockholdersEquity ?: continue
            if (equity <= 0.0) continue
            roeValues += netIncome / equity
        }

        if (roeValues.isEmpty()) {
            return RoeAverageResult(average = null, dataPoints = 0)
        }

        return RoeAverageResult(
            average = roeValues.sum() / roeValues.size,
            dataPoints = roeValues.size,
        )
    }
}

// Result of [RoeCalculator.fiveYearAverage].
// Maps 1-to-1 to ADR-020 §Specifica payload Deep Analysis:
//   `roe.fiveYearAvg`        → [average]
//   `roe.fiveYearDataPoints`  → [dataPoints]
data class RoeAverageResult(
    val average: Double?,
    val dataPoints: Int,
)
