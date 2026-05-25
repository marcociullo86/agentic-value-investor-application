package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.LlmBudgetConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LlmBudgetConfigRepository : JpaRepository<LlmBudgetConfigEntity, Short>
