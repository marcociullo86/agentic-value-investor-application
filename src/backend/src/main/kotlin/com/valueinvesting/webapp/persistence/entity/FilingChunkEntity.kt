package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * Riga del vector store pgvector multi-corpus `filing_chunks` (V014 + V033).
 *
 * Dalla migration V033 la tabella ospita due corpus discriminati da
 * [corpusKind]:
 *  - `FILING` (corpus storico EP-011): [filingBlobId]/[ticker]/[filingType]
 *    valorizzati, campi `wiki*` NULL.
 *  - `WIKI` (corpus EP-024/US-103): [wikiSourceId]/[wikiDomain] valorizzati,
 *    [filingBlobId]/[ticker]/[filingType] NULL.
 *
 * L'integrità per-corpus è garantita a DB dal CHECK `chk_filing_chunks_corpus`
 * (V033); qui i campi corpus-specifici sono nullable per riflettere lo schema.
 *
 * [^src: src/backend/src/main/resources/db/migration/V033__filing_chunks_add_wiki_corpus.sql]
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §2]
 */
@Entity
@Table(name = "filing_chunks")
class FilingChunkEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "corpus_kind", length = 10, nullable = false)
    var corpusKind: CorpusKind = CorpusKind.FILING,

    @Column(name = "filing_blob_id")
    var filingBlobId: Long? = null,

    @Column(name = "ticker", length = 20)
    var ticker: String? = null,

    @Column(name = "filing_type", length = 10)
    var filingType: String? = null,

    @Column(name = "filing_date")
    var filingDate: LocalDate? = null,

    @Column(name = "wiki_source_id", length = 255)
    var wikiSourceId: String? = null,

    @Column(name = "wiki_domain", length = 40)
    var wikiDomain: String? = null,

    @Column(name = "chunk_index", nullable = false)
    var chunkIndex: Int = 0,

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    var content: String = "",

    @Column(name = "metadata", columnDefinition = "JSONB")
    var metadata: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

/** Discriminante di corpus della tabella `filing_chunks` (V033). */
enum class CorpusKind {
    FILING,
    WIKI,
}
