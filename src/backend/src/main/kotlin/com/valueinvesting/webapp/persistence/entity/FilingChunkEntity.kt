package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "filing_chunks")
class FilingChunkEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "filing_blob_id", nullable = false)
    var filingBlobId: Long = 0,

    @Column(name = "ticker", length = 20, nullable = false)
    var ticker: String = "",

    @Column(name = "filing_type", length = 10, nullable = false)
    var filingType: String = "",

    @Column(name = "filing_date")
    var filingDate: LocalDate? = null,

    @Column(name = "chunk_index", nullable = false)
    var chunkIndex: Int = 0,

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    var content: String = "",

    @Column(name = "metadata", columnDefinition = "JSONB")
    var metadata: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
