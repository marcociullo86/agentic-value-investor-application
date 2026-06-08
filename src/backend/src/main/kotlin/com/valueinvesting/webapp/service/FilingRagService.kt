package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import com.valueinvesting.webapp.persistence.repository.FilingChunkRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Orchestrates chunking, embedding, and pgvector persistence for SEC filings.
 * Provides similarity search over the indexed corpus.
 * [^src: wiki/concepts/analysis-api-pipeline.md §Pipeline]
 */
@Service
class FilingRagService(
    private val chunkingService: FilingChunkingService,
    private val embeddingService: EmbeddingService,
    private val filingBlobRepository: FilingBlobRepository,
    private val filingChunkRepository: FilingChunkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Idempotente: se per il filing esistono già chunk indicizzati salta
    // chunking + embedding (evita re-spesa LLM/sidecar su INGEST ripetuti per
    // lo stesso ticker). Usa `force=true` per riprocessare comunque (utile
    // dopo un cambio di chunking strategy o di modello di embedding).
    //
    // Pre-EP-011-split: il metodo veniva chiamato sempre, senza skip, dentro
    // DeepAnalysisService.analyze. Adesso l'unica chiamata viene da
    // DeepAnalysisService.ingest e i re-INGEST devono essere idempotenti per
    // costruzione.
    @Transactional
    fun indexFiling(filingBlobId: Long, force: Boolean = false): IndexResult {
        val existingCount = filingChunkRepository.countByFilingBlobId(filingBlobId)
        if (existingCount > 0 && !force) {
            log.info(
                "Filing {} already indexed ({} chunks), skip (force=false)",
                filingBlobId, existingCount,
            )
            return IndexResult(chunksIndexed = 0, skipped = true)
        }

        val blob = filingBlobRepository.findById(filingBlobId)
            .orElseThrow { IllegalArgumentException("FilingBlob not found: $filingBlobId") }

        val text = blob.extractedText
            ?: throw IllegalStateException("FilingBlob $filingBlobId has no extracted_text")

        val chunks = chunkingService.chunk(text)
        log.info("Filing {} chunked into {} pieces", filingBlobId, chunks.size)

        val embeddings = embeddingService.embed(chunks.map { it.content })

        chunks.forEachIndexed { idx, chunk ->
            val vectorStr = vectorToString(embeddings[idx])
            filingChunkRepository.upsertChunk(
                filingBlobId = filingBlobId,
                ticker = blob.ticker,
                filingType = blob.formType,
                filingDate = blob.filingDate,
                chunkIndex = chunk.index,
                content = chunk.content,
                embedding = vectorStr,
                metadata = null,
            )
        }

        log.info("Filing {} indexed: {} chunks persisted", filingBlobId, chunks.size)
        return IndexResult(chunksIndexed = chunks.size, skipped = false)
    }

    fun similaritySearch(queryText: String, ticker: String, topK: Int = 8): List<FilingChunkResult> {
        val queryEmbedding = embeddingService.embed(listOf(queryText)).first()
        val vectorStr = vectorToString(queryEmbedding)

        val rows = filingChunkRepository.findSimilar(vectorStr, ticker, topK)

        return rows.map { row ->
            FilingChunkResult(
                chunkIndex = (row[5] as Number).toInt(),
                content = row[6] as String,
                distance = (row[9] as Number).toDouble(),
                filingBlobId = (row[1] as Number).toLong(),
            )
        }
    }
}

data class FilingChunkResult(
    val chunkIndex: Int,
    val content: String,
    val distance: Double,
    val filingBlobId: Long,
)

// Esito di una singola chiamata a FilingRagService.indexFiling.
// `chunksIndexed` = chunk effettivamente persistiti; 0 se l'indicizzazione è
// stata saltata perché già presente (skipped=true).
data class IndexResult(
    val chunksIndexed: Int,
    val skipped: Boolean,
)
