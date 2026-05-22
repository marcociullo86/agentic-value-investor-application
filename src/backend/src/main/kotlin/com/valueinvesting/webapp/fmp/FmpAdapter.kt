package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import com.valueinvesting.webapp.fmp.dto.SearchHitDto

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

    // `/stock-screener` — ritorna i candidati che soddisfano le query params FMP
    // (`marketCapMoreThan`, `marketCapLowerThan`, `sector`, `limit`).
    // A differenza degli altri endpoint, lista vuota è un risultato legittimo
    // (zero match) e NON deve sollevare FmpTickerNotFoundException — l'adapter
    // restituisce semplicemente `emptyList()`.
    //
    // I parametri sono passati pre-mappati dal caller (SearchService) che traduce
    // `MarketCapBand` → coppia (minUsd, maxUsd) e `GicsSector` → fmpLabel.
    // Il caller è responsabile della merge multi-sector (vedi SearchService.screen).
    //
    // [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §SearchService.screen]
    fun screen(
        marketCapMoreThan: Long? = null,
        marketCapLowerThan: Long? = null,
        sector: String? = null,
        limit: Int = 50,
    ): List<ScreenedStockDto>

    // `/api/v3/search?query={q}&limit={limit}` — ricerca free-text per ticker o
    // nome azienda. Ritorna 0..N hit; lista vuota è risultato legittimo (nessun
    // match) e NON deve sollevare FmpTickerNotFoundException.
    //
    // Il caller (SearchService.search) è responsabile della normalizzazione
    // uppercase della query (US-001 AC) PRIMA di invocare questo metodo.
    //
    // [^src: management/kanban/EP-001-ricerca-e-screening/US-001-ricerca-ticker-simbolo/TSK-002.md §FmpAdapter]
    // [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
    fun searchSymbol(query: String, limit: Int = 20): List<SearchHitDto>
}
