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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

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
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun analyze(ticker: String, userId: UUID? = null): RuleEngineResultResponse {
        val t = ticker.uppercase()
        // Profile FIRST: fmpCacheService.getOrFetchProfile lazily upserts the
        // stocks(ticker) row, which is a FK target for fmp_financial_snapshot
        // (V003) and rule_engine_result (V004). Fetching the dataset first
        // would attempt to insert a snapshot before the stock exists and trip
        // the FK constraint -> DataIntegrityViolationException -> 500.
        val profile = fetchProfileWithFallback(t)
        val dataset = fetchDatasetSync(t)

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

    // Synchronous fetch on the caller thread so the outer @Transactional from
    // analyze() applies to all snapshot INSERTs. Running this on fmpExecutor
    // (the previous CompletableFuture.supplyAsync wrap) opened a separate
    // transaction that could not see the still-uncommitted stocks(ticker)
    // row from getOrFetchProfile, tripping the FK constraint on snapshots.
    // FinancialDataService already issues the 4 endpoint calls sequentially,
    // so removing the async wrap does not lose any real parallelism.
    private fun fetchDatasetSync(ticker: String): FinancialDataset =
        financialDataService.getFinancialDataset(ticker)

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
