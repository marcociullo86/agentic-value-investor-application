package com.valueinvesting.webapp.service

import com.fasterxml.jackson.core.type.TypeReference
import com.valueinvesting.webapp.api.model.HistoricalSeries
import com.valueinvesting.webapp.api.model.HistoricalSeriesPoint
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// HistoricalSeriesService — assembla la serie decennale ricavi + utile netto
// per US-015 (grafico storico).
//
// Riuso cache: passa per FmpCacheService.getOrFetch con endpoint
// "income-statement" — stesso endpoint usato da FinancialDataService, quindi
// la cache (TTL 24h, TSK-010) e' condivisa: una chiamata recente da
// /api/analyze/{ticker} satura la cache e questa chiamata non genera traffico
// FMP. Coerente con ADR-004 §Cache layer 24h.
//
// Null-safety: revenue / netIncome restano nullable lungo tutta la pipeline.
// `isMissing` viene calcolato sulla coppia (revenue, netIncome) e non
// sostituiamo MAI null con 0.0 (PATTERN §7 — "campi mancanti != 0").
//
// Anno (`fiscalYear`):
//   - estratto da `IncomeStatementDto.calendarYear?.toIntOrNull()` —
//     stesso pattern canonico usato da FinancialYearAligner, CapexIntensityRule
//     e DebtToIncomeRule;
//   - fallback su `date` (formato ISO "yyyy-MM-dd"): prendiamo i primi 4 char
//     se parsabile a Int — coerente con CapexIntensityRule.yearKey;
//   - se entrambi assenti / non parsabili: la riga viene scartata (no anno -> no
//     punto utile sul grafico, evitiamo bucket "0" inquinante).
//
// Ordine: FMP restituisce decrescente (latest-first); il grafico richiede
// crescente sull'asse X -> sortBy(fiscalYear) ascending alla fine. Tronchiamo
// a 10 anni piu' recenti DOPO l'ordinamento decrescente (per garantire che,
// se FMP restituisse > 10 per errore, prendiamo i piu' recenti, US-015 AC
// "Anno corrente sempre visibile in chiusura della serie").
//
// dataset vuoto: ritorna HistoricalSeries(ticker, emptyList(), fetchedAt)
// — 200 con `points: []`. Non solleva eccezioni.
//
// Errori FMP: propagati al GlobalExceptionHandler:
//   - FmpTickerNotFoundException -> 404 RFC 9457
//   - FmpUnavailableException    -> 503 RFC 9457
// (entrambi sollevati da ResilientFmpAdapter / FmpAdapterRestClient — qui
//  non li intercettiamo perche' la semantica e' la stessa di
//  FinancialDataService.fetchWithFallback per income-statement.)
//
// [^src: design_&_architecture/components/backend-components.md §HistoricalSeriesService]
// [^src: design_&_architecture/api/openapi.yaml §/api/historical/{ticker}]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Cache layer 24h]
// [^src: management/kanban/EP-005-dashboard-traffic-light-moat/US-015-grafici-storici/US-015.md §Business Rules]
// [^src: management/kanban/EP-005-dashboard-traffic-light-moat/US-015-grafici-storici/TSK-023.md §Scope tecnico]
@Service
class HistoricalSeriesService(
    private val fmpAdapter: FmpAdapter,
    private val fmpCacheService: FmpCacheService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getSeries(ticker: String): HistoricalSeries {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val t = ticker.uppercase()

        val cached = fmpCacheService.getOrFetch(
            ticker = t,
            endpoint = INCOME_ENDPOINT,
            typeRef = INCOME_TYPE_REF,
            fetchFn = { fmpAdapter.getIncomeStatement(t, FMP_LIMIT) },
        )

        val rows: List<IncomeStatementDto> = cached.value

        // 1. Estrai (year, dto) coppie scartando righe senza anno parsabile.
        // 2. Ordina decrescente per prendere i 10 piu' recenti se FMP overshootta.
        // 3. Tronca a MAX_YEARS.
        // 4. Riordina crescente (asse X grafico).
        val points: List<HistoricalSeriesPoint> = rows
            .mapNotNull { dto ->
                val year = extractYear(dto) ?: return@mapNotNull null
                HistoricalSeriesPoint(
                    fiscalYear = year,
                    revenue = dto.revenue,
                    netIncome = dto.netIncome,
                    isMissing = dto.revenue == null || dto.netIncome == null,
                )
            }
            .sortedByDescending { it.fiscalYear }
            .take(MAX_YEARS)
            .sortedBy { it.fiscalYear }

        log.debug(
            "historical series ticker={} rows={} points={} snapshotAt={}",
            t, rows.size, points.size, cached.fetchedAt,
        )

        return HistoricalSeries(
            ticker = t,
            points = points,
            dataSnapshotAt = cached.fetchedAt,
        )
    }

    // Year extraction: calendarYear (canonical, "YYYY") -> date ("YYYY-MM-DD" prefix).
    // Allineato a FinancialYearAligner / CapexIntensityRule.yearKey.
    private fun extractYear(dto: IncomeStatementDto): Int? {
        dto.calendarYear?.toIntOrNull()?.let { return it }
        return dto.date?.take(4)?.toIntOrNull()
    }

    companion object {
        // FMP endpoint key (deve coincidere con FinancialDataService.ENDPOINT_INCOME
        // per condividere la cache). Validato dal CHECK constraint su
        // fmp_financial_snapshot.endpoint.
        internal const val INCOME_ENDPOINT = "income-statement"

        // US-015 AC: orizzonte fino a 10 anni di osservazioni.
        internal const val MAX_YEARS = 10

        // Limite passato a FMP: chiediamo esattamente MAX_YEARS (FMP default e'
        // 5; senza limit perderemmo gli anni 5-9). Mai > 10: spreco di quota.
        internal const val FMP_LIMIT = MAX_YEARS

        // Jackson TypeReference singleton (anonymous class della erasure
        // generica List<IncomeStatementDto> richiesta da
        // FmpCacheService.getOrFetch).
        internal val INCOME_TYPE_REF: TypeReference<List<IncomeStatementDto>> =
            object : TypeReference<List<IncomeStatementDto>>() {}
    }
}
