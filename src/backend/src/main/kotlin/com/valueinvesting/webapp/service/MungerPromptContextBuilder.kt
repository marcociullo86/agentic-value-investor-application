package com.valueinvesting.webapp.service

import kotlin.math.abs

/**
 * Builds the structured pre-RAG context block for the Munger inversion LLM
 * prompt.  Stateless, no I/O, no Spring wiring.
 *
 * The ROE context block follows the spec in ADR-020 §"Input al report Munger
 * LLM": both 5-year (growth/turnaround) and 10-year (Graham defensive
 * stability) averages are surfaced, with a divergence note when the absolute
 * difference exceeds [DIVERGENCE_THRESHOLD_PP] percentage points.
 *
 * [^src: design_&_architecture/decisions/ADR-020-roe-lookback-policy-deep-analysis.md §Input al report Munger LLM]
 */
object MungerPromptContextBuilder {

    private const val DIVERGENCE_THRESHOLD_PP = 5.0

    /**
     * Produces the ROE portion of the structured context injected before RAG
     * chunks in the Munger inversion prompt.
     *
     * When a value is `null` (insufficient data), the placeholder reads
     * "N/A (dati insufficienti)" per TSK-162 spec.
     *
     * The divergence note is appended only when **both** values are present
     * **and** `|5y − 10y| > 5 pp`.
     */
    fun buildRoeContext(fiveYearAvg: Double?, tenYearAvg: Double?): String {
        val roe5yStr = formatRoe(fiveYearAvg)
        val roe10yStr = formatRoe(tenYearAvg)

        return buildString {
            append("ROE 5y: ").append(roe5yStr).appendLine(" (growth/turnaround signal).")
            append("ROE 10y: ").append(roe10yStr).appendLine(" (Graham defensive stability signal).")

            if (fiveYearAvg != null && tenYearAvg != null) {
                val divergencePp = abs(fiveYearAvg - tenYearAvg) * 100.0
                if (divergencePp > DIVERGENCE_THRESHOLD_PP) {
                    append("Discutere divergenza significativa (|5y − 10y| > 5pp) ")
                    append("come indicatore di cambio strutturale del business.")
                }
            }
        }.trimEnd()
    }

    private fun formatRoe(value: Double?): String =
        value?.let { "%.1f%%".format(it * 100.0) }
            ?: "N/A (dati insufficienti)"
}
