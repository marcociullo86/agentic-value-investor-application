package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "moat_checklist_entry")
data class MoatChecklistEntry(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String,

    @Column(name = "moat_type", length = 40, nullable = false)
    var moatType: String,

    @Column(name = "status", length = 20, nullable = false)
    var status: String,

    @Column(name = "note", columnDefinition = "text")
    var note: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
