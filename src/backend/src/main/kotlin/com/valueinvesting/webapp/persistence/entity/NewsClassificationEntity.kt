package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "news_classification")
class NewsClassificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "ticker", length = 20, nullable = false)
    var ticker: String = "",

    @Column(name = "news_id", length = 200, nullable = false)
    var newsId: String = "",

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "headline", columnDefinition = "TEXT")
    var headline: String? = null,

    @Column(name = "url", columnDefinition = "TEXT")
    var url: String? = null,

    @Column(name = "sentiment_class", length = 30, nullable = false)
    var sentimentClass: String = "",

    @Column(name = "motivazione", length = 250)
    var motivazione: String? = null,

    @Column(name = "classified_at", nullable = false)
    var classifiedAt: Instant = Instant.now(),
)
