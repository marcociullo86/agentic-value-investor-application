package com.valueinvesting.webapp.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.DcfMethodSource
import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.persistence.entity.RuleEngineResultEntity
import com.valueinvesting.webapp.persistence.repository.DcfMethodOverrideRepository
import com.valueinvesting.webapp.persistence.repository.RuleEngineResultRepository
import com.valueinvesting.webapp.ruleengine.RuleEngineService
import com.valueinvesting.webapp.ruleengine.calculators.DcfCalculator
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.ruleengine.calculators.GrahamNumberCalculator
import com.valueinvesting.webapp.ruleengine.calculators.MarginOfSafetyEvaluator
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

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
    fun analyze(ticker: String): RuleEngineResultResponse {
        val t = ticker.uppercase()
        // Profile FIRST: fmpCacheService.getOrFetchProfile lazily upserts the
        // stocks(ticker) row, which is a FK target for fmp_financial_snapshot
        // (V003) and rule_engine_result (V004). Fetching the dataset first
        // would attempt to insert a snapshot before the stock exists and trip
        // the FK constraint -> DataIntegrityViolationException -> 500.
        val profile = fetchProfileWithFallback(t)
        val datasetRaw = fetchDatasetSync(t)
        // EP-010 (TSK-085): fetch dividend history with failure tolerance.
        // Same pattern as fetchProfileWithFallback — if FMP is down or the
        // ticker has no dividend coverage, we degrade to empty list and let
        // DividendContinuityRule resolve to INDETERMINATE (US-037 AC). The
        // call is cache-aside via FmpCacheService.getOrFetch using the same
        // "dividends" label whitelisted by V011 in fmp_financial_snapshot.
        val dividends = fetchDividendsWithFallback(t)
        // EP-010: enrich the dataset with currentPrice from the profile so
        // Pe3yAvgRule and PbLatestRule (TSK-079/081) can read it via the
        // stateless ValuationRule contract (no FmpAdapter injection in rules).
        val dataset = datasetRaw.copy(
            currentPrice = profile.value.price,
            dividends = dividends,
        )

        val signals = ruleEngineService.evaluateAll(dataset)
        val graham = grahamNumberCalculator.calculateFromDataset(dataset)

        val auth = SecurityContextHolder.getContext().authentication
        val userId = (auth?.principal as? UserPrincipal)?.userId
        val override = userId?.let { uid ->
            dcfMethodOverrideRepository.findByUserIdAndTicker(uid, t)
        }
        val dcfMethodSource = if (override != null) {
            DcfMethodSource.USER_OVERRIDE
        } else {
            DcfMethodSource.DEFAULT_POLICY
        }
        val forcedMethod = override?.forcedMethod?.let { name ->
            runCatching { DcfMethod.valueOf(name) }.getOrNull()
        }
        val dcf = dcfCalculator.calculate(dataset, forcedMethod)
        val responseDcfMethod = when (dcfMethodSource) {
            DcfMethodSource.USER_OVERRIDE -> forcedMethod ?: dcf.method
            DcfMethodSource.DEFAULT_POLICY -> if (dcf.method == DcfMethod.NOT_APPLICABLE && dcf.intrinsicValue == null) {
                DcfMethod.NOT_APPLICABLE
            } else {
                dcf.method
            }
        }
        val mos = marginOfSafetyEvaluator.evaluate(profile.value.price, dcf)

        val evaluatedAt = Instant.now()
        val response = RuleEngineResultResponse(
            ticker = t,
            evaluatedAt = evaluatedAt,
            signals = signals,
            grahamNumber = graham.value,
            dcfIntrinsicValue = dcf.intrinsicValue,
            dcfMethod = responseDcfMethod,
            dcfMethodSource = dcfMethodSource,
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

    // TSK-085: cache-aside fetch of dividend history with full failure
    // tolerance. Unlike fetchProfileWithFallback, a missing dividend payload
    // is NOT fatal to the analysis — the rule degrades to INDETERMINATE and
    // the other 12 rules continue normally (US-037 AC: "Ticker senza
    // dividendi -> INDETERMINATE, non RED"). Cached via the same
    // FmpCacheService.getOrFetch used by the 4 heavy statements, labelled
    // "dividends" (whitelisted by V011 CHECK constraint).
    private fun fetchDividendsWithFallback(ticker: String): List<DividendRecord> =
        runCatching {
            fmpCacheService
                .getOrFetch(
                    ticker = ticker,
                    endpoint = ENDPOINT_DIVIDENDS,
                    typeRef = object : TypeReference<List<DividendRecord>>() {},
                    fetchFn = { fmpAdapter.getDividendHistory(ticker) },
                )
                .value
        }.getOrElse { ex ->
            log.warn(
                "Dividend history fetch failed for {} — DividendContinuityRule will degrade to INDETERMINATE: {}",
                ticker, ex.message,
            )
            emptyList()
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

    private companion object {
        // Endpoint label per FmpCacheService — must match V011 CHECK whitelist.
        const val ENDPOINT_DIVIDENDS = "dividends"
    }
}
