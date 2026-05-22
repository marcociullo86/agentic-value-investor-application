package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.ScreenerResultPage
import com.valueinvesting.webapp.api.model.SearchResultItem
import com.valueinvesting.webapp.api.model.SearchResultList
import com.valueinvesting.webapp.api.model.StockProfile
import com.valueinvesting.webapp.domain.GicsSector
import com.valueinvesting.webapp.domain.MarketCapBand
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import com.valueinvesting.webapp.fmp.dto.SearchHitDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Base64

// SearchService — facade per le ricerche/screener (US-001 + US-002).
//
// Espone:
//   - `screen(criteria)`     → US-002 (TSK-005), screener parametrico.
//   - `search(query)`        → US-001 (TSK-002), ricerca free-text per ticker/nome.
//   - `validateTicker(t)`    → US-001 (TSK-002), validazione esistenza + profilo.
//
// Decisione cache: **NO cache** sullo screener in questa prima implementazione.
//   Razionale: combinatoria parametri (5 bande × 11 settori × excludeHardToPredict ×
//   limit × cursor) → hit rate prevedibilmente bassissimo, costo storage e
//   complessità invalidation > beneficio. Inoltre il preset più comune
//   (utente che esplora il dashboard) raramente è ripetuto identico tra
//   sessioni. Il rate limiting FMP è già coperto da Resilience4j RateLimiter
//   nel ResilientFmpAdapter. Follow-up Sprint 3: valutare cache short-TTL
//   (5-15 min) sui preset più gettonati se osserviamo pressure FMP.
//
// Decisione paginazione: **cursor opaque Base64**. Encode di `lastTicker` come
//   token semplice: stateless, non leakka offset, robusto a inserzioni nello
//   stream. Per MVP è sufficiente — non c'è un vero "scroll" perché FMP non
//   espone offset/cursor lato sua: il cursor lato nostro è puramente
//   client-side resume hint. nextCursor è null se la pagina ha < limit
//   elementi (= ultima pagina).
//
// Decisione multi-sector: **N chiamate SEQUENZIALI**. Razionale: parametro
//   `sector` di FMP `/stock-screener` accetta UN solo valore. Per supportare
//   N settori facciamo N chiamate (una per settore) e mergiamo i risultati.
//   Scelgliamo sequenziale anziché parallelo per:
//     (a) semplicità — niente ExecutorService dedicato in questo service
//         (TSK-018 dimostra il pattern parallelo per AnalyzeTickerService);
//     (b) rispetto del Resilience4j RateLimiter: chiamate parallele
//         rischiano di esaurire i token su un solo screener invocato;
//     (c) il caso comune è 1 sector — il fanout multi-sector è eccezione.
//   Follow-up Sprint 3: parallelizzare se i tempi di risposta diventano un
//   problema (ETA misurabile dopo deploy).
//
// [^src: design_&_architecture/components/backend-components.md §SearchService]
// [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §SearchService.screen]
// [^src: management/kanban/EP-001-ricerca-e-screening/US-001-ricerca-ticker-simbolo/TSK-002.md §SearchService]
@Service
class SearchService(
    private val fmpAdapter: FmpAdapter,
    private val fmpCacheService: FmpCacheService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ----- US-001: ricerca free-text + validazione ticker --------------------
    //
    // Decisione validazione: SOLO `IllegalArgumentException` lato service.
    //   Razionale: la pre-validazione jakarta `@RequestParam` su String non offre
    //   un guadagno netto rispetto a require(...) qui (stesso esito 400
    //   ProblemDetails via GlobalExceptionHandler), e mantiene la regola di
    //   normalizzazione (uppercase BEFORE chiamata FMP, US-001 AC) come single
    //   source of truth: il controller delega tutto, il service è l'unico
    //   custode delle business rules. Niente disallineamento tra annotazione
    //   controller e regex service.
    //
    // Decisione DTO API: RIUSO di SearchResultItem (creato da TSK-005) per
    //   /api/search items. Razionale: lo schema OpenAPI §components/schemas/
    //   SearchResultItem è condiviso fra /api/search e /api/screener — entrambi
    //   ritornano "candidato lista" con {ticker, companyName, sector?,
    //   marketCapUsd?}. Per /api/search popoliamo solo ticker+companyName
    //   perché l'endpoint FMP /search NON restituisce sector/marketCap: lasciamo
    //   null sui due campi opzionali (semantica "non disponibile da questa
    //   call"). Per arricchirli serve una seconda call /profile per hit (N+1
    //   inaccettabile per autocomplete tipico); il client UI ottiene poi i
    //   dettagli completi tramite click → /api/search/{ticker} → StockProfile.
    //   Per /api/search/{ticker} usiamo lo schema dedicato `StockProfile`
    //   (campi differenti: industry, currentPrice, dataSnapshotAt).

    fun search(query: String): SearchResultList {
        // Normalizzazione + validazione PRIMA della call FMP (US-001 AC).
        val normalized = normalizeQuery(query)

        val hits: List<SearchHitDto> = fmpAdapter.searchSymbol(
            query = normalized,
            limit = SEARCH_MAX_RESULTS,
        )

        val items: List<SearchResultItem> = hits
            .filter { !it.symbol.isNullOrBlank() }
            // FMP a volte restituisce duplicati (cross-listing). Dedupe per symbol.
            .distinctBy { it.symbol!!.uppercase() }
            .map { it.toSearchResultItem() }

        log.debug("search query='{}' hits={} mapped={}", normalized, hits.size, items.size)
        return SearchResultList(items = items)
    }

    fun validateTicker(ticker: String): StockProfile {
        // Normalizzazione + validazione PRIMA della call FMP (US-001 AC).
        val normalized = normalizeTicker(ticker)

        // Cache-aside via FmpCacheService (TTL profile 1h, US-005 §lazy upsert
        // su stocks). Se il ticker non esiste, l'adapter FMP solleva
        // FmpTickerNotFoundException → GlobalExceptionHandler → 404 RFC 9457.
        val cached = fmpCacheService.getOrFetchProfile(normalized) {
            fmpAdapter.getProfile(normalized)
        }
        val dto: ProfileDto = cached.value

        return StockProfile(
            ticker = normalized,
            // OpenAPI required: companyName. Se FMP omette → fallback al ticker
            // (stessa convenzione di SearchService.screen → toSearchResultItem).
            companyName = dto.companyName?.takeIf { it.isNotBlank() } ?: normalized,
            sector = dto.sector,
            industry = dto.industry,
            marketCapUsd = dto.marketCap,
            currentPrice = dto.price,
            dataSnapshotAt = cached.fetchedAt,
        )
    }

    // Validazione query free-text: 1..32 char, charset [A-Z0-9.-] post-uppercase.
    // La query supporta sia ticker (es. "AAPL") sia nome parziale ("APPL").
    // Charset volutamente restrittivo per ridurre il rischio injection in
    // upstream (FMP non documenta encoding rules) e perché US-001 AC limita
    // l'input a "1-6 caratteri alfanumerici"; il TSK porta il limite a 32 per
    // coprire il caso di company-name partial. Vedi gap se ulteriormente esteso.
    private fun normalizeQuery(query: String): String {
        require(query.isNotBlank()) { "query must not be blank" }
        val upper = query.trim().uppercase()
        require(upper.length in 1..SEARCH_MAX_QUERY_LENGTH) {
            "query length must be in [1, $SEARCH_MAX_QUERY_LENGTH] (got ${upper.length})"
        }
        require(QUERY_PATTERN.matches(upper)) {
            "query contains invalid characters (allowed: A-Z 0-9 . -, got '$upper')"
        }
        return upper
    }

    // Validazione ticker: 1..10 char, charset [A-Z0-9.-] post-uppercase.
    // Coerente con `openapi.yaml §components/parameters/Ticker` (pattern
    // ^[A-Za-z0-9.\-]{1,10}$).
    private fun normalizeTicker(ticker: String): String {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val upper = ticker.trim().uppercase()
        require(upper.length in 1..TICKER_MAX_LENGTH) {
            "ticker length must be in [1, $TICKER_MAX_LENGTH] (got ${upper.length})"
        }
        require(TICKER_PATTERN.matches(upper)) {
            "ticker contains invalid characters (allowed: A-Z 0-9 . -, got '$upper')"
        }
        return upper
    }

    private fun SearchHitDto.toSearchResultItem(): SearchResultItem = SearchResultItem(
        ticker = symbol!!.uppercase(),
        // OpenAPI required: companyName. Se FMP omette `name` → fallback al
        // symbol (rara, ma documentata per ETF/IPO recenti). Mai null.
        companyName = name?.takeIf { it.isNotBlank() } ?: symbol!!.uppercase(),
        // SearchResultItem espone {sector, marketCapUsd} (OpenAPI), entrambi
        // assenti nel payload /search FMP → null. Il client UI li ottiene poi
        // tramite click su risultato → /api/search/{ticker} → StockProfile.
        sector = null,
        marketCapUsd = null,
    )

    fun screen(criteria: ScreenerCriteria): ScreenerResultPage {
        require(criteria.limit in 1..MAX_LIMIT) {
            "limit must be in [1, $MAX_LIMIT] (got ${criteria.limit})"
        }

        // 1. Determina i settori effettivi: la lista richiesta meno gli
        //    hard-to-predict se il flag è attivo. Se la richiesta è vuota,
        //    il flag rimuove dal set "non specificato" → manteniamo emptyList
        //    (semantica: nessun filtro settoriale) ma applichiamo post-filter
        //    sulla risposta FMP (vedi sotto).
        val requestedSectors = if (criteria.excludeHardToPredict && criteria.sectors.isNotEmpty()) {
            criteria.sectors.filterNot { it.isHardToPredict() }
        } else {
            criteria.sectors
        }

        // Se l'utente ha esplicitamente richiesto solo settori hard-to-predict
        // + excludeHardToPredict=true → set vuoto → 0 risultati (semantica
        // utente coerente: "nessun match" non "tutti").
        if (criteria.excludeHardToPredict &&
            criteria.sectors.isNotEmpty() &&
            requestedSectors.isEmpty()
        ) {
            log.debug("screen: tutti i settori richiesti sono hard-to-predict → empty")
            return ScreenerResultPage(items = emptyList(), nextCursor = null)
        }

        // 2. Per ogni combinazione (band × sector) facciamo una call FMP.
        //    Se nessun band richiesto → null (FMP applica nessun filtro market cap).
        //    Se nessun sector richiesto → null (FMP applica nessun filtro settoriale,
        //    ma applichiamo post-filter excludeHardToPredict sulla risposta).
        val bands: List<MarketCapBand?> = criteria.marketCapBands.ifEmpty { listOf(null) }
        val sectors: List<GicsSector?> = requestedSectors.ifEmpty { listOf(null) }

        // Limit per call FMP: chiediamo `limit` interi per ogni combinazione,
        // poi tronchiamo a `limit` totale dopo merge. Soluzione semplice MVP —
        // potrebbe causare overshoot, ma FMP è sempre il bottleneck quindi
        // non ottimizziamo prematuramente.
        val perCallLimit = criteria.limit

        val collected: MutableList<ScreenedStockDto> = mutableListOf()
        for (band in bands) {
            for (sector in sectors) {
                val page = fmpAdapter.screen(
                    marketCapMoreThan = band?.minUsd,
                    marketCapLowerThan = band?.maxUsd,
                    sector = sector?.fmpLabel,
                    limit = perCallLimit,
                )
                collected.addAll(page)
            }
        }

        // 3. De-duplica per symbol (lo stesso ticker potrebbe comparire da
        //    fanout multi-sector se FMP cambiasse classificazione in-flight,
        //    e in generale come safety net).
        val deduped: List<ScreenedStockDto> = collected
            .filter { !it.symbol.isNullOrBlank() }
            .distinctBy { it.symbol!!.uppercase() }

        // 4. Post-filter excludeHardToPredict quando l'utente NON ha specificato
        //    sectors esplicitamente (caso: "tutto tranne i 3 settori difficili").
        val filtered: List<ScreenedStockDto> = if (
            criteria.excludeHardToPredict && criteria.sectors.isEmpty()
        ) {
            deduped.filterNot { dto ->
                GicsSector.fromFmpLabel(dto.sector)?.isHardToPredict() == true
            }
        } else {
            deduped
        }

        // 5. Cursor: avanza oltre `lastTicker` decodificato. Per MVP filtra
        //    solo i ticker > cursor (ordinamento lessicografico). Non garantisce
        //    consistenza forte ma è sufficiente per il caso d'uso "load more".
        val afterCursor: List<ScreenedStockDto> = criteria.cursor
            ?.let { decodeCursor(it) }
            ?.let { c -> filtered.filter { (it.symbol ?: "") > c } }
            ?: filtered

        // 6. Tronca a limit e produci nextCursor.
        val pageItems = afterCursor.take(criteria.limit)
        val nextCursor: String? =
            if (pageItems.size < criteria.limit) null
            else pageItems.lastOrNull()?.symbol?.let { encodeCursor(it) }

        return ScreenerResultPage(
            items = pageItems.map { it.toSearchResultItem() },
            nextCursor = nextCursor,
        )
    }

    private fun ScreenedStockDto.toSearchResultItem(): SearchResultItem = SearchResultItem(
        ticker = (symbol ?: "").uppercase(),
        // FMP a volte ometti companyName su listings borderline → fallback al symbol.
        companyName = companyName?.takeIf { it.isNotBlank() } ?: (symbol ?: "").uppercase(),
        sector = sector,
        marketCapUsd = marketCap,
    )

    private fun encodeCursor(lastTicker: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(lastTicker.toByteArray(StandardCharsets.UTF_8))

    private fun decodeCursor(cursor: String): String? = try {
        String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
    } catch (ex: IllegalArgumentException) {
        log.warn("invalid cursor format: '{}' — ignoring", cursor)
        null
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200

        // US-001 — ricerca free-text. Limite per page e bound charset di input.
        const val SEARCH_MAX_RESULTS = 20
        const val SEARCH_MAX_QUERY_LENGTH = 32
        // Coerente con openapi.yaml §components/parameters/Ticker pattern.
        const val TICKER_MAX_LENGTH = 10
        // Charset post-uppercase: A-Z 0-9 dot dash (no whitespace, no quote, no
        // angle brackets → blocca payload tipo "<script>").
        private val QUERY_PATTERN = Regex("^[A-Z0-9.\\-]+$")
        private val TICKER_PATTERN = Regex("^[A-Z0-9.\\-]+$")
    }
}
