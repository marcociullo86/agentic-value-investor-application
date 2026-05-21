package com.valueinvesting.webapp.domain

// 11 settori GICS canonici esposti dallo screener parametrico US-002.
// Ordine ed elenco allineati a [[vi-07-risoluzione-q002-q003]] §Classificazione Settoriale (GICS)
// e all'enum `sector` in design_&_architecture/api/openapi.yaml §components/schemas
// (parametri /api/screener).
//
// `fmpLabel` corrisponde all'etichetta usata da FMP nel campo `sector` di /profile e
// /stock-screener (vedi [[fmp-search]] e ADR-004 §Adapter pattern): mantenuta in
// PascalCase con spazi per allinearsi al payload remoto.
//
// `isHardToPredict()` restituisce true per i 3 settori esclusi dal preset
// "Exclude Hard-to-Predict Sectors" del filtro Circle of Competence (TSK-005 spec):
// FINANCIALS, REAL_ESTATE, ENERGY — proxy MVP per la nota qualitativa del wiki
// (biotech/startup tech/mining speculativa). Vedi follow-up Sprint 3 in
// [[fmp-search]] per affinamenti.
//
// [^src: wiki/sources/vi-07-risoluzione-q002-q003.md §Classificazione Settoriale (GICS)]
// [^src: design_&_architecture/api/openapi.yaml §/api/screener parameters.sector]
// [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §Scope tecnico]
enum class GicsSector(val fmpLabel: String) {
    ENERGY("Energy"),
    MATERIALS("Basic Materials"),
    INDUSTRIALS("Industrials"),
    CONSUMER_DISCRETIONARY("Consumer Cyclical"),
    CONSUMER_STAPLES("Consumer Defensive"),
    HEALTH_CARE("Healthcare"),
    FINANCIALS("Financial Services"),
    INFORMATION_TECHNOLOGY("Technology"),
    COMMUNICATION_SERVICES("Communication Services"),
    UTILITIES("Utilities"),
    REAL_ESTATE("Real Estate"),
    ;

    fun isHardToPredict(): Boolean = this in HARD_TO_PREDICT

    companion object {
        // 3 settori esclusi dal preset "Exclude Hard-to-Predict Sectors" (US-002).
        // Razionale TSK-005: bilanci a struttura non operativa (banche/assicurazioni,
        // REITs) o esposizione macro-commodity (energy) — non confrontabili con i
        // criteri standard del Rule Engine senza override dedicati.
        val HARD_TO_PREDICT: Set<GicsSector> = setOf(FINANCIALS, REAL_ESTATE, ENERGY)

        // Best-effort reverse lookup dalla label FMP. Restituisce null su label
        // sconosciuta — il caller filtra/scarta. FMP usa varianti (`Technology` vs
        // `Information Technology` etc.); manteniamo il mapping accentrato qui.
        fun fromFmpLabel(label: String?): GicsSector? {
            if (label.isNullOrBlank()) return null
            val normalized = label.trim()
            return entries.firstOrNull { it.fmpLabel.equals(normalized, ignoreCase = true) }
        }
    }
}
