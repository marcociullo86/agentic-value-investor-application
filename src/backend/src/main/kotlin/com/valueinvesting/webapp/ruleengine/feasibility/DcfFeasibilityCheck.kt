package com.valueinvesting.webapp.ruleengine.feasibility

import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.ruleengine.calculators.FcfFallbackEstimator
import com.valueinvesting.webapp.ruleengine.calculators.GreenwaldMaintenanceCapexEstimator
import com.valueinvesting.webapp.service.FinancialDataService
import org.springframework.stereotype.Service

@Service
class DcfFeasibilityCheck(
    private val financialDataService: FinancialDataService,
    private val greenwaldEstimator: GreenwaldMaintenanceCapexEstimator,
    private val fcfFallbackEstimator: FcfFallbackEstimator,
) {

    fun canApply(ticker: String, method: DcfMethod): FeasibilityResult {
        val dataset = financialDataService.getFinancialDataset(ticker.uppercase())
        return when (method) {
            DcfMethod.GREENWALD -> greenwaldEstimator.isFeasible(dataset)
            DcfMethod.FCF_FALLBACK -> fcfFallbackEstimator.isFeasible(dataset)
            DcfMethod.NOT_APPLICABLE -> FeasibilityResult(true, null, null, null)
        }
    }
}
