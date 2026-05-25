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

    @Transactional
    fun indexFiling(filingBlobId: Long) {
        val blob = filingBlobRepository.findById(filingBlobId)
            .orElseThrow { IllegalArgumentException("FilingBlob not found: $filingBlobId") }

        val text = blob.chunkableText
            ?: throw IllegalStateException("FilingBlob $filingBlobId has no chunkable_text")

        val chunks = chunkingService.chunk(text)
        log.info("Filing {} chunked into {} pieces", filingBlobId, chunks.size)

        val embeddings = embeddingService.embed(chunks.map { it.content })

        chunks.forEachIndexed { idx, chunk ->
            val vectorStr = vectorToString(embeddings[idx])
            filingChunkRepository.upsertChunk(
                filingBlobId = filingBlobId,
                ticker = blob.ticker,
                filingType = blob.formType,
                filingDate = blob.filedAt,
                chunkIndex = chunk.index,
                content = chunk.content,
                embedding = vectorStr,
                metadata = null,
            )
        }

        log.info("Filing {} indexed: {} chunks persisted", filingBlobId, chunks.size)
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

    private fun vectorToString(vector: FloatArray): String {
        return vector.joinToString(",", prefix = "[", postfix = "]")
    }
}

data class FilingChunkResult(
    val chunkIndex: Int,
    val content: String,
    val distance: Double,
    val filingBlobId: Long,
)
