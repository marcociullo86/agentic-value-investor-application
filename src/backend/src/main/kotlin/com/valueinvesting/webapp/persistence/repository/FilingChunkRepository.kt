package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.FilingChunkEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface FilingChunkRepository : JpaRepository<FilingChunkEntity, Long> {

    fun findByFilingBlobId(filingBlobId: Long): List<FilingChunkEntity>

    fun countByFilingBlobId(filingBlobId: Long): Long

    @Modifying
    @Query(
        value = """
            INSERT INTO filing_chunks (filing_blob_id, ticker, filing_type, filing_date, chunk_index, content, embedding, metadata, created_at)
            VALUES (:filingBlobId, :ticker, :filingType, :filingDate, :chunkIndex, :content, cast(:embedding AS vector), cast(:metadata AS jsonb), now())
            ON CONFLICT (filing_blob_id, chunk_index)
            DO UPDATE SET content = EXCLUDED.content,
                          embedding = EXCLUDED.embedding,
                          metadata = EXCLUDED.metadata
        """,
        nativeQuery = true,
    )
    fun upsertChunk(
        @Param("filingBlobId") filingBlobId: Long,
        @Param("ticker") ticker: String,
        @Param("filingType") filingType: String,
        @Param("filingDate") filingDate: LocalDate?,
        @Param("chunkIndex") chunkIndex: Int,
        @Param("content") content: String,
        @Param("embedding") embedding: String?,
        @Param("metadata") metadata: String?,
    )

    @Query(
        value = """
            SELECT fc.id, fc.filing_blob_id, fc.ticker, fc.filing_type, fc.filing_date,
                   fc.chunk_index, fc.content, fc.metadata, fc.created_at,
                   (fc.embedding <=> cast(:queryVector AS vector)) AS distance
            FROM filing_chunks fc
            WHERE fc.ticker = :ticker
            ORDER BY fc.embedding <=> cast(:queryVector AS vector)
            LIMIT :topK
        """,
        nativeQuery = true,
    )
    fun findSimilar(
        @Param("queryVector") queryVector: String,
        @Param("ticker") ticker: String,
        @Param("topK") topK: Int,
    ): List<Array<Any?>>
}
