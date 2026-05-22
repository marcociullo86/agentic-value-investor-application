package com.valueinvesting.webapp.api.model

import java.time.Instant

// Serie storica decennale ricavi + utile netto per US-015.
//
// `points` ordinata cronologicamente crescente (fiscalYear asc) per servire
// direttamente l'asse X del grafico Recharts senza ulteriore sort lato client.
// Lunghezza max 10 (US-015 AC "fino a 10 anni").
//
// `dataSnapshotAt` propaga `CachedPayload.fetchedAt` del FmpCacheService —
// stessa semantica usata da FinancialsController per il header
// `X-Data-Snapshot-At` (coerenza US-005 "timestamp dati al").
//
// Lista vuota e' un risultato legittimo (ticker nuovo / FMP senza storia):
// il caller restituisce 200 con `points: []`, NON 404. Solo
// FmpTickerNotFoundException -> 404, FmpUnavailableException -> 503.
//
// [^src: design_&_architecture/api/openapi.yaml §HistoricalSeries]
// [^src: design_&_architecture/components/backend-components.md §HistoricalSeriesService]
data class HistoricalSeries(
    val ticker: String,
    val points: List<HistoricalSeriesPoint>,
    val dataSnapshotAt: Instant,
)
