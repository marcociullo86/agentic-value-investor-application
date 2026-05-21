package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component
import kotlin.math.sqrt

// Graham Number calculator (US-011, TSK-016).
//
// Formula (Benjamin Graham, "The Intelligent Investor"):
//   GrahamNumber = sqrt(22.5 * EPS * BVPS)
//
// Contract:
// - If EPS or BVPS is null OR <= 0, the result is `applicable = false` with `value = null`.
//   We never substitute missing financial figures with 0.0 (US-004 null-safety contract).
// - Otherwise, the result is `applicable = true` with the computed scalar value.
//
// Design notes:
// - This is a `@Component` standalone calculator, intentionally NOT a `ValuationRule`.
//   The Rule Engine signal-based contract (RuleEngineService.evaluateAll -> List<RuleSignal>)
//   is preserved as-is (Opzione B in TSK-016 brief). AnalyzeTickerService (TSK-019)
//   will aggregate the rule signals together with this calculator's output.
// - EPS source: `IncomeStatementDto.eps` of the latest fiscal year (first element, since
//   the FMP adapter returns rows newest-first — same convention used by the 7 rules).
// - BVPS source: `KeyMetricsDto.bookValuePerShare` of the latest fiscal year (first element).
// - "Latest" = first row in each list, mirroring the upstream FMP ordering already
//   consumed by every other rule.
// [^src: management/kanban/EP-004-valore-intrinseco-margin-of-safety/US-011-graham-number/TSK-016.md §Scope tecnico]
// [^src: design_&_architecture/components/backend-components.md §GrahamCalc]
// [^src: wiki/concepts/graham-number.md]
@Component
class GrahamNumberCalculator {

    /**
     * Pure computation: returns the Graham Number for the given EPS and BVPS.
     *
     * Side-effect free. No I/O. Safe to call from any thread.
     */
    fun calculate(eps: Double?, bvps: Double?): GrahamResult {
        if (eps == null || bvps == null) {
            return GrahamResult(
                value = null,
                applicable = false,
                rationale = "EPS o BVPS non disponibili (null) — Graham Number Not Applicable.",
            )
        }
        if (eps <= 0.0 || bvps <= 0.0) {
            return GrahamResult(
                value = null,
                applicable = false,
                rationale = "EPS=$eps, BVPS=$bvps non utilizzabili (richiesti valori > 0) — Graham Number Not Applicable.",
            )
        }
        val computed = sqrt(GRAHAM_CONSTANT * eps * bvps)
        return GrahamResult(
            value = computed,
            applicable = true,
            rationale = "sqrt($GRAHAM_CONSTANT * EPS=$eps * BVPS=$bvps) = ${"%.4f".format(computed)}.",
        )
    }

    /**
     * Convenience: extracts EPS (income statement latest) + BVPS (key metrics latest)
     * from a [FinancialDataset] and delegates to [calculate].
     *
     * EPS comes from `IncomeStatementDto.eps` (KeyMetricsDto exposes `netIncomePerShare`
     * but not `eps` directly, and the US-011 brief refers to EPS as reported).
     * BVPS comes from `KeyMetricsDto.bookValuePerShare`.
     *
     * If either list is empty the result is Not Applicable (null EPS or null BVPS).
     */
    fun calculateFromDataset(dataset: FinancialDataset): GrahamResult {
        val eps = dataset.income.firstOrNull()?.eps
        val bvps = dataset.keyMetrics.firstOrNull()?.bookValuePerShare
        return calculate(eps, bvps)
    }

    private companion object {
        // Graham's empirical constant: 22.5 = (max P/E 15) * (max P/B 1.5).
        const val GRAHAM_CONSTANT = 22.5
    }
}
