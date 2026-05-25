package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class StockNewsItem(
    val newsId: String? = null,
    val publishedDate: LocalDateTime? = null,
    val title: String? = null,
    val text: String? = null,
    val url: String? = null,
    val site: String? = null,
    val symbol: String? = null,
)
