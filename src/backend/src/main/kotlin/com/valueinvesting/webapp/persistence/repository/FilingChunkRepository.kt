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

    // Gate per il ramo LLM di DeepAnalysisService.analyze: se per il ticker
    // non esiste alcun chunk indicizzato (zero righe in filing_chunks) significa
    // che nessun INGEST è andato a buon fine e MungerInversionAnalyzer non
    // potrebbe trovare contesto → FilingsNotIndexedException.
    fun countByTicker(ticker: String): Long

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
            WHERE fc.corpus_kind = 'FILING' AND fc.ticker = :ticker
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

    // ------------------------------------------------------------------ //
    // Corpus WIKI (TSK-337 / ADR-030 §2). Chiave logica idempotente:      //
    // (corpus_kind=WIKI, wiki_source_id, chunk_index) → partial unique    //
    // index uq_wiki_chunks (V033).                                        //
    // ------------------------------------------------------------------ //

    // Numero di chunk WIKI persistiti per la pagina (slug = wiki_source_id).
    @Query(
        value = "SELECT count(*) FROM filing_chunks WHERE corpus_kind = 'WIKI' AND wiki_source_id = :wikiSourceId",
        nativeQuery = true,
    )
    fun countWikiChunks(@Param("wikiSourceId") wikiSourceId: String): Long

    // Conteggio totale dei chunk WIKI indicizzati (tutti i domini).
    @Query(
        value = "SELECT count(*) FROM filing_chunks WHERE corpus_kind = 'WIKI'",
        nativeQuery = true,
    )
    fun countAllWikiChunks(): Long

    // Idempotenza alternativa per pagina: cancella i chunk WIKI di una pagina
    // prima di re-indicizzarla (usato dal WikiCorpusIndexer per gestire il caso
    // in cui una re-indicizzazione produce *meno* chunk della precedente, che il
    // solo upsert non ripulirebbe).
    @Modifying
    @Query(
        value = "DELETE FROM filing_chunks WHERE corpus_kind = 'WIKI' AND wiki_source_id = :wikiSourceId",
        nativeQuery = true,
    )
    fun deleteWikiChunks(@Param("wikiSourceId") wikiSourceId: String): Int

    // Upsert idempotente del singolo chunk WIKI sul partial unique index
    // uq_wiki_chunks (wiki_source_id, chunk_index) WHERE corpus_kind='WIKI'.
    // filing_blob_id/ticker/filing_type restano NULL (CHECK chk_filing_chunks_corpus).
    @Modifying
    @Query(
        value = """
            INSERT INTO filing_chunks (corpus_kind, wiki_source_id, wiki_domain, chunk_index, content, embedding, metadata, created_at)
            VALUES ('WIKI', :wikiSourceId, :wikiDomain, :chunkIndex, :content, cast(:embedding AS vector), cast(:metadata AS jsonb), now())
            ON CONFLICT (wiki_source_id, chunk_index) WHERE corpus_kind = 'WIKI'
            DO UPDATE SET wiki_domain = EXCLUDED.wiki_domain,
                          content     = EXCLUDED.content,
                          embedding   = EXCLUDED.embedding,
                          metadata    = EXCLUDED.metadata
        """,
        nativeQuery = true,
    )
    fun upsertWikiChunk(
        @Param("wikiSourceId") wikiSourceId: String,
        @Param("wikiDomain") wikiDomain: String,
        @Param("chunkIndex") chunkIndex: Int,
        @Param("content") content: String,
        @Param("embedding") embedding: String?,
        @Param("metadata") metadata: String?,
    )

    // Similarity search filtrata sul corpus WIKI per il Summary cross-dominio
    // (US-103 §"Citazioni RAG cross-dominio"). Se :wikiDomain è NULL il filtro di
    // dominio è disattivato (ricerca su entrambi i domini wiki). Colonne in
    // ordine coerente con findSimilar: gli indici 1=filing_blob_id (NULL per WIKI),
    // 10=wiki_source_id, 11=wiki_domain sono aggiunti in coda.
    @Query(
        value = """
            SELECT fc.id, fc.filing_blob_id, fc.ticker, fc.filing_type, fc.filing_date,
                   fc.chunk_index, fc.content, fc.metadata, fc.created_at,
                   (fc.embedding <=> cast(:queryVector AS vector)) AS distance,
                   fc.wiki_source_id, fc.wiki_domain
            FROM filing_chunks fc
            WHERE fc.corpus_kind = 'WIKI'
              AND (cast(:wikiDomain AS varchar) IS NULL OR fc.wiki_domain = :wikiDomain)
            ORDER BY fc.embedding <=> cast(:queryVector AS vector)
            LIMIT :topK
        """,
        nativeQuery = true,
    )
    fun findSimilarWiki(
        @Param("queryVector") queryVector: String,
        @Param("wikiDomain") wikiDomain: String?,
        @Param("topK") topK: Int,
    ): List<Array<Any?>>
}
