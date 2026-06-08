package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.WikiCorpusProperties
import com.valueinvesting.webapp.persistence.repository.FilingChunkRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Indicizza le pagine markdown wiki come secondo corpus RAG (corpus_kind=WIKI)
 * nel vector store pgvector di EP-011, per le citazioni cross-dominio del
 * Riepilogo (US-103 §"Citazioni RAG cross-dominio", ADR-030 §2).
 *
 * Pipeline (riuso EP-011): per ogni pagina con `domain ∈` domini ammessi →
 * strip del frontmatter → chunking ([FilingChunkingService]) → embedding
 * Qwen3-Embedding-0.6B 1024-dim ([EmbeddingService]) → persistenza come
 * `corpus_kind=WIKI` con `wiki_source_id` = slug (nome file senza estensione) e
 * `wiki_domain` dal frontmatter.
 *
 * **Idempotente** per pagina: delete-by-wiki_source_id + reinsert, così una
 * re-indicizzazione che produce meno chunk non lascia chunk orfani; il successivo
 * upsert sul partial unique index uq_wiki_chunks resta comunque idempotente.
 * Pura orchestrazione: nessun verdetto LLM.
 *
 * NOTA accesso runtime ai file: l'app è containerizzata e `wiki/` non è nel
 * classpath né montato dal compose corrente → la sorgente è una directory di
 * filesystem configurabile via `rag.wiki.corpus-path` ([WikiCorpusProperties]).
 * Vedi GAP be-wiki-runtime-corpus-mount (wiki/gaps.md 2026-06-08).
 *
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §2]
 * [^src: management/.../US-103.md §"Citazioni RAG cross-dominio"]
 */
@Service
class WikiCorpusIndexer(
    private val props: WikiCorpusProperties,
    private val chunkingService: FilingChunkingService,
    private val embeddingService: EmbeddingService,
    private val wikiChunkWriter: WikiChunkWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // Frontmatter YAML delimitato da '---' su righe proprie (in testa al file).
        private val FRONTMATTER = Regex("^\\s*---\\s*\\R(.*?)\\R---\\s*\\R", RegexOption.DOT_MATCHES_ALL)
        private val DOMAIN_LINE = Regex("^\\s*domain:\\s*(.+?)\\s*$", RegexOption.MULTILINE)
    }

    /**
     * Re-indicizza l'intero corpus wiki. Idempotente: re-eseguirlo riparte dai
     * file su disco e riallinea i chunk WIKI in DB. Non al boot — invocato solo
     * dall'endpoint admin triggered.
     *
     * Intenzionalmente NON-@Transactional (TSK-337 F1, allineato a TSK-306 F1): le
     * chiamate HTTP al sidecar embedding restano FUORI da ogni tx (un reindex full
     * terrebbe pinned uno slot del connection pool per minuti). Le sole DB op per-pagina
     * sono isolate in [WikiChunkWriter.replacePageChunks] (REQUIRES_NEW, bean separato →
     * niente self-invocation): una pagina fallita non rollbacka le altre.
     */
    fun reindexAll(): WikiReindexResult {
        val root = Path.of(props.corpusPath)
        if (!root.exists()) {
            log.warn("Wiki corpus path '{}' non esiste: nessuna pagina indicizzata", root)
            return WikiReindexResult(
                pagesIndexed = 0,
                chunksIndexed = 0,
                pagesSkipped = 0,
                corpusPath = root.toString(),
            )
        }

        var pagesIndexed = 0
        var chunksIndexed = 0
        var pagesSkipped = 0

        for (subdir in props.subdirs) {
            val dir = root.resolve(subdir)
            if (!dir.exists()) {
                log.warn("Sottocartella wiki '{}' assente sotto '{}', skip", subdir, root)
                continue
            }
            Files.list(dir).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.name.endsWith(".md") }
                    .sorted()
                    .forEach { file ->
                        val chunks = indexPage(file)
                        if (chunks < 0) {
                            pagesSkipped++
                        } else {
                            pagesIndexed++
                            chunksIndexed += chunks
                        }
                    }
            }
        }

        log.info(
            "Wiki corpus reindex completato: {} pagine, {} chunk (skip {}) da '{}'",
            pagesIndexed, chunksIndexed, pagesSkipped, root,
        )
        return WikiReindexResult(
            pagesIndexed = pagesIndexed,
            chunksIndexed = chunksIndexed,
            pagesSkipped = pagesSkipped,
            corpusPath = root.toString(),
        )
    }

    /**
     * Indicizza una singola pagina. Ritorna il numero di chunk persistiti, oppure
     * -1 se la pagina è stata saltata (dominio non ammesso / frontmatter assente /
     * sidecar embedding indisponibile o in timeout su questa pagina).
     *
     * L'embedding (HTTP al sidecar) avviene FUORI da ogni transazione (F1); le sole
     * DB op sono delegate a [WikiChunkWriter] in una tx REQUIRES_NEW dedicata.
     */
    private fun indexPage(file: Path): Int {
        val raw = file.readText()
        val domain = extractDomain(raw)
        if (domain == null || domain !in props.domains) {
            log.debug("Pagina '{}' saltata (domain='{}' non ammesso)", file.name, domain)
            return -1
        }

        val wikiSourceId = file.nameWithoutExtension
        val body = stripFrontmatter(raw).trim()
        if (body.isEmpty()) {
            log.debug("Pagina '{}' saltata (corpo vuoto dopo strip frontmatter)", file.name)
            return -1
        }

        val chunks = chunkingService.chunk(body)
        // F3: il fallimento del sidecar su una pagina non deve abortire l'intero
        // reindex — logga WARN, salta la pagina e prosegui (reindex idempotente).
        val embeddings = try {
            embeddingService.embed(chunks.map { it.content })
        } catch (e: EmbeddingServiceUnavailableException) {
            log.warn("Pagina wiki '{}' saltata: sidecar embedding indisponibile — {}", wikiSourceId, e.message)
            return -1
        } catch (e: EmbeddingTimeoutException) {
            log.warn("Pagina wiki '{}' saltata: timeout sidecar embedding — {}", wikiSourceId, e.message)
            return -1
        }

        wikiChunkWriter.replacePageChunks(wikiSourceId, domain, chunks, embeddings)

        log.info("Pagina wiki '{}' (domain={}) indicizzata: {} chunk", wikiSourceId, domain, chunks.size)
        return chunks.size
    }

    private fun extractDomain(raw: String): String? {
        val fm = FRONTMATTER.find(raw)?.groupValues?.get(1) ?: return null
        return DOMAIN_LINE.find(fm)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
    }

    private fun stripFrontmatter(raw: String): String = FRONTMATTER.replaceFirst(raw, "")
}

