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
@Table(name = "filing_blob")
class FilingBlobEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "ticker", length = 20, nullable = false)
    var ticker: String = "",

    @Column(name = "cik", length = 10)
    var cik: String? = null,

    @Column(name = "form_type", length = 10, nullable = false)
    var formType: String = "",

    @Column(name = "accession_number", length = 50)
    var accessionNumber: String? = null,

    @Column(name = "filed_at")
    var filedAt: LocalDate? = null,

    @Column(name = "html_blob", columnDefinition = "TEXT")
    var htmlBlob: String? = null,

    @Column(name = "chunkable_text", columnDefinition = "TEXT")
    var chunkableText: String? = null,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now(),

    @Column(name = "blob_size_bytes")
    var blobSizeBytes: Int? = null,
)
