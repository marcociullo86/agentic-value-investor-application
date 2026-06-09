package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.api.model.WikiCitation
import com.valueinvesting.webapp.service.EmbeddingService
import com.valueinvesting.webapp.persistence.repository.FilingChunkRepository
import com.valueinvesting.webapp.service.vectorToString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Recupera le citazioni RAG cross-dominio per il Riepilogo (EP-024 / US-103 /
 * TSK-339). Pattern coerente con [com.valueinvesting.webapp.service.FilingRagService]
 * (similarity search), ma sul corpus **WIKI** (`corpus_kind = 'WIKI'`,
 * TSK-337 + ADR-030 §2): le pagine wiki dei domini `value-investing` e
 * `technical-analysis-trading` indicizzate via [WikiCorpusIndexer].
 *
 * Logica:
 *   1. Costruisce 2 query strutturate (una per dominio) dai 3 verdetti tipati
 *      (VI / Deep / TA) — pattern simile al ramo Munger inversion (EP-011
 *      TSK-105) ma molto piu' leggero (no LLM-driven retrieval).
 *   2. Esegue una similarity search per dominio con filtro `wiki_domain`
 *      (top-K configurabile via `summary.wiki-citations.top-k-per-domain`,
 *      default 2 → totale max 4 citazioni, US-103 §"Citazioni RAG").
 *   3. Deduplica per `wiki_source_id` mantenendo la prima occorrenza (ordine
 *      VI prima, TA poi — gate primario domina), normalizza in [WikiCitation].
 *   4. **Almeno una citazione per dominio** quando entrambi i layer hanno
 *      contribuito al verdetto (US-103 AC + ADR-030 §2): garantita dal fatto
 *      che la search e' per-dominio.
 *
 * Robustezza: ogni guasto downstream (sidecar embeddings down, DB error,
 * corpus WIKI vuoto) → lista vuota di citazioni, MAI eccezione. La Summary
 * funziona anche senza RAG.
 *
 * [^src: management/kanban/EP-024-.../US-103-.../TSK-339.md]
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §2]
 * [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Citazioni RAG cross-dominio"]
 */
@Service
class SummaryWikiCitationsService(
    private val embeddingService: EmbeddingService,
    private val filingChunkRepository: FilingChunkRepository,
    @Value("\${summary.wiki-citations.top-k-per-domain:2}")
    private val topKPerDomain: Int = 2,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        require(topKPerDomain > 0) { "summary.wiki-citations.top-k-per-domain must be > 0, got $topKPerDomain" }
    }

    /**
     * Esegue la similarity search per i due domini wiki e ritorna fino a
     * `2 × topKPerDomain` citazioni deduplicate per `wiki_source_id`.
     * Mai eccezioni: errori downstream → lista vuota.
     */
    fun fetchCitations(
        viVerdict: ViVerdict,
        deepVerdict: DeepVerdict?,
        taVerdict: EntryTimingVerdict?,
    ): List<WikiCitation> {
        val viQuery = buildViQuery(viVerdict, deepVerdict)
        val taQuery = buildTaQuery(taVerdict)

        val viCitations = runCatching { searchDomain(viQuery, DOMAIN_VI) }
            .onFailure { log.warn("RAG search WIKI domain={} failed: {}", DOMAIN_VI, it.message) }
            .getOrElse { emptyList() }

        val taCitations = if (taVerdict != null) {
            runCatching { searchDomain(taQuery, DOMAIN_TA) }
                .onFailure { log.warn("RAG search WIKI domain={} failed: {}", DOMAIN_TA, it.message) }
                .getOrElse { emptyList() }
        } else emptyList()

        // Dedupe per id (slug pagina): VI prima (gate primario), poi TA.
        val seen = linkedSetOf<String>()
        val merged = mutableListOf<WikiCitation>()
        (viCitations + taCitations).forEach { cit ->
            if (seen.add(cit.id)) merged += cit
        }
        return merged
    }

    private fun searchDomain(queryText: String, domain: String): List<WikiCitation> {
        if (queryText.isBlank()) return emptyList()
        val queryEmbedding = embeddingService.embed(listOf(queryText)).firstOrNull()
            ?: return emptyList()
        val vec = vectorToString(queryEmbedding)
        val rows = filingChunkRepository.findSimilarWiki(
            queryVector = vec,
            wikiDomain = domain,
            topK = topKPerDomain,
        )
        // Mapping colonne (vedi FilingChunkRepository.findSimilarWiki):
        //  [0]=id  [1]=filing_blob_id  [2]=ticker  [3]=filing_type  [4]=filing_date
        //  [5]=chunk_index  [6]=content  [7]=metadata  [8]=created_at  [9]=distance
        //  [10]=wiki_source_id  [11]=wiki_domain
        // De-dup per wiki_source_id intra-dominio: una stessa pagina puo'
        // matchare con piu' chunk in top-K — vogliamo una sola citazione.
        val seenSlug = linkedSetOf<String>()
        val out = mutableListOf<WikiCitation>()
        for (row in rows) {
            val slug = (row[10] as? String) ?: continue
            if (!seenSlug.add(slug)) continue
            val wikiDomain = (row[11] as? String) ?: domain
            out += WikiCitation(id = slug, anchor = null, domain = wikiDomain)
        }
        return out
    }

    private fun buildViQuery(viVerdict: ViVerdict, deepVerdict: DeepVerdict?): String {
        // Query semantica strutturata dai verdetti tipati: il sidecar embedding
        // codifica termini canonici del dominio VI ("intrinsic value", "margin
        // of safety", "rule engine", "Munger inversion"). Coerente con i
        // concept centrali del corpus (vedi EP-024 wiki_pages — value-investing).
        val sb = StringBuilder("Value investing decision criteria: ")
        sb.append(
            when (viVerdict) {
                ViVerdict.GREEN_DOMINANT -> "rule engine verdict positivo, margin of safety, intrinsic value, owner earnings."
                ViVerdict.YELLOW_DOMINANT -> "rule engine verdict ambiguo, margin of safety marginale, attendere prezzo migliore."
                ViVerdict.RED_DOMINANT -> "rule engine verdict negativo, criteria Graham non soddisfatti, evitare."
                ViVerdict.INDETERMINATE_DOMINANT -> "rule engine dati insufficienti, history limitata."
            },
        )
        when (deepVerdict) {
            DeepVerdict.RISCHIO_ESTREMO -> sb.append(" Munger inversion cascade RISCHIO_ESTREMO, panic buy vs value trap detection.")
            DeepVerdict.WATCHLIST -> sb.append(" Munger inversion WATCHLIST, value trap risk.")
            DeepVerdict.OK -> sb.append(" Munger inversion verdict OK, moat economico.")
            null -> {}
        }
        return sb.toString()
    }

    private fun buildTaQuery(taVerdict: EntryTimingVerdict?): String {
        if (taVerdict == null) return ""
        val sb = StringBuilder("Technical analysis decision layer: ")
        sb.append(
            when (taVerdict) {
                EntryTimingVerdict.ENTRY_FAVORABLE -> "Triple Screen Elder entry favorevole, pullback nel trend, RSI oversold rebound, support strutturale."
                EntryTimingVerdict.ENTRY_NEUTRAL -> "Triple Screen Elder entry neutrale, trend di lungo positivo, momentum ambiguo."
                EntryTimingVerdict.ENTRY_UNFAVORABLE -> "Triple Screen Elder entry sfavorevole, downtrend primario, RSI overbought."
                EntryTimingVerdict.WAIT -> "WAIT setup tecnico, RSI overbought attendere pullback, condizione re-entry, anti voting rigging."
                EntryTimingVerdict.INDETERMINATE -> "Trend INDETERMINATE storico EOD insufficiente, classification deterministica SMA200."
            },
        )
        sb.append(" Decisione VI vs TA, gate VI primario, layer advisory di timing.")
        return sb.toString()
    }

    companion object {
        const val DOMAIN_VI: String = "value-investing"
        const val DOMAIN_TA: String = "technical-analysis-trading"
    }
}
