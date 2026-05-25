package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

// JPA entity mapping the `filing_blob` table (V013 migration, TSK-095).
// Column names aligned verbatim with V013__filing_blob.sql.
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-095.md]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-096.md]
@Entity
@Table(name = "filing_blob")
class FilingBlobEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String = "",

    @Column(name = "cik", length = 10, nullable = false)
    var cik: String = "",

    @Column(name = "form_type", length = 10, nullable = false)
    var formType: String = "",

    @Column(name = "accession_number", length = 30, nullable = false)
    var accessionNumber: String = "",

    @Column(name = "filing_date", nullable = false)
    var filingDate: LocalDate = LocalDate.EPOCH,

    @Column(name = "primary_doc_url", columnDefinition = "TEXT")
    var primaryDocUrl: String? = null,

    @Column(name = "html_body", columnDefinition = "TEXT")
    var htmlBody: String? = null,

    @Column(name = "html_size_bytes")
    var htmlSizeBytes: Long? = null,

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    var extractedText: String? = null,

    @Column(name = "extracted_size_bytes")
    var extractedSizeBytes: Long? = null,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now(),
)