/**
 * Boundary @Transactional delle sole DB op per-pagina del reindex WIKI (TSK-337 F1).
 *
 * Estratto come @Component separato (non metodo private di [WikiCorpusIndexer]) per
 * garantire l'intercettazione del proxy Spring: un @Transactional invocato via
 * self-invocation NON è intercettato (stesso pattern di [NewsClassificationCacheStore],
 * TSK-306 F1). REQUIRES_NEW: ogni pagina committa nella propria tx short-lived, così una
 * pagina fallita non rollbacka le altre. L'embedding resta fuori da questo boundary.
 */
@Component
class WikiChunkWriter(
    private val filingChunkRepository: FilingChunkRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun replacePageChunks(
        wikiSourceId: String,
        wikiDomain: String,
        chunks: List<TextChunk>,
        embeddings: List<FloatArray>,
    ) {
        // Idempotenza: rimuovi i chunk pregressi della pagina, poi reinserisci.
        filingChunkRepository.deleteWikiChunks(wikiSourceId)
        chunks.forEachIndexed { idx, chunk ->
            filingChunkRepository.upsertWikiChunk(
                wikiSourceId = wikiSourceId,
                wikiDomain = wikiDomain,
                chunkIndex = chunk.index,
                content = chunk.content,
                embedding = vectorToString(embeddings[idx]),
                metadata = null,
            )
        }
    }
}

/**
 * Esito di una reindex del corpus wiki.
 * [pagesIndexed] = pagine effettivamente indicizzate; [pagesSkipped] = pagine
 * ignorate (dominio non ammesso o corpo vuoto); [chunksIndexed] = chunk persistiti.
 */
data class WikiReindexResult(
    val pagesIndexed: Int,
    val chunksIndexed: Int,
    val pagesSkipped: Int,
    val corpusPath: String,
)
