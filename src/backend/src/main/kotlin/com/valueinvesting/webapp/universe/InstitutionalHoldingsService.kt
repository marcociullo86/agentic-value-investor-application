package com.valueinvesting.webapp.universe

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.secedgar.SecEdgarAdapter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

// Implementazione concreta di `InstitutionalHoldingsProvider` (overlay 13-F).
//
// SCOPE — EP-012, US-047, TSK-127. Sostituisce il default
// `NoopInstitutionalHoldingsProvider` (TSK-126) via @Primary quando la property
// `universe.institutional.enabled = true` (default true; impostare a false in
// test/dev per disabilitare).
//
// PACKAGE NOTE — Il TSK-127 originale prescriveva
// `com/valueinvesting/service/`. Override post-TSK-126 al package canonico
// `com.valueinvesting.webapp.universe` per coerenza con UniverseScreenerService
// (TSK-126), che e' il consumatore del provider. Path drift documentato in
// wiki/log.md (develop entry TSK-127).
//
// PIPELINE per fund CIK (5 fund hardcoded in application.yml):
//   1. SEC EDGAR `listFilings(cik, ["13F-HR"], 1)` → metadata ultimo 13-F.
//   2. GET del directory `index.json` del filing per risolvere il nome REALE
//      del file information table (varia per filing agent: "infotable.xml",
//      "information_table.xml", "53405.xml", ...). Si scarta `primary_doc.xml`
//      (cover page) e si prende l'unico altro .xml.
//   3. SEC `downloadFilingHtml(xmlUrl)` → body XML del 13-F informationTable.
//   4. Parse XML via Jsoup (XmlParser) → estrae `<nameOfIssuer>` + `<cusip>`.
//   5. Per ogni holding: `FmpAdapter.searchCusip(cusip)` → ticker (o null →
//      skip).
//   6. Mappa a `UniverseCandidate(source=THIRTEEN_F)` + dedupe per ticker
//      uppercase.
//
// CACHE — Per ogni fund CIK il risultato finale (post-CUSIP-resolution) e'
// cached in Caffeine `institutionalHoldingsCache` con TTL 7gg
// (UniverseCacheConfig). Subsequent screen() entro 7gg → 0 HTTP SEC/FMP.
//
// ERROR HANDLING — Tutti gli step sono wrappati in `runCatching`:
//   - fund senza 13-F (lista vuota o accession null) → skip silenzioso
//   - SEC unavailable (403/429/5xx) → warn + skip questo fund (gli altri
//     continuano; l'orchestrator UniverseScreenerService a sua volta tratta
//     un'eventuale exception finale come emptyList best-effort).
//   - XML malformato o `informationtable.xml` 404 → warn + skip questo fund.
//   - CUSIP non risolvibile via FMP → skip questa holding (gli altri ticker
//     del fund proseguono).
//
// THREADING — Il provider gira sequenzialmente nei 5 fund. Parallelizzazione
// non necessaria: il caller (UniverseScreenerService) e' chiamato 1x al giorno
// dal batch top-value-picks; latenza totale stimata 5 fund * ~5s = 25s, sotto
// soglia critica.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-127.md]
// [^src: wiki/concepts/superinvestors-graham-doddsville.md §Top value fund holdings]
// [^src: wiki/runbooks/defensive-investor-checklist.md §Universe screening]
@Component
@Primary
@ConditionalOnProperty(
    name = ["universe.institutional.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class InstitutionalHoldingsService(
    private val secEdgarAdapter: SecEdgarAdapter,
    // La risoluzione CUSIP→ticker dei 13-F passa dall'unico RateLimiter FMP
    // `fmp` (280/min, condiviso online+batch — ADR-016 §4).
    private val fmpAdapter: FmpAdapter,
    private val properties: InstitutionalHoldingsProperties,
    @Qualifier("institutionalHoldingsCache")
    private val cache: Cache<String, List<UniverseCandidate>>,
    private val objectMapper: ObjectMapper,
) : InstitutionalHoldingsProvider {

    /**
     * Lista tutti i ticker presenti nei 13-F dei top value fund configurati.
     * Dedupe per ticker uppercase (lo stesso ticker tenuto da Berkshire e
     * Markel compare 1 sola volta). Tag `source = THIRTEEN_F`.
     */
    override fun thirteenFTickers(): List<UniverseCandidate> {
        if (properties.topValueFunds.isEmpty()) {
            log.info(
                "InstitutionalHoldingsService: topValueFunds vuoto in application.yml — skip",
            )
            return emptyList()
        }
        return properties.topValueFunds
            .flatMap { fund -> getFundHoldings(fund.cik, fund.name) }
            .distinctBy { it.ticker.uppercase() }
    }

    /**
     * Ritorna le holding del fund identificato dal CIK. Cache-aside via
     * Caffeine 7gg; cache miss → pipeline SEC+FMP completa.
     */
    private fun getFundHoldings(cik: String, fundName: String): List<UniverseCandidate> {
        val cacheKey = "13f-$cik"
        cache.getIfPresent(cacheKey)?.let {
            log.debug("13-F cache hit: {} ({}) — {} holdings", fundName, cik, it.size)
            return it
        }

        // Step 1 — lista filing 13F-HR (most recent quarter).
        val filings = runCatching {
            secEdgarAdapter.listFilings(cik, listOf("13F-HR"), 1)
        }.getOrElse { ex ->
            log.warn(
                "13-F listFilings failed for {} ({}): {}",
                fundName, cik, ex.message,
            )
            return emptyList()
        }

        if (filings.isEmpty()) {
            log.info("13-F: no recent filing for {} ({})", fundName, cik)
            return emptyList()
        }

        val filing = filings.first()
        val accession = filing.accessionNumber
        if (accession.isNullOrBlank()) {
            log.warn(
                "13-F: filing for {} ({}) has null accessionNumber — skip",
                fundName, cik,
            )
            return emptyList()
        }
        val accessionNoDashes = accession.replace("-", "")
        val cikNoLeadZeros = cik.trimStart('0').ifEmpty { "0" }
        val baseDir =
            "https://www.sec.gov/Archives/edgar/data/$cikNoLeadZeros/$accessionNoDashes"

        // Step 2 — risolvi il nome REALE del file information table.
        // SEC NON usa un nome canonico fisso: a seconda del filing agent il file
        // si chiama "infotable.xml", "information_table.xml", "53405.xml", ecc.
        // (verificato empiricamente 2026-05-30 sui 5 fund: nessuno usa
        // "informationtable.xml", che era hardcoded e causava 404 sistematici).
        // Strategia robusta: GET del directory index.json del filing e prendi
        // l'unico .xml diverso da primary_doc.xml (la cover page del 13-F).
        val infoTableFile = runCatching {
            resolveInfoTableFilename(baseDir)
        }.getOrElse { ex ->
            log.warn(
                "13-F index.json resolve failed for {} ({}): {}",
                fundName, cik, ex.message,
            )
            return emptyList()
        }
        if (infoTableFile == null) {
            log.info(
                "13-F: nessun information table xml nel filing index per {} ({}) — skip",
                fundName, cik,
            )
            return emptyList()
        }

        // Step 3 — scarica l'information table XML via SEC adapter.
        val xmlUrl = "$baseDir/$infoTableFile"
        val xml = runCatching {
            secEdgarAdapter.downloadFilingHtml(xmlUrl)
        }.getOrElse { ex ->
            log.warn(
                "13-F downloadFilingHtml failed for {} ({}): {}",
                fundName, cik, ex.message,
            )
            return emptyList()
        }

        if (xml.isNullOrBlank()) {
            log.info(
                "13-F: information table xml ({}) 404 or empty for {} ({}) — skip",
                infoTableFile, fundName, cik,
            )
            return emptyList()
        }

        // Step 4 — parse XML: estrai <nameOfIssuer> + <cusip>.
        val holdings = runCatching { parseInformationTable(xml) }.getOrElse { ex ->
            log.warn(
                "13-F XML parse failed for {} ({}): {}",
                fundName, cik, ex.message,
            )
            return emptyList()
        }
        log.debug(
            "13-F {} ({}): {} holdings parsed from XML",
            fundName, cik, holdings.size,
        )

        // Step 5-6 — risolvi CUSIP → ticker, mappa a UniverseCandidate.
        val resolved = holdings.mapNotNull { holding ->
            val ticker = runCatching {
                fmpAdapter.searchCusip(holding.cusip)
            }.getOrNull() ?: return@mapNotNull null
            UniverseCandidate(
                ticker = ticker,
                source = CandidateSource.THIRTEEN_F,
                // 13-F XML non documenta market cap, sector ne exchange:
                // i campi saranno arricchiti dal merge con il base screener FMP
                // in UniverseScreenerService (priorita' dedupe 13F > SCREENER).
                marketCapUsd = null,
                sector = null,
                exchange = null,
                companyName = holding.nameOfIssuer,
            )
        }.distinctBy { it.ticker.uppercase() }

        log.info(
            "13-F {} ({}): {} holdings → {} ticker resolved (CUSIP→FMP)",
            fundName, cik, holdings.size, resolved.size,
        )
        cache.put(cacheKey, resolved)
        return resolved
    }

    /**
     * Risolve il nome del file information table nella directory del filing
     * 13-F leggendo `{baseDir}/index.json`. SEC espone l'elenco file in
     * `directory.item[].name`; il 13-F contiene sempre `primary_doc.xml` (cover
     * page) piu' UN file XML con la tabella holdings, il cui nome varia per
     * filing agent. Ritorna il primo .xml != primary_doc.xml, o null se assente
     * (filing senza information table → caller skippa il fund).
     */
    private fun resolveInfoTableFilename(baseDir: String): String? {
        val indexJson = secEdgarAdapter.downloadFilingHtml("$baseDir/index.json")
            ?: return null
        val items = objectMapper.readTree(indexJson).path("directory").path("item")
        if (!items.isArray) return null
        return items
            .mapNotNull { it.get("name")?.asText()?.takeIf(String::isNotBlank) }
            .firstOrNull { name ->
                name.endsWith(".xml", ignoreCase = true) &&
                    !name.equals("primary_doc.xml", ignoreCase = true)
            }
    }

    /**
     * Parsing del 13-F informationTable XML SEC. Schema canonico:
     *   <informationTable>
     *     <infoTable>
     *       <nameOfIssuer>APPLE INC</nameOfIssuer>
     *       <titleOfClass>COM</titleOfClass>
     *       <cusip>037833100</cusip>
     *       <value>...</value>
     *       <shrsOrPrnAmt>...</shrsOrPrnAmt>
     *       ...
     *     </infoTable>
     *     ...
     *   </informationTable>
     *
     * Jsoup `Parser.xmlParser()` rispetta il case-sensitive XML (a differenza
     * del default HTML che lowercase tutto). Gia' disponibile in build.gradle.kts
     * (org.jsoup:jsoup:1.18.1, presente da TSK-096 per HTML strip filing_blob).
     */
    private fun parseInformationTable(xml: String): List<HoldingRecord> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("infoTable").mapNotNull { table ->
            val name = table.selectFirst("nameOfIssuer")?.text()?.trim()
                ?: return@mapNotNull null
            val cusip = table.selectFirst("cusip")?.text()?.trim()
                ?: return@mapNotNull null
            if (name.isBlank() || cusip.isBlank()) {
                null
            } else {
                HoldingRecord(nameOfIssuer = name, cusip = cusip)
            }
        }
    }

    /** Record interno post-parsing XML, pre-CUSIP-resolution. */
    private data class HoldingRecord(val nameOfIssuer: String, val cusip: String)

    companion object {
        private val log = LoggerFactory.getLogger(InstitutionalHoldingsService::class.java)
    }
}
