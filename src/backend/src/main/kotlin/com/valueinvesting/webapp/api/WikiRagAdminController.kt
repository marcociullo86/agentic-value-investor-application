package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.service.WikiCorpusIndexer
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoint amministrativo **triggered** (non al boot) per la (re)indicizzazione
 * del corpus RAG WIKI (TSK-337 / US-103, ADR-030 §2). Idempotente: ogni call
 * riallinea i chunk WIKI in pgvector ripartendo dai file su disco.
 *
 * Stesso pattern di [LlmBudgetAdminController]: subtree admin (SecurityConfig
 * → hasRole ADMIN; ADR-025 §1) + `@PreAuthorize`. Controller code-first, non
 * documentato in openapi.yaml — coerente con gli altri controller admin (es.
 * LlmBudgetAdminController), che vivono solo nel codice.
 *
 * Reindex eseguito sincronicamente: l'embedding gira sul thread di richiesta. Il
 * costo (sidecar CPU) è accettabile per un'operazione admin manuale e raramente
 * invocata; il timeout del sidecar (EmbeddingsProperties) protegge da stalli.
 */
@RestController
@RequestMapping("/admin/rag/wiki")
@PreAuthorize("hasRole('ADMIN')")
class WikiRagAdminController(
    private val wikiCorpusIndexer: WikiCorpusIndexer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/reindex")
    fun reindex(): ResponseEntity<WikiReindexResponse> {
        log.info("Admin trigger: reindex corpus wiki")
        val result = wikiCorpusIndexer.reindexAll()
        return ResponseEntity.ok(
            WikiReindexResponse(
                pagesIndexed = result.pagesIndexed,
                chunksIndexed = result.chunksIndexed,
                pagesSkipped = result.pagesSkipped,
                corpusPath = result.corpusPath,
            ),
        )
    }
}

data class WikiReindexResponse(
    val pagesIndexed: Int,
    val chunksIndexed: Int,
    val pagesSkipped: Int,
    val corpusPath: String,
)
