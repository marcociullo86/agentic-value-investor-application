package com.valueinvesting.webapp.job

import com.valueinvesting.webapp.fmp.FmpBatchContext
import com.valueinvesting.webapp.persistence.entity.TopPicksRunLogEntity
import com.valueinvesting.webapp.persistence.entity.TopValuePickEntity
import com.valueinvesting.webapp.persistence.entity.TopValuePickId
import com.valueinvesting.webapp.persistence.repository.TopPicksRunLogRepository
import com.valueinvesting.webapp.persistence.repository.TopValuePickRepository
import com.valueinvesting.webapp.service.DeepAnalysisService
import com.valueinvesting.webapp.service.VerdictClass
import com.valueinvesting.webapp.universe.UniverseCandidate
import com.valueinvesting.webapp.universe.UniverseScreenerService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// TopValuePicksJob — Spring @Scheduled cron 02:00 UTC (TSK-131, EP-012/US-048).
//
// PIPELINE
// --------
// 1. UniverseScreenerService.screen() → List<UniverseCandidate> (max 500).
// 2. Per ogni candidato → DeepAnalysisService.analyze(ticker, invokeLlm=false).
//    NOTA: `MungerDecisionService.compute()` accetta `MungerDecisionInput`
//    pre-computato (rule signals + sentiment + risk). L'orchestrator canonico
//    che orchestrate il dataset e' DeepAnalysisService (US-045) — usiamo
//    quello come entry point. invokeLlm=false per evitare cost LLM in batch
//    (la Munger inversion e' opt-in via UI).
// 3. Filtro: tieni solo verdetti APPROVATO / APPROVATO_PANIC_BUY / WATCHLIST.
// 4. Sort by marginOfSafetyPct DESC, take topN (default 30).
// 5. Upsert idempotente: DELETE WHERE run_date = X + INSERT entities.
// 6. Run log: 1 row TopPicksRunLogEntity con stato STARTED → COMPLETED/FAILED.
//
// BEAN LOADING — il bean è sempre caricato per permettere il trigger manuale
// via POST /api/top-picks/run anche quando il cron schedulato è disabilitato.
// Il flag `top-picks.enabled` controlla SOLO l'esecuzione automatica via
// @Scheduled (vedi `scheduledTick()`).
//
// IDEMPOTENZA — il job e' safe per rerun stesso run_date: DELETE-then-INSERT
// nel boundary del save (PK = run_date + ticker). Non e' transazionale stretto
// (single @Transactional non applicato qui) perche' su crash mid-write il
// successivo run del giorno seguente riprende dall'inizio.
//
// ERROR HANDLING per-ticker — try/catch dentro il loop: un fail su ticker X
// NON interrompe gli altri. Conteggio `failed` finisce nel run log.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-131.md]
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/service/DeepAnalysisService.kt §analyze]
@Component
class TopValuePicksJob(
    private val universeScreenerService: UniverseScreenerService,
    private val deepAnalysisService: DeepAnalysisService,
    private val topValuePickRepository: TopValuePickRepository,
    private val runLogRepository: TopPicksRunLogRepository,
    private val properties: TopPicksProperties,
    private val cancellationSignal: TopPicksCancellationSignal,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Set dei verdetti che persistiamo (top picks "buoni"). I bocciati Munger
    // (BOCCIATO_NUMERICO, BOCCIATO_QUALITATIVO, BOCCIATO_VALUE_TRAP) sono
    // scartati a monte. NB: il CHECK lato DB include anche SCARTATO e
    // INDETERMINATO che NON sono enum di VerdictClass — riservati a future
    // estensioni del rule engine.
    private val keepVerdicts = setOf(
        VerdictClass.APPROVATO,
        VerdictClass.APPROVATO_PANIC_BUY,
        VerdictClass.WATCHLIST,
    )

    // Wrapper @Scheduled: chiamato dal cron, esegue il job SOLO se
    // `top-picks.enabled=true`. Il trigger manuale (POST /api/top-picks/run)
    // invoca `run()` direttamente e bypassa questo check, by design.
    @Scheduled(cron = "\${top-picks.cron:0 0 2 * * *}", zone = "\${top-picks.zone:UTC}")
    fun scheduledTick() {
        if (!properties.enabled) {
            log.debug("TopValuePicksJob scheduled tick skipped — top-picks.enabled=false")
            return
        }
        // Start clean: a stale cancel flag from a previous (manually aborted)
        // run must never abort this scheduled run. The manual path clears the
        // flag in TopPicksManualTrigger before dispatching, by the same logic.
        cancellationSignal.clear()
        run()
    }

    fun run() {
        val runDate = LocalDate.now(ZoneId.of(properties.zone))
        val startedAt = Instant.now()
        var runLog = TopPicksRunLogEntity(
            runDate = runDate,
            startedAt = startedAt,
            status = "STARTED",
        )
        runLog = runLogRepository.save(runLog)

        // Tutte le chiamate FMP del batch (screener + 13-F + NewsScout dentro
        // screen(), e il loop DeepAnalysisService) girano sotto il flag batch:
        // su esaurimento del rate limiter FMP condiviso il ResilientFmpAdapter
        // attende il refresh (~1 min) e ritenta, invece di perdere il ticker
        // (ADR-016 §Appendice A). Il batch e' single-thread sincrono, quindi il
        // ThreadLocal copre tutte le chiamate discendenti; spento in `finally`.
        FmpBatchContext.setBatch(true)
        try {
            val candidates = universeScreenerService.screen()
            log.info("TopValuePicksJob start runDate={} universeSize={}", runDate, candidates.size)

            var processed = 0
            var failed = 0
            data class Scored(
                val candidate: UniverseCandidate,
                val verdetto: VerdictClass,
                val mos: BigDecimal,
            )
            val results = mutableListOf<Scored>()

            for (cand in candidates) {
                // Cooperative cancellation: a manual "Blocca" request (POST
                // /api/top-picks/run/cancel) sets the shared flag. We poll it
                // at the top of each iteration so an in-flight run stops at the
                // next ticker boundary — without corrupting the day's picks
                // (see the ABORTED branch below: no DELETE-then-INSERT upsert).
                if (cancellationSignal.isCancelRequested()) {
                    val finishedAt = Instant.now()
                    runLog.finishedAt = finishedAt
                    runLog.durationSeconds = ChronoUnit.SECONDS.between(startedAt, finishedAt)
                    runLog.tickersProcessed = processed
                    runLog.tickersFailed = failed
                    runLog.status = "ABORTED"
                    runLogRepository.save(runLog)
                    log.warn(
                        "TopValuePicksJob ABORTED runDate={} processed={} failed={} — cancel requested; existing picks left untouched",
                        runDate, processed, failed,
                    )
                    return
                }
                try {
                    val response = deepAnalysisService.analyze(cand.ticker, invokeLlm = false)
                    val verdetto = response.verdict.verdettoClasse
                    if (verdetto in keepVerdicts) {
                        // marginOfSafetyPct e' Double — convert to BigDecimal scale 4
                        // per matching la colonna NUMERIC(10,4). null = skip pick
                        // (verdetto non puo' essere ranked senza MoS).
                        val mosPct = response.positionSize?.marginOfSafetyPct
                        if (mosPct != null) {
                            results.add(
                                Scored(
                                    candidate = cand,
                                    verdetto = verdetto,
                                    mos = BigDecimal.valueOf(mosPct)
                                        .setScale(4, java.math.RoundingMode.HALF_UP),
                                ),
                            )
                        }
                    }
                    processed++
                } catch (ex: Exception) {
                    failed++
                    log.warn("TopValuePicksJob ticker={} skip cause={}", cand.ticker, ex.message)
                }
            }

            val top = results
                .sortedByDescending { it.mos }
                .take(properties.topN)

            // Upsert idempotente: cancella le righe esistenti per run_date e
            // reinserisci. PK (run_date, ticker) garantisce no-dup.
            val existing = topValuePickRepository.findByRunDateOrderByRankPositionAsc(runDate)
            if (existing.isNotEmpty()) {
                topValuePickRepository.deleteAllById(
                    existing.map { TopValuePickId(it.runDate, it.ticker) },
                )
                log.info("TopValuePicksJob rerun: deleted {} existing rows for runDate={}", existing.size, runDate)
            }

            val entities = top.mapIndexed { idx, scored ->
                TopValuePickEntity(
                    runDate = runDate,
                    ticker = scored.candidate.ticker,
                    verdettoClasse = scored.verdetto.name,
                    marginOfSafety = scored.mos,
                    posizionamento = null,
                    sector = scored.candidate.sector,
                    marketCapUsd = scored.candidate.marketCapUsd,
                    rankPosition = idx + 1,
                    source = scored.candidate.source.name,
                    companyName = scored.candidate.companyName,
                    ruleSignalSummary = null,
                )
            }
            topValuePickRepository.saveAll(entities)

            val finishedAt = Instant.now()
            val durationSec = ChronoUnit.SECONDS.between(startedAt, finishedAt)
            if (durationSec > properties.warningDurationMinutes * 60) {
                log.warn(
                    "TopValuePicksJob duration > {}min: {}s — investigate (FMP slow?)",
                    properties.warningDurationMinutes,
                    durationSec,
                )
            }

            runLog.finishedAt = finishedAt
            runLog.durationSeconds = durationSec
            runLog.tickersProcessed = processed
            runLog.tickersFailed = failed
            runLog.top30Count = entities.size
            runLog.top30Tickers = entities.joinToString(",") { it.ticker }
            runLog.status = "COMPLETED"
            runLogRepository.save(runLog)

            log.info(
                "TopValuePicksJob done runDate={} top={} processed={} failed={} durationSec={}",
                runDate, entities.size, processed, failed, durationSec,
            )
        } catch (ex: Exception) {
            log.error("TopValuePicksJob FAILED runDate={} cause={}", runDate, ex.message, ex)
            runLog.finishedAt = Instant.now()
            runLog.durationSeconds = ChronoUnit.SECONDS.between(startedAt, Instant.now())
            runLog.status = "FAILED"
            runLog.errorMessage = ex.message?.take(2000)
            runLogRepository.save(runLog)
        } finally {
            FmpBatchContext.setBatch(false)
        }
    }
}
