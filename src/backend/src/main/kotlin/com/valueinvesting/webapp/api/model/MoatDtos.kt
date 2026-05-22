package com.valueinvesting.webapp.api.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

// DTOs aligned with openapi.yaml §components.schemas
// (MoatChecklistEntryRequest, MoatChecklistEntry, MoatChecklist).

object MoatType {
    const val INTANGIBLE_ASSETS = "INTANGIBLE_ASSETS"
    const val SWITCHING_COSTS = "SWITCHING_COSTS"
    const val NETWORK_EFFECT = "NETWORK_EFFECT"
    const val COST_ADVANTAGE = "COST_ADVANTAGE"
    val ALL = listOf(INTANGIBLE_ASSETS, SWITCHING_COSTS, NETWORK_EFFECT, COST_ADVANTAGE)
}

object MoatStatus {
    const val PRESENT = "PRESENT"
    const val PARTIAL = "PARTIAL"
    const val ABSENT = "ABSENT"
    val ALL = setOf(PRESENT, PARTIAL, ABSENT)
}

data class MoatChecklistEntryRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^(INTANGIBLE_ASSETS|SWITCHING_COSTS|NETWORK_EFFECT|COST_ADVANTAGE)$",
        message = "moatType must be one of INTANGIBLE_ASSETS/SWITCHING_COSTS/NETWORK_EFFECT/COST_ADVANTAGE",
    )
    val moatType: String,

    @field:NotBlank
    @field:Pattern(
        regexp = "^(PRESENT|PARTIAL|ABSENT)$",
        message = "status must be PRESENT/PARTIAL/ABSENT",
    )
    val status: String,

    @field:Size(max = 4_000)
    val note: String? = null,
)

data class MoatChecklistEntryResponse(
    val moatType: String,
    val status: String?,
    val note: String?,
    val updatedAt: Instant?,
)

data class MoatChecklistResponse(
    val ticker: String,
    val entries: List<MoatChecklistEntryResponse>,
)
