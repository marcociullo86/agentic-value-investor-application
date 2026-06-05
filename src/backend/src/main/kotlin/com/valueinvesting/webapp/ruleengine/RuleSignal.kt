@file:Suppress("DEPRECATION")
// file-level suppress: i 13 data class sotto-tipi devono dichiarare i 3 campi
// legacy @Deprecated come parametri di costruttore, e i generated `toString` /
// `equals` / `hashCode` / `copy` / `componentN` di una data class accedono ai
// suoi val — emettendo warning di deprecation a cascata. Sopprimiamo qui per
// non sporcare il compile output durante R+1/R+2. Rimozione naturale a R+3
// quando i campi legacy spariranno (ADR-028 §8).

package com.valueinvesting.webapp.ruleengine

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// RuleSignal — sealed interface polimorfica Jackson-aware.
//
// Refactor EP-021 / US-093 / TSK-311 (ADR-028): da `data class` flat a sealed interface
// con 13 sotto-tipi tipati per `ruleId`. La serializzazione JSON usa `ruleId` come
// discriminator (`@JsonTypeInfo(include = EXISTING_PROPERTY)`); il deserializer
// polimorfico Jackson legge i record JSONB pre-EP-021 con campi tipati = null
// (natural overwrite, ADR-028 §4).
//
// Transition window (R+1 / R+2) — ADR-028 §8:
//   I campi legacy `observedValue` / `rationale` / `threshold` restano nel payload,
//   marcati @Deprecated a livello Kotlin (warning sui consumer interni) e
//   `deprecated: true` nello schema OpenAPI. Rimozione a R+3 via nuovo ADR.
//
// Compatibility shim TSK-311 → TSK-312 (RIMOSSA):
//   TSK-311 aveva introdotto una factory top-level `fun RuleSignal(ruleId, signal,
//   observedValue, threshold, rationale)` come ponte verso le 13 call-site
//   pre-refactor. TSK-312 ha sostituito tutte le call-site con il costruttore
//   tipato diretto (es. `RuleSignal.Size(revenueLatest = ..., thresholdUsd = ...,
//   ...)`) e ha rimosso la shim. Da questo momento `RuleSignal` è invocabile
//   SOLO tramite uno dei 13 sotto-tipi tipati.
//
// [^src: design_&_architecture/decisions/ADR-028-rulesignal-typed-oneof-discriminator.md §1, §3, §4, §8]
// [^src: management/kanban/EP-021-rulesignal-payload-refactor/US-093-rulesignal-schema-typed-be/TSK-311.md §Scope]
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-007-regola-redditivita/TSK-012.md §Scope tecnico]
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "ruleId",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RuleSignal.Size::class, name = "SIZE_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.EarningsStability10y::class, name = "EARNINGS_STABILITY_10Y"),
    JsonSubTypes.Type(value = RuleSignal.EpsGrowth10y::class, name = "EPS_GROWTH_10Y"),
    JsonSubTypes.Type(value = RuleSignal.Pe3yAvg::class, name = "PE_3Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.PbLatest::class, name = "PB_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.DividendContinuity20y::class, name = "DIVIDEND_CONTINUITY_20Y"),
    JsonSubTypes.Type(value = RuleSignal.Roe10yAvg::class, name = "ROE_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.Roic10yAvg::class, name = "ROIC_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.GrossMargin10yAvg::class, name = "GROSS_MARGIN_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.NetMargin10yAvg::class, name = "NET_MARGIN_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.CurrentRatioLatest::class, name = "CURRENT_RATIO_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.DebtToIncomeLatest::class, name = "DEBT_TO_INCOME_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.CapexIntensity10yAvg::class, name = "CAPEX_INTENSITY_10Y_AVG"),
    // EP-023 — Scenario B ADR-029 §5: TSK-316 aggiunge NcavLatest, TSK-317 aggiunge NetNetRatio.
    JsonSubTypes.Type(value = RuleSignal.NcavLatest::class, name = "NCAV_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.NetNetRatio::class, name = "NET_NET_RATIO"),
)
sealed interface RuleSignal {
    val ruleId: String
    val signal: Signal

    // Legacy fields — rimozione R+3 (ADR-028 §8).
    // Marcati @Deprecated per emettere warning sui consumer interni; restano nel
    // payload (Jackson li serializza normalmente perché override di property).
    @Deprecated("Use typed metadata fields. Removed in R+3.")
    val observedValue: Double?

    @Deprecated("Use typed metadata fields. Removed in R+3.")
    val rationale: String

    @Deprecated("Use typed metadata fields. Removed in R+3.")
    val threshold: String

    // -------------------------------------------------------------------------
    // 13 typed sub-types (ADR-028 §3 — mapping vincolante con OpenAPI schema)
    // -------------------------------------------------------------------------

