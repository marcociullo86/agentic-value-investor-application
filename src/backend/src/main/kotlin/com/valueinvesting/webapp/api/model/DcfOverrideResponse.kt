package com.valueinvesting.webapp.api.model

import java.time.Instant

data class DcfOverrideResponse(
    val ticker: String,
    val forcedMethod: String,
    val createdAt: Instant,
)
