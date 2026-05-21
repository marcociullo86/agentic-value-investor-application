package com.valueinvesting.webapp.service

import java.time.Instant

// Aggregated financial dataset returned by FinancialDataService.
// Shape allineata a openapi.yaml §FinancialDataset (ticker/years/dataSnapshotAt/isStale).
// L'array `years` (YearlyFinancials) viene popolato dal Rule Engine in TSK successivi;
// in questo TSK il dataset è raw-oriented (le 4 liste DTO) per uso diagnostico.
// [^src: design_&_architecture/api/openapi.yaml §FinancialDataset]
// [^src: design_&_architecture/components/backend-components.md §FinancialDataService]
data class FinancialDataset(
    val ticker: String,
    val income: List<com.valueinvesting.webapp.fmp.dto.IncomeStatementDto>,
    val balance: List<com.valueinvesting.webapp.fmp.dto.BalanceSheetDto>,
    val cashFlow: List<com.valueinvesting.webapp.fmp.dto.CashFlowDto>,
    val keyMetrics: List<com.valueinvesting.webapp.fmp.dto.KeyMetricsDto>,
    val dataSnapshotAt: Instant,
    val isStale: Boolean = false,
    val staleReason: String? = null,
)
