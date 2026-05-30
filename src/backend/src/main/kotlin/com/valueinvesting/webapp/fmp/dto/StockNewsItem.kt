package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class StockNewsItem(
    val newsId: String? = null,
    // FMP /stable/news/stock ritorna publishedDate come "yyyy-MM-dd HH:mm:ss"
    // (separatore spazio, non ISO-8601 'T'): senza pattern esplicito il
    // JavaTimeModule fallisce il decode -> RestClientException.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    val publishedDate: LocalDateTime? = null,
    val title: String? = null,
    val text: String? = null,
    val url: String? = null,
    val site: String? = null,
    val symbol: String? = null,
)
