package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.LlmCallLogEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LlmCallLogRepository : JpaRepository<LlmCallLogEntity, Long>
