package com.valueinvesting.webapp.backtest

import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// PointInTimeFinancialFilter — anti look-ahead per i fondamentali (EP-024 / US-105 / TSK-346).
//
// Filtra i record FMP financial-statements per `acceptedDate`/`filingDate` ≤ t:
// a una data `t` un bilancio depositato a `t + 1 mese` NON deve essere visibile
// al rule engine. Questo previene il look-ahead bias piu' grossolano.
//
// Limite residuo dichiarato (US-105 §"Onesta dei limiti"): i fondamentali FMP
// sono serviti **ristrutturati** (le revisioni successive sostituiscono i dati
// as-first-reported). `filingDate` elimina il look-ahead piu' grossolano ma
// NON le revisioni successive — questo limite e' esposto in `caveats.lookAheadResidual`.
//
// Pure-function: niente I/O, niente persistenza, niente LLM. Sicuro per riuso
// dal backtest US-105 in modalita' "as-of date".
//
// KeyMetrics: il DTO FMP `/stable/key-metrics` espone `date` ma NON
// `filingDate`/`acceptedDate`. Convenzione conservativa: il `date` (fiscal year
// end) e' usato come proxy della disponibilita' — un report con date = `2024-12-31`
// NON e' usato a `t = 2024-11-30` ma E' usato a `t = 2025-03-31` (assumiamo che
// i metrics derivati siano disponibili insieme al filing del bilancio annuale).
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-346.md §"Filtro filingDate"]
// [^src: wiki/concepts/value-investing-rule-engine.md]
@Component
class PointInTimeFinancialFilter {

    /**
     * Filtra un [FinancialDataset] sul cut-off `asOf`, escludendo i record con
     * `acceptedDate` (o `filingDate` di fallback) > `asOf`. Il [FinancialDataset]
     * ritornato e' compatibile col `RuleEngineService.evaluateAll`.
     *
     * Il `currentPrice` e il `dataSnapshotAt` sono propagati dai parametri:
     * il caller passa il prezzo EOD a `asOf` come `currentPrice` (riproduce la
     * semantica live di `AnalyzeTickerService.analyze`).
     */
    fun filter(
        dataset: FinancialDataset,
        asOf: LocalDate,
        currentPrice: Double?,
    ): FinancialDataset {
        val income = dataset.income.filter { acceptedOrFilingOnOrBefore(it.acceptedDate, it.fillingDate, asOf) }
        val balance = dataset.balance.filter { acceptedOrFilingOnOrBefore(it.acceptedDate, it.fillingDate, asOf) }
        val cashFlow = dataset.cashFlow.filter { acceptedOrFilingOnOrBefore(it.acceptedDate, it.fillingDate, asOf) }
        val keyMetrics = dataset.keyMetrics.filter { keyMetricDateOnOrBefore(it, asOf) }
        val dividends = dataset.dividends.filter { dividendDateOnOrBefore(it, asOf) }

        return dataset.copy(
            income = income,
            balance = balance,
            cashFlow = cashFlow,
            keyMetrics = keyMetrics,
            dividends = dividends,
            currentPrice = currentPrice,
            // Il dataSnapshotAt rappresenta il "data al" del filtro: usiamo
            // `asOf` end-of-day in UTC come timestamp deterministico (riusabile
            // dai test idempotenza — stesso input → stesso output).
            dataSnapshotAt = asOf.atTime(23, 59, 59).toInstant(ZoneOffset.UTC),
            isStale = false,
            staleReason = null,
        )
    }

    /**
     * Income / Balance / CashFlow DTO: preferisce `acceptedDate` (timestamp pieno
     * `yyyy-MM-dd HH:mm:ss`); fallback a `filingDate` (`yyyy-MM-dd`). Se entrambi
     * mancano (rari record FMP legacy), il record e' considerato pubblico — per
     * conservativita' lo escludiamo, perche' non possiamo dimostrare che fosse
     * gia' noto a `t` (anti look-ahead esplicito).
     */
    internal fun acceptedOrFilingOnOrBefore(
        acceptedDate: String?,
        filingDate: String?,
        asOf: LocalDate,
    ): Boolean {
        val parsedAccepted = parseLooseDate(acceptedDate)
        if (parsedAccepted != null) return !parsedAccepted.isAfter(asOf)
        val parsedFiling = parseLooseDate(filingDate)
        if (parsedFiling != null) return !parsedFiling.isAfter(asOf)
        // Conservativo: nessuna data → escludi.
        return false
    }

    private fun keyMetricDateOnOrBefore(metric: KeyMetricsDto, asOf: LocalDate): Boolean {
        val parsed = parseLooseDate(metric.date) ?: return false
        return !parsed.isAfter(asOf)
    }

    private fun dividendDateOnOrBefore(div: DividendRecord, asOf: LocalDate): Boolean {
        // Per i dividendi usiamo `paymentDate` se presente (cassa effettivamente
        // pagata, semantica anti look-ahead), altrimenti `date` (ex-dividend).
        val parsed = parseLooseDate(div.paymentDate) ?: parseLooseDate(div.date) ?: return false
        return !parsed.isAfter(asOf)
    }

    /**
     * Parser tollerante: accetta sia `yyyy-MM-dd` sia `yyyy-MM-dd HH:mm:ss`
     * (FMP varia tra i due formati su `acceptedDate` vs `filingDate`). Null se
     * la stringa e' null/blank o non parsable.
     */
    internal fun parseLooseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        // Provo formato ISO `yyyy-MM-dd` (filingDate).
        return runCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE) }
            .recoverCatching {
                LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate()
            }
            .recoverCatching {
                // Fallback ultra-tollerante: prende i primi 10 caratteri se in
                // formato `yyyy-MM-dd...` qualsiasi.
                if (raw.length >= 10) LocalDate.parse(raw.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE)
                else error("date too short")
            }
            .getOrNull()
    }

    companion object {
        /** Istante "end of day" UTC a `asOf` — usato per `dataSnapshotAt` deterministico. */
        fun endOfDayInstant(asOf: LocalDate): Instant = asOf.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
    }
}
