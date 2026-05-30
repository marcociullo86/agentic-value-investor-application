package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "TickerResetRequest", description = "Richiesta di reset distruttivo dati deep-analysis di un ticker")
data class TickerResetRequest(
    @Schema(description = "Master password admin", required = true)
    val masterPassword: String = "",
)

@Schema(name = "TickerResetResult", description = "Esito del reset: righe cancellate per tabella")
data class TickerResetResult(
    val ticker: String,
    @Schema(description = "Righe cancellate per ciascuna tabella impattata")
    val deletedByTable: Map<String, Int>,
    val totalDeleted: Int,
)
