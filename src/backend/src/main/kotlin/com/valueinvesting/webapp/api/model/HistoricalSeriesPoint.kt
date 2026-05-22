package com.valueinvesting.webapp.api.model

// Punto serie storica annuale per US-015 (grafico ricavi + utile netto).
//
// `isMissing=true` quando almeno uno tra revenue e netIncome e' null per
// quell'anno: il client (Recharts) usa questo flag per rendere esplicito
// l'anno mancante (zona "no data") anziche' interpolare silenziosamente,
// coerentemente con US-015 Business Rule "Se mancano dati per anni
// intermedi, lo si rende esplicito".
//
// I valori restano nullable: MAI sostituire con 0.0 (convenzione PATTERN
// §7 — "campi mancanti = assenti, mai 0").
//
// Schema name e field naming allineati al contratto OpenAPI:
//   - schema: HistoricalSeriesPoint
//   - properties: fiscalYear / revenue / netIncome / isMissing
//
// [^src: design_&_architecture/api/openapi.yaml §HistoricalSeriesPoint]
// [^src: management/kanban/EP-005-dashboard-traffic-light-moat/US-015-grafici-storici/US-015.md §Business Rules]
data class HistoricalSeriesPoint(
    val fiscalYear: Int,
    val revenue: Double? = null,
    val netIncome: Double? = null,
    val isMissing: Boolean,
)
