package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto

// Interface to the Financial Modeling Prep external service.
// Tipizzata e nullable-aware: ogni metodo restituisce una List<DTO> ordinata dalla più
// recente alla più vecchia, troncata a `limit` (default 10 → analisi decennale).
// La gestione cache (TSK-010), resilience (TSK-011) e parallelism (TSK-018) sono
// applicate sopra questa interfaccia dai caller, non internamente.
// [^src: design_&_architecture/components/backend-components.md §FmpAdapter]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
interface FmpAdapter {

    fun getIncomeStatement(ticker: String, limit: Int = 10): List<IncomeStatementDto>

    fun getBalanceSheet(ticker: String, limit: Int = 10): List<BalanceSheetDto>

    fun getCashFlow(ticker: String, limit: Int = 10): List<CashFlowDto>

    fun getKeyMetrics(ticker: String, limit: Int = 10): List<KeyMetricsDto>

    // `/profile/{ticker}` — single-element list per FMP convention.  Returns the
    // first element or throws FmpTickerNotFoundException on empty response.
    fun getProfile(ticker: String): ProfileDto
}
