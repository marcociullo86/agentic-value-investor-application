package com.valueinvesting.webapp.api.model

import java.time.LocalDate

// Response DTOs per GET /api/top-picks (EP-012, US-050, TSK-138).
//
// `runDate` può essere null quando il DB non ha ancora prodotto alcun batch
// (es. prima esecuzione del job @Scheduled cron 02:00 UTC). In quel caso il
// service risponde 200 con runDate=null, total=0, items=[] — coerente con
// AC#3 del TSK ("date senza run → 200 total=0").
//
// `marginOfSafety` esposto come Double (precision a 4 decimali rispetto allo
// storage NUMERIC(10,4) lato DB — sufficiente per UI).
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-050-endpoint-top-picks/TSK-138.md]
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/persistence/entity/TopValuePickEntity.kt]
data class TopPicksPageResponse(
    val runDate: LocalDate?,
    val page: Int,
    val size: Int,
    val total: Int,
    val items: List<TopPickItemDto>,
)

data class TopPickItemDto(
    val ticker: String,
    val rankPosition: Int,
    val verdettoClasse: String,
    val marginOfSafety: Double?,
    val sector: String?,
    val marketCapUsd: Long?,
    val source: String,
    val companyName: String?,
)
