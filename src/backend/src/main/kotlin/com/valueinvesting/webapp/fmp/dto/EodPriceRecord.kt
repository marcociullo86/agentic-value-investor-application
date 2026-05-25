package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodPriceRecord(
    val date: LocalDate? = null,
    val close: Double? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Long? = null,
)
