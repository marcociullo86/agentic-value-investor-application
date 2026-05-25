package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FilingBlobRepository : JpaRepository<FilingBlobEntity, Long>
