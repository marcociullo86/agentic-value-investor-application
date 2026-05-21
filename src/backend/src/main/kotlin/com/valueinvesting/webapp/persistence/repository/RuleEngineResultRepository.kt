package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.RuleEngineResultEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RuleEngineResultRepository : JpaRepository<RuleEngineResultEntity, UUID>
