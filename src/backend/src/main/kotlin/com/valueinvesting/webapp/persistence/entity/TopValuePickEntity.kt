package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// JPA entity per `top_value_picks` (V022, TSK-135).
// PK composta (run_date, ticker) → @IdClass per upsert idempotente sui rerun
// stessa data: il job esegue DELETE WHERE run_date = X + INSERT ALL nel
// transaction boundary del save (vedi TopValuePicksJob, TSK-131).
//
// JSONB `rule_signal_summary` segue il pattern V003/V015 — storage String,
// (de)serializzazione Jackson lato service. @JdbcTypeCode(SqlTypes.JSON)
// indispensabile altrimenti Hibernate binda come VARCHAR e Postgres rigetta:
//   ERROR: column "rule_signal_summary" is of type jsonb but expression is of type character varying
//
// CHECK verdetto_classe vincolato a (APPROVATO, APPROVATO_PANIC_BUY, WATCHLIST,
// SCARTATO, INDETERMINATO) lato DB — i bocciati Munger NON sono persistiti
// dal job (filtro top-N applicato in TopValuePicksJob).
//
// [^src: src/backend/src/main/resources/db/migration/V022__top_value_picks.sql]
// [^src: management/kanban/EP-012-batch-top-value-picks/US-049-persistenza-top-picks/TSK-136.md]
@Entity
@Table(name = "top_value_picks")
@IdClass(TopValuePickId::class)
data class TopValuePickEntity(
    @Id
    @Column(name = "run_date", nullable = false)
    var runDate: LocalDate = LocalDate.now(),

    @Id
    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String = "",

    @Column(name = "verdetto_classe", length = 40, nullable = false)
    var verdettoClasse: String = "",

    @Column(name = "margin_of_safety", precision = 10, scale = 4)
    var marginOfSafety: BigDecimal? = null,

    @Column(name = "posizionamento", length = 40)
    var posizionamento: String? = null,

    @Column(name = "sector", length = 80)
    var sector: String? = null,

    @Column(name = "market_cap_usd")
    var marketCapUsd: Long? = null,

    @Column(name = "rank_position", nullable = false)
    var rankPosition: Int = 0,

    @Column(name = "source", length = 20, nullable = false)
    var source: String = "",

    @Column(name = "company_name", length = 255)
    var companyName: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_signal_summary", columnDefinition = "jsonb")
    var ruleSignalSummary: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

// IdClass composta — runDate + ticker. I nomi dei field devono matchare
// 1:1 i field annotati @Id in TopValuePickEntity (Hibernate contract).
data class TopValuePickId(
    var runDate: LocalDate = LocalDate.now(),
    var ticker: String = "",
) : Serializable
