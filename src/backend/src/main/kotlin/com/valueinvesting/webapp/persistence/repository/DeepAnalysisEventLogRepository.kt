package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.DeepAnalysisEventLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DeepAnalysisEventLogRepository : JpaRepository<DeepAnalysisEventLogEntity, Long>
