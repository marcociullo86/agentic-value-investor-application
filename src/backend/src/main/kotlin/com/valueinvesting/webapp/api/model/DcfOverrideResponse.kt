package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "DcfOverride")
data class DcfOverrideResponse(
    val ticker: String,
    val forcedMethod: String,
    val createdAt: Instant,
)
