package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dcf_method_override")
data class DcfMethodOverrideEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String,

    @Column(name = "forced_method", length = 32, nullable = false)
    var forcedMethod: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
