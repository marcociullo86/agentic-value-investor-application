package com.valueinvesting.webapp.api.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class DcfOverrideRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Za-z0-9.\\-]{1,10}$", message = "Invalid ticker symbol")
    val ticker: String,

    @field:NotBlank
    @field:Pattern(regexp = "GREENWALD|FCF_FALLBACK")
    val forcedMethod: String,
)
