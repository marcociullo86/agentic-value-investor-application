package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
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

    // `/stable/dividends?symbol={ticker}` — serie storica dividendi (Dividends
    // Company API). L'endpoint non documenta parametri `from`/`to`/`limit`:
    // ritorna la serie completa. Il filtraggio temporale (es. 20-anni per la
    // regola DividendContinuityRule, TSK-085) è demandato al consumer.
    //
    // Lista vuota = ticker senza dividendi (es. tech growth pre-2024). NON
    // deve sollevare FmpTickerNotFoundException — l'adapter ritorna
    // `emptyList()`. Le rule trattano lista vuota come INDETERMINATE.
    //
    // L'adapter ordina DESC by `date` (string ISO `yyyy-MM-dd` → lex ordering
    // == cronologico) per convenience. Il consumer può riordinare se serve.
    //
    // Cache: il caller (es. DividendDataService futuro / TSK-085) wrappa
    // questa chiamata con `FmpCacheService.getOrFetch` usando il label
    // `"dividends"`, TTL 24h. NB: la migration V003 elenca solo 4 endpoint
    // nella CHECK constraint `fmp_fin_snap_endpoint_chk` — TSK-084 aggiunge
    // `'dividends'` alla whitelist DB.
    //
    // [^src: raw/fmp_docs.md §Earnings, Dividends, Splits — Dividends Company API]
    // [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-083.md]
    fun getDividendHistory(ticker: String): List<DividendRecord>
}
