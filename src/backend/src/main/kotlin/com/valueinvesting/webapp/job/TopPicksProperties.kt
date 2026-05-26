package com.valueinvesting.webapp.job

import org.springframework.boot.context.properties.ConfigurationProperties

// Configurazione TopValuePicksJob (TSK-131, EP-012/US-048).
//
// Tutti i field hanno default sicuri per disabilitare in dev/test (`enabled=false`
// di default). Per attivare il batch in prod, override via `top-picks.enabled=true`
// (env `TOP_PICKS_ENABLED=true`) e configurare zone/cron come da ADR-009.
//
// `cron` — sintassi Spring 6-field (seconds + minutes + hours + DoM + month + DoW).
// Default `0 0 2 * * *` = 02:00 UTC ogni giorno.
//
// `warningDurationMinutes` — soglia log WARN se durata totale > 180m.
// `abortFmpUnavailableMinutes` — soglia abort se FMP unavailable > 30m (NB:
// implementazione abort in TSK successiva, qui solo retention via property).
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-131.md]
// [^src: design_&_architecture/decisions/ADR-009-deployment-target.md §Scheduler]
@ConfigurationProperties(prefix = "top-picks")
data class TopPicksProperties(
    val enabled: Boolean = false,
    val cron: String = "0 0 2 * * *",
    val zone: String = "UTC",
    val topN: Int = 30,
    val retentionDays: Long = 90,
    val warningDurationMinutes: Long = 180,
    val abortFmpUnavailableMinutes: Long = 30,
)
