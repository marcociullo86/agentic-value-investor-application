package com.valueinvesting.webapp.ruleengine.feasibility

import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.ruleengine.calculators.FcfFallbackEstimator
import com.valueinvesting.webapp.ruleengine.calculators.GreenwaldMaintenanceCapexEstimator
import com.valueinvesting.webapp.service.FinancialDataService
import org.springframework.stereotype.Service

@Service
class DcfFeasibilityCheck(
    private val financialDataService: FinancialDataService,
    private val fmpCacheService: FmpCacheService,
    private val fmpAdapter: FmpAdapter,
    private val greenwaldEstimator: GreenwaldMaintenanceCapexEstimator,
    private val fcfFallbackEstimator: FcfFallbackEstimator,
) {

    fun canApply(ticker: String, method: DcfMethod): FeasibilityResult {
        val t = ticker.uppercase()
        // Profile upserts stocks(ticker) before snapshot INSERTs (same ordering as AnalyzeTickerService).
        fmpCacheService.getOrFetchProfile(t) { fmpAdapter.getProfile(t) }
        val dataset = financialDataService.getFinancialDataset(t)
        return when (method) {
            DcfMethod.GREENWALD -> greenwaldEstimator.isFeasible(dataset)
            DcfMethod.FCF_FALLBACK -> fcfFallbackEstimator.isFeasible(dataset)
            DcfMethod.NOT_APPLICABLE -> FeasibilityResult(true, null, null, null)
        }
    }
}
