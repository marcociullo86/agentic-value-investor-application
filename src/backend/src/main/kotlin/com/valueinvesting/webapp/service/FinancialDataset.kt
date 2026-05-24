package com.valueinvesting.webapp.service

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// Aggregated financial dataset returned by FinancialDataService.
// Shape allineata a openapi.yaml §FinancialDataset (ticker/years/dataSnapshotAt/isStale).
// L'array `years` (YearlyFinancials) viene popolato dal Rule Engine in TSK successivi;
// in questo TSK il dataset è raw-oriented (le 4 liste DTO) per uso diagnostico.
// [^src: design_&_architecture/api/openapi.yaml §FinancialDataset]
// [^src: design_&_architecture/components/backend-components.md §FinancialDataService]
@Schema(name = "FinancialDataset")
data class FinancialDataset(
    val ticker: String,
    val income: List<com.valueinvesting.webapp.fmp.dto.IncomeStatementDto>,
    val balance: List<com.valueinvesting.webapp.fmp.dto.BalanceSheetDto>,
    val cashFlow: List<com.valueinvesting.webapp.fmp.dto.CashFlowDto>,
    val keyMetrics: List<com.valueinvesting.webapp.fmp.dto.KeyMetricsDto>,
    val dataSnapshotAt: Instant,
    val isStale: Boolean = false,
    val staleReason: String? = null,
    // EP-010 — current quote price from ProfileDto.price, needed by Pe3yAvgRule
    // (TSK-079) and PbLatestRule (TSK-081). Optional/nullable to preserve
    // backward-compat with the 9 rules that operate purely on historical FMP
    // financials. Populated by AnalyzeTickerService.analyze() after the profile
    // fetch, BEFORE calling RuleEngineService.evaluateAll().
    val currentPrice: Double? = null,
    // EP-010 — dividend history series from FmpAdapter.getDividendHistory(),
    // needed by DividendContinuityRule (TSK-085, US-037 Graham criterio 4).
    // Default `emptyList()` preserves backward-compat with the 10 pre-existing
    // rules that do not consult dividends. Populated by
    // AnalyzeTickerService.analyze() after the profile/dataset fetch, with
    // failure tolerance (FMP unavailable -> empty list -> rule INDETERMINATE).
    // Cached via FmpCacheService.getOrFetch(endpoint="dividends", TTL 24h)
    // (V011 adds 'dividends' to the fmp_financial_snapshot.endpoint CHECK).
    val dividends: List<com.valueinvesting.webapp.fmp.dto.DividendRecord> = emptyList(),
)
