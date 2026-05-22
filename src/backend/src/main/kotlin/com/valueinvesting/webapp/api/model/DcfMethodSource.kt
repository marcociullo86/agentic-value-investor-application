package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "DcfMethodSource")
enum class DcfMethodSource {
    DEFAULT_POLICY,
    USER_OVERRIDE,
}