    /** Graham §Adequate Size of the Enterprise — soglia $100M revenue latest. */
    data class Size(
        override val signal: Signal,
        val revenueLatest: Double? = null,
        val thresholdUsd: Long = 100_000_000L,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "SIZE_LATEST"
    }

    /** Graham §Earnings Stability — anni con EPS > 0 sull'orizzonte 10y. */
    data class EarningsStability10y(
        override val signal: Signal,
        val yearsPositive: Int = 0,
        val yearsAvailable: Int = 0,
        val lossYears: List<Int> = emptyList(),
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "EARNINGS_STABILITY_10Y"
    }

    /** Graham §EPS Growth — CAGR EPS su 10 anni. */
    data class EpsGrowth10y(
        override val signal: Signal,
        val cagrPercent: Double? = null,
        val thresholdPercent: Double = 33.0,
        val epsStart: Double? = null,
        val epsEnd: Double? = null,
        val yearStart: Int? = null,
        val yearEnd: Int? = null,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "EPS_GROWTH_10Y"
    }

    /**
     * Graham §P/E Moderate — media 3y EPS.
     *
     * Deviazione ADR-028 §3 (vincolante "small adjustment" prevista dall'ADR):
     * il mapping originale prevedeva `threshold: Double` ma (a) la rule ha DUE
     * soglie semantiche (`<=15 GREEN`, `(15,20] YELLOW`, `>20 RED`) e (b) il
     * nome `threshold` collide con il campo legacy `threshold: String` ereditato
     * dall'interfaccia. Adottato pattern `thresholdGreen` + `thresholdYellow`,
     * coerente con `Roe10yAvg`/`Roic10yAvg`/`CurrentRatioLatest`/`DebtToIncomeLatest`.
     */
    data class Pe3yAvg(
        override val signal: Signal,
        val pe3yAvg: Double? = null,
        val thresholdGreen: Double = 15.0,
        val thresholdYellow: Double = 20.0,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "PE_3Y_AVG"
    }

    /**
     * Graham §P/B Moderate.
     *
     * Deviazione ADR-028 §3 analoga a [Pe3yAvg]: `thresholdGreen` (≤1.5) +
     * `thresholdYellow` (≤3.0), invece di un singolo `threshold: Double`.
     */
    data class PbLatest(
        override val signal: Signal,
        val pbLatest: Double? = null,
        val thresholdGreen: Double = 1.5,
        val thresholdYellow: Double = 3.0,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "PB_LATEST"
    }

    /** Graham §Dividend Continuity — anni consecutivi. */
    data class DividendContinuity20y(
        override val signal: Signal,
        val consecutiveYears: Int? = null,
        val thresholdYears: Int = 20,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "DIVIDEND_CONTINUITY_20Y"
    }

    /** Buffett §Quality — ROE 10y avg. */
    data class Roe10yAvg(
        override val signal: Signal,
        val averagePercent: Double? = null,
        val yearsAvailable: Int = 0,
        val thresholdGreenPercent: Double = 15.0,
        val thresholdYellowPercent: Double = 10.0,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "ROE_10Y_AVG"
    }

    /** Buffett §Quality — ROIC 10y avg. */
    data class Roic10yAvg(
        override val signal: Signal,
        val averagePercent: Double? = null,
        val yearsAvailable: Int = 0,
        val thresholdGreenPercent: Double = 12.0,
        val thresholdYellowPercent: Double = 8.0,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "ROIC_10Y_AVG"
    }

    /** Buffett §Pricing Power — gross margin 10y avg. */
    data class GrossMargin10yAvg(
        override val signal: Signal,
        val averagePercent: Double? = null,
        val thresholdGreenPercent: Double = 40.0,
        val thresholdYellowPercent: Double = 30.0,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "GROSS_MARGIN_10Y_AVG"
    }

    /** Buffett §Pricing Power — net margin 10y avg. */
    data class NetMargin10yAvg(
        override val signal: Signal,
        val averagePercent: Double? = null,
        val thresholdGreenPercent: Double = 10.0,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "NET_MARGIN_10Y_AVG"
    }

    /** Buffett §Financial Strength — current ratio latest. */
    data class CurrentRatioLatest(
        override val signal: Signal,
        val ratioLatest: Double? = null,
        val thresholdGreen: Double = 2.0,
        val thresholdYellow: Double = 1.5,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "CURRENT_RATIO_LATEST"
    }

    /** Buffett §Financial Strength — debt/income latest (INDETERMINATE se net income ≤ 0). */
    data class DebtToIncomeLatest(
        override val signal: Signal,
        val ratioLatest: Double? = null,
        val thresholdGreen: Double = 4.0,
        val thresholdYellow: Double = 5.0,
        val netIncomePositive: Boolean = false,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "DEBT_TO_INCOME_LATEST"
    }

