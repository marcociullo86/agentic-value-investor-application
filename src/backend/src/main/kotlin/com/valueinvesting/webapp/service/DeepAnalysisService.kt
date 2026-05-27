package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.api.model.FilingRef
import com.valueinvesting.webapp.api.model.InversionItem
import com.valueinvesting.webapp.api.model.MungerReportBlock
import com.valueinvesting.webapp.api.model.NewsSentimentBlock
import com.valueinvesting.webapp.api.model.PositionSizeBlock
import com.valueinvesting.webapp.api.model.PriceActionBlock
import com.valueinvesting.webapp.api.model.RoeBlock
import com.valueinvesting.webapp.api.model.VerdictBlock
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.llm.LlmException
import com.valueinvesting.webapp.persistence.entity.DeepAnalysisEventLogEntity
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisEventLogRepository
import com.valueinvesting.webapp.ruleengine.RuleEngineService
import com.valueinvesting.webapp.ruleengine.calculators.DcfCalculator
import com.valueinvesting.webapp.ruleengine.calculators.RoeCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

// Orchestrates the deep-analysis pipeline for a single ticker (US-045).
//
// Pipeline steps (deterministic always, LLM opt-in):
//   1. Profile + financial dataset via FMP (for ROE + rule engine)
//   2. Filing download + RAG indexing
//   3. Munger inversion LLM analysis (opt-in via invoke_llm)
//   4. News sentiment LLM classification (opt-in via invoke_llm)
//   5. Price action analysis (deterministic)
//   6. Rule engine evaluation (13 rules)
//   7. Verdict cascade via MungerDecisionService
//   8. Position sizing via PositionSizeCalculator
//
// [^src: wiki/concepts/analysis-api-pipeline.md §Pipeline]
// [^src: wiki/runbooks/sec-10k-10q-analysis-playbook.md §Step 1-5]
@Service
class DeepAnalysisService(
    private val financialDataService: FinancialDataService,
    private val fmpCacheService: FmpCacheService,
    private val fmpAdapter: FmpAdapter,
    private val filing10KQDownloaderService: Filing10KQDownloaderService,
    private val filingRagService: FilingRagService,
    private val mungerInversionAnalyzer: MungerInversionAnalyzer,
    private val newsSentimentService: NewsSentimentService,
    private val priceActionAnalyzer: PriceActionAnalyzer,
    private val ruleEngineService: RuleEngineService,
    private val mungerDecisionService: MungerDecisionService,
    private val positionSizeCalculator: PositionSizeCalculator,
    private val dcfCalculator: DcfCalculator,
    private val deepAnalysisEventLogRepository: DeepAnalysisEventLogRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun analyze(ticker: String, invokeLlm: Boolean = false): DeepAnalysisResponse {
        val startMs = System.currentTimeMillis()
        val t = ticker.uppercase()
        var llmCalls = 0

        val profile = try {
            fmpCacheService.getOrFetchProfile(t) { fmpAdapter.getProfile(t) }
        } catch (ex: FmpTickerNotFoundException) {
            throw ex
        }

        // Step 1: Financial dataset + ROE
        val dataset = financialDataService.getFinancialDataset(t)
        val roe5y = RoeCalculator.fiveYearAverage(dataset.income, dataset.balance)
        val roe10y = RoeCalculator.tenYearAverage(dataset.income, dataset.balance)
        val roeBlock = RoeBlock(
            fiveYearAvg = roe5y.average,
            tenYearAvg = roe10y.average,
            fiveYearDataPoints = roe5y.dataPoints,
            tenYearDataPoints = roe10y.dataPoints,
        )

        // Step 2: Filing download + indexing
        val filingBlobs = filing10KQDownloaderService.fetchAndCache(t)
        if (filingBlobs.isEmpty()) {
            throw NoSecFilingsException(t)
        }

        for (blob in filingBlobs) {
            val blobId = blob.id ?: continue
            filingRagService.indexFiling(blobId)
        }

        val filingsUsed = filingBlobs.map { blob ->
            FilingRef(
                accessionNumber = blob.accessionNumber,
                formType = blob.formType,
                filingDate = blob.filingDate.toString(),
            )
        }

        // Step 3: Munger inversion (LLM — opt-in)
        var mungerReport: MungerReportBlock? = null
        var mungerRiskLevel = LivelloRischio.RISCHIO_MODERATO
        var llmStatus = "NOT_INVOKED"

        if (invokeLlm) {
            try {
                val report = mungerInversionAnalyzer.analyze(
                    ticker = t,
                    roeFiveYearAvg = roe5y.average,
                    roeTenYearAvg = roe10y.average,
                )
                mungerRiskLevel = report.livelloRischio
                llmCalls += report.llmCallsCount
                llmStatus = "INVOKED"

                mungerReport = MungerReportBlock(
                    livelloRischio = report.livelloRischio,
                    rischiPrincipali = report.rischiPrincipali.map { InversionItem(it.testo, it.chunkIndex) },
                    puntiDiForza = report.puntiDiForza.map { InversionItem(it.testo, it.chunkIndex) },
                    segnaliRecenti10Q = report.segnaliRecenti10Q.map { InversionItem(it.testo, it.chunkIndex) },
                    filingComboHash = report.filingComboHash,
                    llmCallsCount = report.llmCallsCount,
                )
            } catch (ex: EmbeddingServiceUnavailableException) {
                throw LlmUnavailableException(t, cause = ex)
            } catch (ex: LlmException) {
                throw LlmUnavailableException(t, cause = ex)
            }
        }

        // Step 4: News sentiment (LLM — opt-in)
        var newsSentiment: NewsSentimentBlock? = null
        var dominantSentiment = SentimentClass.NEUTRAL

        if (invokeLlm) {
            try {
                val sentimentResult = newsSentimentService.classify(t)
                dominantSentiment = sentimentResult.dominantClass

                newsSentiment = NewsSentimentBlock(
                    total = sentimentResult.total,
                    panicCount = sentimentResult.panicCount,
                    structuralCount = sentimentResult.structuralCount,
                    neutralCount = sentimentResult.neutralCount,
                    dominantClass = sentimentResult.dominantClass,
                )
            } catch (ex: LlmException) {
                throw LlmUnavailableException(t, cause = ex)
            }
        }

        // Step 5: Price action (deterministic)
        val priceSnapshot = priceActionAnalyzer.analyze(t)
        val priceActionBlock = PriceActionBlock(
            priceNow = priceSnapshot.priceNow,
            max52w = priceSnapshot.max52w,
            min52w = priceSnapshot.min52w,
            drawdownPct = priceSnapshot.drawdownPct,
            trend3mPct = priceSnapshot.trend3mPct,
            ma50 = priceSnapshot.ma50,
            ma200 = priceSnapshot.ma200,
            panicDiscount = priceSnapshot.panicDiscount,
            deteriorationWarning = priceSnapshot.deteriorationWarning,
            seriesDays = priceSnapshot.seriesDays,
        )

        // Step 6: Rule engine (13 rules — deterministic)
        val datasetWithPrice = dataset.copy(currentPrice = profile.value.price)
        val signals = ruleEngineService.evaluateAll(datasetWithPrice)

        // Step 7: DCF (needed for position sizing via MoS)
        val dcf = dcfCalculator.calculate(datasetWithPrice)

        // Step 8: Verdict cascade
        val decisionInput = MungerDecisionInput(
            ticker = t,
            ruleResults = signals,
            livelloRischio = mungerRiskLevel,
            newsSentimentDominante = dominantSentiment,
            panicDiscount = priceSnapshot.panicDiscount,
            deteriorationWarning = priceSnapshot.deteriorationWarning,
        )
        val verdictPayload = mungerDecisionService.compute(decisionInput)

        val verdictBlock = VerdictBlock(
            verdettoClasse = verdictPayload.verdettoClasse,
            positionSizePct = verdictPayload.positionSizePct,
            partialBasis = verdictPayload.partialBasis || !invokeLlm,
            motivazioneAggregata = verdictPayload.motivazioneAggregata,
            ruleCountGreen = verdictPayload.ruleCountByColor.green,
            ruleCountYellow = verdictPayload.ruleCountByColor.yellow,
            ruleCountRed = verdictPayload.ruleCountByColor.red,
            livelloRischio = mungerRiskLevel,
            newsSentimentDominante = dominantSentiment,
        )

        // Step 9: Position sizing
        val mosPct = computeMosPct(profile.value.price, dcf.intrinsicValue)
        val positionSizeBlock = if (mosPct != null) {
            val result = positionSizeCalculator.calculate(verdictPayload.verdettoClasse, mosPct)
            PositionSizeBlock(
                recommendedPct = result.recommendedPct,
                rangeLow = result.range.first,
                rangeHigh = result.range.second,
                basisVerdict = result.basisVerdict,
                marginOfSafetyPct = result.marginOfSafetyPct,
                disclaimer = result.disclaimer,
            )
        } else {
            null
        }

        val totalDurationMs = System.currentTimeMillis() - startMs

        log.info(
            "Deep analysis complete for {}: verdict={}, llmStatus={}, llmCalls={}, durationMs={}",
            t, verdictPayload.verdettoClasse, llmStatus, llmCalls, totalDurationMs,
        )

        try {
            deepAnalysisEventLogRepository.save(
                DeepAnalysisEventLogEntity(
                    ticker = t,
                    generatedAt = Instant.now(),
                    cacheHits = 0,
                    llmCalls = llmCalls,
                    totalDurationMs = totalDurationMs,
                ),
            )
        } catch (ex: Exception) {
            log.warn("Failed to persist deep_analysis_event_log for {}: {}", t, ex.message)
        }

        return DeepAnalysisResponse(
            ticker = t,
            generatedAt = Instant.now(),
            roe = roeBlock,
            priceAction = priceActionBlock,
            ruleEngineResults = signals,
            verdict = verdictBlock,
            positionSize = positionSizeBlock,
            filingsUsed = filingsUsed,
            mungerReport = mungerReport,
            newsSentiment = newsSentiment,
            llmStatus = llmStatus,
            llmCalls = llmCalls,
            totalDurationMs = totalDurationMs,
        )
    }

    private fun computeMosPct(currentPrice: Double?, intrinsicValue: Double?): Double? {
        if (currentPrice == null || intrinsicValue == null || intrinsicValue <= 0.0) return null
        return ((intrinsicValue - currentPrice) / intrinsicValue) * 100.0
    }
}
