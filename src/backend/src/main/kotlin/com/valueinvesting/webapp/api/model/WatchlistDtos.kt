package com.valueinvesting.webapp.api.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// DTOs aligned with openapi.yaml §components.schemas (WatchlistItemRequest,
// WatchlistItem, Watchlist).

data class WatchlistItemRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Za-z0-9.\\-]{1,10}$", message = "Invalid ticker symbol")
    val ticker: String,
)

data class WatchlistItemResponse(
    val ticker: String,
    val companyName: String?,
    val sector: String?,
    val marketCapUsd: BigDecimal?,
    val addedAt: Instant,
)

data class WatchlistResponse(
    val id: UUID,
    val name: String,
    val isDefault: Boolean,
    val items: List<WatchlistItemResponse>,
)
