package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.persistence.entity.RuleEngineResultEntity
import com.valueinvesting.webapp.persistence.repository.DcfMethodOverrideRepository
import com.valueinvesting.webapp.persistence.repository.RuleEngineResultRepository
import com.valueinvesting.webapp.ruleengine.RuleEngineService
import com.valueinvesting.webapp.ruleengine.calculators.DcfCalculator
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.ruleengine.calculators.GrahamNumberCalculator
import com.valueinvesting.webapp.ruleengine.calculators.MarginOfSafetyEvaluator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Service
class AnalyzeTickerService(
    private val financialDataService: FinancialDataService,
    private val fmpCacheService: FmpCacheService,
    private val fmpAdapter: FmpAdapter,
    private val ruleEngineService: RuleEngineService,
    private val grahamNumberCalculator: GrahamNumberCalculator,
    private val dcfCalculator: DcfCalculator,
    private val marginOfSafetyEvaluator: MarginOfSafetyEvaluator,
    private val ruleEngineResultRepository: RuleEngineResultRepository,
    private val dcfMethodOverrideRepository: DcfMethodOverrideRepository,
    private val objectMapper: ObjectMapper,
    @Qualifier("fmpExecutor") private val fmpExecutor: TaskExecutor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun analyze(ticker: String, userId: UUID? = null): RuleEngineResultResponse {
        val t = ticker.uppercase()
        val dataset = fetchDatasetParallel(t)
        val profile = fetchProfileWithFallback(t)

        val signals = ruleEngineService.evaluateAll(dataset)
        val graham = grahamNumberCalculator.calculateFromDataset(dataset)
        val forcedMethod = userId?.let { uid ->
            dcfMethodOverrideRepository.findByUserIdAndTicker(uid, t)?.forcedMethod?.let { name ->
                runCatching { DcfMethod.valueOf(name) }.getOrNull()
            }
        }
        val dcf = dcfCalculator.calculate(dataset, forcedMethod)
        val mos = marginOfSafetyEvaluator.evaluate(profile.value.price, dcf)

        val evaluatedAt = Instant.now()
        val response = RuleEngineResultResponse(
            ticker = t,
            evaluatedAt = evaluatedAt,
            signals = signals,
            grahamNumber = graham.value,
            dcfIntrinsicValue = dcf.intrinsicValue,
            dcfMethod = if (dcf.method == DcfMethod.NOT_APPLICABLE && dcf.intrinsicValue == null) {
                DcfMethod.NOT_APPLICABLE
            } else {
                dcf.method
            },
            mosSignal = mos.signal,
            currentPriceAtEval = profile.value.price,
            dataSnapshotAt = dataset.dataSnapshotAt,
            isStale = dataset.isStale,
        )

        persistResult(response, dataset.dataSnapshotAt)
        return response
    }

    private fun fetchDatasetParallel(ticker: String): FinancialDataset =
        CompletableFuture.supplyAsync({ financialDataService.getFinancialDataset(ticker) }, fmpExecutor).join()

    private fun fetchProfileWithFallback(ticker: String) =
        try {
            fmpCacheService.getOrFetchProfile(ticker) { fmpAdapter.getProfile(ticker) }
        } catch (ex: FmpUnavailableException) {
            log.warn("Profile fetch failed for {} — analysis continues without price", ticker)
            throw ex
        }

    private fun persistResult(response: RuleEngineResultResponse, snapshotAt: Instant) {
        val entity = RuleEngineResultEntity(
            ticker = response.ticker,
            evaluatedAt = response.evaluatedAt,
            signalsJson = objectMapper.writeValueAsString(response.signals),
            grahamNumber = response.grahamNumber?.let { BigDecimal.valueOf(it) },
            dcfIntrinsicValue = response.dcfIntrinsicValue?.let { BigDecimal.valueOf(it) },
            dcfMethod = response.dcfMethod?.toApiValue(),
            mosSignal = response.mosSignal.name,
            currentPriceAtEval = response.currentPriceAtEval?.let { BigDecimal.valueOf(it) },
            sourceSnapshotFetchedAt = snapshotAt,
        )
        ruleEngineResultRepository.save(entity)
    }
}
