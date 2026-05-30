package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.MasterPasswordEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MasterPasswordRepository : JpaRepository<MasterPasswordEntity, Long> {
    // Una sola riga attesa; prendiamo deterministicamente la prima.
    fun findTopByOrderByIdAsc(): MasterPasswordEntity?
}
