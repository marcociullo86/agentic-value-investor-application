package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.NewsClassificationEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface NewsClassificationRepository : JpaRepository<NewsClassificationEntity, Long> {

    fun findByNewsId(newsId: String): NewsClassificationEntity?

    fun findByTickerAndClassifiedAtAfter(ticker: String, after: Instant): List<NewsClassificationEntity>

    fun existsByNewsId(newsId: String): Boolean
}
