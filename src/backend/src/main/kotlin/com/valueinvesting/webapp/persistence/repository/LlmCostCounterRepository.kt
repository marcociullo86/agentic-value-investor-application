package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.LlmCostCounterEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LlmCostCounterRepository : JpaRepository<LlmCostCounterEntity, String>