    /** Buffett §Capital-Light — capex intensity (capex/operatingCashFlow) 10y avg. */
    data class CapexIntensity10yAvg(
        override val signal: Signal,
        val averagePercent: Double? = null,
        val thresholdGreenPercent: Double = 25.0,
        val thresholdYellowPercent: Double = 30.0,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "CAPEX_INTENSITY_10Y_AVG"
    }

    /**
     * Graham §Cap.15 Enterprising Investor — Net Current Asset Value (informativo).
     *
     * Formula (ADR-029 §1):
     *   NCAV total      = totalCurrentAssets - totalLiabilities  (passività TOTALI)
     *   NCAV per share  = NCAV total / sharesOutstanding
     *
     * Codifica (ADR-029 §2):
     *   - dati mancanti                 → INDETERMINATE
     *   - ncavTotal > 0                 → GREEN  (calcolo riuscito; la decisione di acquisto spetta a NET_NET_RATIO)
     *   - ncavTotal <= 0                → RED    (passivo totale > attivo corrente)
     *
     * Costruito direttamente (NON via factory shim) — TSK-316 EP-023 introduce il
     * sotto-tipo come typed-native fin dalla nascita; la factory shim (TSK-311 →
     * TSK-312) viene rimossa da TSK-312 quindi non aggiungiamo qui un ramo legacy.
     */
    data class NcavLatest(
        override val signal: Signal,
        val ncavTotal: Double? = null,
        val ncavPerShare: Double? = null,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "NCAV_LATEST"
    }

    /**
     * Graham §Cap.15 Enterprising Investor — Net-Net Ratio (decisionale).
     *
     * Formula (ADR-029 §1 + §3):
     *   ratio = priceLatest / ncavPerShare
     *
     * Soglia (ADR-029 §3): `THRESHOLD_RATIO = 2/3 = 0.6667 con precisione double`.
     *
     * Codifica (ADR-029 §3):
     *   - ncavPerShare non calcolabile OR priceLatest == null   → INDETERMINATE
     *   - ncavPerShare <= 0                                     → NOT_CALCULABLE  (coerente con NCAV_LATEST RED)
     *   - ratio < 0.6667 (price < 2/3 × NCAV_per_share)         → GREEN           (opportunità net-net Graham)
     *   - ratio >= 0.6667                                       → RED             (titolo non net-net)
     *
     * Coerenza con NCAV_LATEST:
     *   - NCAV_LATEST INDETERMINATE  ⇒ NET_NET_RATIO INDETERMINATE
     *   - NCAV_LATEST RED (ncavTotal ≤ 0) ⇒ NET_NET_RATIO NOT_CALCULABLE
     *   - NCAV_LATEST GREEN (ncavTotal > 0) ⇒ NET_NET_RATIO GREEN o RED secondo il ratio
     *
     * Costruito direttamente (NON via factory shim) — TSK-317 EP-023 introduce il
     * sotto-tipo come typed-native fin dalla nascita (stesso pattern di NcavLatest
     * in TSK-316). I 3 campi legacy `observedValue`/`rationale`/`threshold` restano
     * popolati per la transition window R+1/R+2 (ADR-028 §8); rimozione naturale a R+3.
     */
    data class NetNetRatio(
        override val signal: Signal,
        val priceLatest: Double? = null,
        val ncavPerShare: Double? = null,
        val ratio: Double? = null,
        val thresholdRatio: Double = THRESHOLD_RATIO,
        @Deprecated("R+3") override val observedValue: Double? = null,
        @Deprecated("R+3") override val rationale: String = "",
        @Deprecated("R+3") override val threshold: String = "",
    ) : RuleSignal {
        override val ruleId: String = "NET_NET_RATIO"

        companion object {
            /** Soglia Graham Cap.15 verbatim: prezzo < 2/3 × NCAV per share. */
            const val THRESHOLD_RATIO: Double = 2.0 / 3.0
        }
    }
}

// -----------------------------------------------------------------------------
// Factory shim TSK-311 RIMOSSA da TSK-312
// -----------------------------------------------------------------------------
//
// La factory `fun RuleSignal(ruleId, signal, observedValue, threshold, rationale)`
// introdotta da TSK-311 come ponte di transizione è stata rimossa: tutte le 13
// strategie ValuationRule + i test fixture costruiscono ora direttamente uno
// dei sotto-tipi tipati `RuleSignal.*`. Le call-site future devono usare il
// costruttore tipato (PATTERN §11 verbatim su ADR-028 §1, §3).
//
// [^src: management/kanban/EP-021-rulesignal-payload-refactor/US-093-rulesignal-schema-typed-be/TSK-312.md §Obiettivo]
