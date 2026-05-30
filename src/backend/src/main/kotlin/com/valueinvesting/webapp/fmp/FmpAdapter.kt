package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import com.valueinvesting.webapp.fmp.dto.SearchHitDto
import com.valueinvesting.webapp.fmp.dto.SecFilingFmpDto
import com.valueinvesting.webapp.fmp.dto.StockNewsItem
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord

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

    // `/company-screener` — ritorna i candidati che soddisfano le query params FMP
    // (`marketCapMoreThan`, `marketCapLowerThan`, `sector`, `exchange`, `country`,
    // `limit`).
    // A differenza degli altri endpoint, lista vuota è un risultato legittimo
    // (zero match) e NON deve sollevare FmpTickerNotFoundException — l'adapter
    // restituisce semplicemente `emptyList()`.
    //
    // I parametri sono passati pre-mappati dal caller (SearchService) che traduce
    // `MarketCapBand` → coppia (minUsd, maxUsd) e `GicsSector` → fmpLabel.
    // Il caller è responsabile della merge multi-sector (vedi SearchService.screen).
    //
    // Parametri exchange/country (TSK-129, EP-012 Top Value Picks):
    //   - `exchange`: filtra per listing venue. Supporta comma-separated values
    //     accettati da FMP (es. "NASDAQ,NYSE"). Nullable.
    //   - `country`: filtra per country code ISO (es. "US"). Nullable.
    //
    // Param canonici verificati in raw/fmp_docs.md §Stock ScreenerAPI (riga 519
    // exchange, riga 527 country).
    //
    // [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §SearchService.screen]
    // [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-129.md]
    // [^src: raw/fmp_docs.md §Stock ScreenerAPI]
    fun screen(
        marketCapMoreThan: Long? = null,
        marketCapLowerThan: Long? = null,
        sector: String? = null,
        exchange: String? = null,
        country: String? = null,
        limit: Int = 50,
    ): List<ScreenedStockDto>

    // `/stable/search-symbol?query={q}&limit={limit}` — ricerca free-text per
    // ticker o nome azienda (TSK-272: migrato da `/api/v3/search` deprecato).
    // Ritorna 0..N hit; lista vuota è risultato legittimo (nessun match) e
    // NON deve sollevare FmpTickerNotFoundException.
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

    // `/stable/news/stock?symbols={ticker}&from=...` — last N days of news.
    // [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-042-news-sentiment-classifier/TSK-108.md]
    fun getStockNews(ticker: String, days: Int = 90): List<StockNewsItem>

    // `/stable/news/press-releases?symbols={ticker}&from=...` — comunicati stampa
    // ufficiali dell'azienda negli ultimi N giorni. Stessa shape di /news/stock
    // (riusa StockNewsItem). Affianca getStockNews nel NewsSentimentService:
    // i press release sono fonte primaria (voce dell'azienda) mentre /news/stock
    // e' copertura editoriale di terze parti. Lista vuota = nessun comunicato.
    fun getPressReleases(ticker: String, days: Int = 90): List<StockNewsItem>

    // `/stable/historical-price-eod/full?symbol={ticker}&from=...` — EOD prices.
    // [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-043-price-action-analyzer/TSK-112.md]
    fun getHistoricalEodPrices(ticker: String, days: Int = 365): List<EodPriceRecord>

    // `/stable/sec-filings-search/symbol?symbol={ticker}&formType={ft}&from={from}&to={to}&page=0&limit=...`
    // — discovery SEC filing per ticker via FMP search. Endpoint canonico
    // verificato in raw/fmp_docs.md:10815 (SEC Filings By Symbol API).
    //
    // Comportamento reale FMP (verificato sul campo): `from`/`to` OBBLIGATORI
    // (assenti → 400 BAD_REQUEST); `formType` NON filtrato lato server (l'endpoint
    // ritorna tutti i form type ordinati DESC per data). L'adapter emette una
    // chiamata distinta per ogni form type richiesto (così la richiesta "esce" con
    // formType=10-K / formType=10-Q) e filtra client-side. L'endpoint gemello
    // /form-type ignora invece il `symbol` → inutilizzabile per ticker singolo.
    //
    // La finestra temporale è calcolata da `lookbackMonths` indietro da oggi
    // (`to = today`, `from = today - lookbackMonths`). Default `lookbackMonths = 15`:
    // copre l'ultimo 10-K annuale + gli ultimi 10-Q trimestrali, con margine per
    // ritardi di deposito SEC.
    //
    // Ritorna metadata (CIK, link, finalLink, filingDate, formType) — NON il body
    // HTML. Il download HTML è demandato a SecEdgarAdapter (US-038) o
    // Filing10KQDownloaderService (TSK-096) che orchestra la pipeline completa.
    //
    // Form types tipici: "10-K", "10-Q", "10-K/A", "10-Q/A". Default: ["10-K", "10-Q"].
    // `limit` è il cap sul numero TOTALE di filing restituiti (union dei tipi),
    // non un cap per-tipo.
    //
    // Lista vuota = ticker senza filing visibili nel range. NON deve sollevare
    // FmpTickerNotFoundException — l'adapter ritorna `emptyList()`.
    //
    // Cache: il caller (Filing10KQDownloaderService, TSK-096) wrappa con
    // `FmpCacheService.getOrFetch` usando label `"sec-filings"`, TTL 24h.
    // Migration V012 aggiunge 'sec-filings' al CHECK constraint
    // `fmp_fin_snap_endpoint_chk` (whitelist endpoint).
    //
    // [^src: raw/fmp_docs.md §Sec Filings — SEC Filings By Symbol API]
    // [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-094.md]
    fun getSecFilings(
        ticker: String,
        formTypes: List<String> = listOf("10-K", "10-Q"),
        limit: Int = 10,
        lookbackMonths: Long = 15,
    ): List<SecFilingFmpDto>

    // `/stable/search-cusip?cusip={cusip}` — risolve un CUSIP (9 char alfanumerici)
    // al ticker corrispondente. Usato da InstitutionalHoldingsService (TSK-127)
    // per mappare le holding 13-F SEC (che usano CUSIP, non ticker) a simboli
    // ticker analizzabili dal resto della pipeline FMP.
    //
    // Response shape FMP (raw/fmp_docs.md §CUSIPAPI riga 281):
    //   [ { "symbol": "AAPL.NE", "companyName": "Apple Inc.",
    //       "cusip": "037833100", "marketCap": 5156676087644.16 } ]
    //
    // Ritorna `null` se CUSIP non e' riconosciuto (lista vuota o 404). Il caller
    // tratta null come "skip questa holding" (e.g. CUSIP di un bond, ETF, o
    // security non coperta da FMP).
    //
    // Error policy: in linea con searchSymbol/getDividendHistory (4xx non-429 →
    // null pragmatico; 429/5xx → FmpUnavailableException routed via Resilience4j).
    //
    // Caching: il caller (InstitutionalHoldingsService) puo' decidere se cachare
    // a livello service. Il caching a livello FmpCacheService DB e' opzionale —
    // se attivato, V021 aggiunge 'search-cusip' alla CHECK constraint whitelist
    // `fmp_fin_snap_endpoint_chk`.
    //
    // [^src: raw/fmp_docs.md §CUSIPAPI]
    // [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-127.md]
    fun searchCusip(cusip: String): String?

    // `/stable/technical-indicators/{indicator}?symbol={ticker}&periodLength={n}&timeframe={tf}`
    // — endpoint generico per indicatori tecnici (EP-013 Mr. Market Context Flags).
    //
    // Whitelist `indicator` enforced in implementazione: per EP-013 ammessi solo
    // `"rsi"` (US-056) e `"sma"` (US-057). Altri valori (ema, wma, dema, tema,
    // standarddeviation, williams, adx — vedi raw/fmp_docs.md:10385+) sono fuori
    // scope e sollevano IllegalArgumentException. Estendere la whitelist quando
    // nuovi indicator entrano in scope (no breaking change al contract).
    //
    // Semantica advisory (NON rule signal):
    //   - I context flag NON contribuiscono alla MoS o ai 13 signal del Rule
    //     Engine. Sono input "Mr. Market" per UI badge (US-056/057).
    //   - Lista vuota = ticker IPO recente (< periodLength giorni di storico)
    //     o nessun dato disponibile → evaluator degrada a INDETERMINATE.
    //
    // Error policy (idem altri endpoint):
    //   - 429 → FmpUnavailableException(429) (rate-limited, route resilienza).
    //   - 5xx → FmpUnavailableException(status).
    //   - 4xx (non 429) → emptyList() (ticker IPO recente o indicator non
    //     calcolabile, semantica "no data" coerente con /dividends).
    //   - Lista vuota / null body → emptyList().
    //
    // [^src: raw/fmp_docs.md §Technical Indicators]
    // [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-164.md]
    fun getTechnicalIndicator(
        ticker: String,
        indicator: String,
        periodLength: Int,
        timeframe: String = "1day",
    ): List<TechnicalIndicatorRecord>
}
