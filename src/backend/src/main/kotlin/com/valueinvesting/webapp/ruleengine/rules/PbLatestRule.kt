package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: Graham "Moderate Price-to-Book" — single-snapshot P/B check
// (Cap. 14 Criterio 6 / EP-010 — US-036).
//
// Formula:
//   pbLatest = currentPrice / bookValuePerShare   (latest fiscal year)
//
// Thresholds (US-036 Business Rules, TSK-081 §Cosa fare):
//   pbLatest <= 1.5            -> GREEN
//   pbLatest in (1.5, 3.0]     -> YELLOW
//   pbLatest > 3.0             -> RED
//
// Out-of-scope per US-036 (delegato a GrahamNumberCalculator):
//   il vincolo combinato P/E * P/B <= 22.5 NON e' calcolato qui — questa rule
//   misura solo il P/B puntuale. La logica multiplo di Graham vive nel
//   GrahamNumberCalculator (riuso esistente, US-008 / TSK-016).
//
// Design note — LATEST-YEAR (snapshot), NOT multi-year average:
//   P/B e' un rapporto point-in-time tra prezzo di mercato corrente e
//   book value per share dell'ultimo bilancio annuale. Stessa semantica di
//   CurrentRatioRule / SizeRule: selezione del record piu' recente per
//   `date` (ISO-8601 lex == cronologico) con fallback `calendarYear`.
//
// Design note — bookValuePerShare nullability su schema /stable:
//   Audit 2026-05-22 (memory/feedback_fmp_doc_refresh.md): nello schema
//   FMP /stable il campo `bookValuePerShare` su /key-metrics POTREBBE essere
//   null (spostato a /ratios). KeyMetricsDto.bookValuePerShare resta
//   `Double?`. In quel caso -> INDETERMINATE. NON tentiamo la derivazione
//   `totalStockholdersEquity / sharesOutstanding`: la logica complessa di
//   fallback equity-derived vive gia' in GrahamNumberCalculator (US-008) e
//   replicarla qui sarebbe duplicazione e fuori scope TSK-081. L'estensione
//   pull-from-/ratios e' rimandata a sprint futuro.
//
// Design note — bookValuePerShare <= 0:
//   Patrimonio netto per azione negativo o nullo = company distressed /
//   negative equity (e.g. buyback aggressivo che ha consumato il book oppure
//   perdite accumulate). Il P/B su baseline non positiva non e' interpretabile
//   (un prezzo positivo / book negativo darebbe un ratio negativo privo di
//   significato value-investing). -> INDETERMINATE (NOT RED), allineato a
//   CurrentRatioRule.liabilities<=0 e Pe3yAvgRule.avgEps3y<=0.
//
// Design note — currentPrice via dataset.currentPrice (NOT FmpAdapter):
//   Come Pe3yAvgRule (TSK-079), questa rule e' pure per contratto
//   ValuationRule: legge dataset.currentPrice (Double? popolato da
//   AnalyzeTickerService da ProfileDto.price PRIMA di evaluateAll). No DI
//   verso FmpAdapter — il TSK-081 indicava `FmpAdapter.getQuote(ticker)` ma
//   il dataset enrichment (EP-010) ha gia' centralizzato il fetch.
//
// Edge cases (signal policy):
//   - dataset.keyMetrics empty                       -> NOT_CALCULABLE
//   - currentPrice == null                           -> INDETERMINATE
//   - bookValuePerShare == null                      -> INDETERMINATE
//   - bookValuePerShare <= 0.0                       -> INDETERMINATE
//   PATTERN §7 r.13: mai coerce a 0 — null resta null.
//
// Note: priceTimestamp NOT emitted in the RuleSignal — ProfileDto su
// /stable non espone un campo timestamp dedicato per la quote; il dato
// freshness e' tracciato a livello FinancialDataset.dataSnapshotAt.
// (Stesso skip di TSK-079 / Pe3yAvgRule.)
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-036-regola-pb-moderato-graham/TSK-081.md §Cosa fare]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 7 — Rapporto P/Book Moderato]
// [^src: wiki/concepts/value-investing-rule-engine.md §Calcolo Valore Intrinseco (RF4)]
// [^src: wiki/runbooks/defensive-investor-checklist.md §Step 7 — P/B moderato]
@Component
class PbLatestRule : ValuationRule {

    override val ruleId: String = "PB_LATEST"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.keyMetrics.isEmpty()) {
            return RuleSignal.PbLatest(
                signal = Signal.NOT_CALCULABLE,
                pbLatest = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_UPPER_BOUND,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Key Metrics non disponibili: P/B indeterminato.",
            )
        }

        val currentPrice = dataset.currentPrice
        if (currentPrice == null) {
            return RuleSignal.PbLatest(
                signal = Signal.INDETERMINATE,
                pbLatest = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_UPPER_BOUND,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Prezzo corrente non disponibile: P/B indeterminato.",
            )
        }

        // Latest-year picker (stesso pattern di SizeRule / CurrentRatioRule).
        val latestKeyMetrics = dataset.keyMetrics.maxByOrNull { it.date ?: it.calendarYear ?: "" }
            ?: return RuleSignal.PbLatest(
                signal = Signal.INDETERMINATE,
                pbLatest = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_UPPER_BOUND,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Key Metrics non ordinabili: P/B indeterminato.",
            )

        val yearLabel = latestKeyMetrics.date
            ?: latestKeyMetrics.calendarYear
            ?: "ultimo esercizio"

        val bookValuePerShare = latestKeyMetrics.bookValuePerShare
        if (bookValuePerShare == null) {
            // Schema /stable: campo potenzialmente null su /key-metrics
            // (memory/feedback_fmp_doc_refresh.md). No fallback derivativo qui.
            return RuleSignal.PbLatest(
                signal = Signal.INDETERMINATE,
                pbLatest = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_UPPER_BOUND,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Book Value per Share mancante per $yearLabel: P/B indeterminato.",
            )
        }
        if (bookValuePerShare <= 0.0) {
            return RuleSignal.PbLatest(
                signal = Signal.INDETERMINATE,
                pbLatest = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_UPPER_BOUND,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Book Value per Share non positivo ($bookValuePerShare) per $yearLabel: P/B indeterminato (patrimonio netto negativo o nullo).",
            )
        }

        val pbLatest = currentPrice / bookValuePerShare
        val signal = when {
            pbLatest <= GREEN_THRESHOLD -> Signal.GREEN
            pbLatest <= YELLOW_UPPER_BOUND -> Signal.YELLOW
            else -> Signal.RED
        }
        val rationale = "Prezzo $%.2f / Book Value per Share $%.2f ($yearLabel) = P/B %.2f"
            .format(currentPrice, bookValuePerShare, pbLatest)
        return RuleSignal.PbLatest(
            signal = signal,
            pbLatest = pbLatest,
            thresholdGreen = GREEN_THRESHOLD,
            thresholdYellow = YELLOW_UPPER_BOUND,
            observedValue = pbLatest,
            threshold = THRESHOLD_LABEL,
            rationale = rationale,
        )
    }

    private companion object {
        const val GREEN_THRESHOLD = 1.5
        const val YELLOW_UPPER_BOUND = 3.0
        const val THRESHOLD_LABEL = "≤ 1.5 (GREEN), 1.5-3.0 (YELLOW), > 3.0 (RED)"
    }
}
