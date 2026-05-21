package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto

// Shared helper: compute the 10-year average of a nullable metric exposed on
// KeyMetricsDto, applying the US-007 null-safety contract.
//
// Rules:
// - Years where the metric is `null` are EXCLUDED from the mean (NEVER 0.0).
// - "Effective sample" = count of non-null years considered.
// - Caller decides what to do with thresholds; this helper only summarises inputs.
//
// We deliberately do not cap to 10: the upstream FmpAdapter already requests
// `limit=10`, but passing a longer list would still produce a meaningful avg.
// [^src: design_&_architecture/components/backend-components.md §Validazione null safety]
internal data class MetricSample(
    /** Average of non-null values, or null if no usable value exists. */
    val average: Double?,
    /** Count of non-null years (the "effective sample"). */
    val effectiveYears: Int,
    /** Total number of rows considered (including the null ones). */
    val totalYears: Int,
)

internal fun averageOf(
    keyMetrics: List<KeyMetricsDto>,
    extractor: (KeyMetricsDto) -> Double?,
): MetricSample {
    val total = keyMetrics.size
    val values = keyMetrics.mapNotNull(extractor)
    if (values.isEmpty()) {
        return MetricSample(average = null, effectiveYears = 0, totalYears = total)
    }
    return MetricSample(
        average = values.sum() / values.size,
        effectiveYears = values.size,
        totalYears = total,
    )
}

// Generic overload: shares the exact same null-safety contract but is decoupled
// from KeyMetricsDto. Used by TSK-013 rules (GrossMarginRule, NetMarginRule)
// that derive margins from IncomeStatementDto rows.
//
// Additive only — does not alter the KeyMetricsDto-bound overload above so the
// pre-existing RoeRule/RoicRule call sites remain untouched.
internal fun <T> averageOfMetric(
    rows: List<T>,
    extractor: (T) -> Double?,
): MetricSample {
    val total = rows.size
    val values = rows.mapNotNull(extractor)
    if (values.isEmpty()) {
        return MetricSample(average = null, effectiveYears = 0, totalYears = total)
    }
    return MetricSample(
        average = values.sum() / values.size,
        effectiveYears = values.size,
        totalYears = total,
    )
}
