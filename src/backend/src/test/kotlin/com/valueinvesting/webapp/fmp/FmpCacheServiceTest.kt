package com.valueinvesting.webapp.fmp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.persistence.entity.FmpFinancialSnapshot
import com.valueinvesting.webapp.persistence.entity.FmpProfileSnapshot
import com.valueinvesting.webapp.persistence.entity.Stock
import com.valueinvesting.webapp.persistence.repository.FmpFinancialSnapshotRepository
import com.valueinvesting.webapp.persistence.repository.FmpProfileSnapshotRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

// Unit tests for FmpCacheService — the cache-aside layer with TTL 24h (financial)
// and TTL 1h (profile).  Uses a virtualised `Clock.fixed` to make TTL boundary
// behaviour deterministic (TSK-010 DoD).
//
// What is exercised here:
//   1. cache hit < 24h → no fetchFn call (DoD: "Seconda analisi entro 24h non
//      genera nuova chiamata a FmpAdapter").
//   2. cache miss / age > 24h → fetchFn invoked + new snapshot persisted (DoD).
//   3. profile TTL is shorter (1h) and behaves analogously.
//   4. lazy population: getOrFetchProfile upserts the `stocks` row on first fetch.
//   5. getStale returns the expired entry as fallback (US-006 prep).
//
// Note: the cache uses JpaRepository methods; we mock the repositories rather
// than spinning up Testcontainers — the JSONB round-trip is covered by an
// integration test in TSK-011 (resilience layer).
class FmpCacheServiceTest {

    private val financialRepo: FmpFinancialSnapshotRepository = mockk(relaxed = true)
    private val profileRepo: FmpProfileSnapshotRepository = mockk(relaxed = true)
    private val stockRepo: StockRepository = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val now = Instant.parse("2026-05-21T10:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)

    private fun newService(clock: Clock = fixedClock) = FmpCacheService(
        financialSnapshotRepository = financialRepo,
        profileSnapshotRepository = profileRepo,
        stockRepository = stockRepo,
        objectMapper = objectMapper,
        clock = clock,
    )

    private val incomeTypeRef = object : TypeReference<List<IncomeStatementDto>>() {}

    // ---------- 24h financial cache ----------

    @Test
    fun `getOrFetch returns cached payload when fresh (less than 24h old)`() {
        // 23h old → still fresh.
        val cachedAt = now.minus(Duration.ofHours(23))
        val payload = listOf(IncomeStatementDto(symbol = "AAPL", calendarYear = "2024", revenue = 100.0))
        val snapshot = FmpFinancialSnapshot(
            ticker = "AAPL",
            endpoint = "income-statement",
            payload = objectMapper.writeValueAsString(payload),
            fetchedAt = cachedAt,
        )
        every {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("AAPL", "income-statement")
        } returns snapshot

        var fetchFnCalls = 0
        val service = newService()
        val result = service.getOrFetch("AAPL", "income-statement", incomeTypeRef) {
            fetchFnCalls++
            error("fetchFn should NOT be invoked on a fresh cache hit")
        }

        assertThat(fetchFnCalls).isZero()
        assertThat(result.value).hasSize(1)
        assertThat(result.value[0].revenue).isEqualTo(100.0)
        assertThat(result.fetchedAt).isEqualTo(cachedAt)
        assertThat(result.stale).isFalse()
        verify(exactly = 0) { financialRepo.save(any()) }
    }

    @Test
    fun `getOrFetch invokes fetchFn and persists snapshot when cache expired (more than 24h old)`() {
        // 25h old → expired, must refetch.
        val cachedAt = now.minus(Duration.ofHours(25))
        val staleSnapshot = FmpFinancialSnapshot(
            ticker = "AAPL",
            endpoint = "income-statement",
            payload = "[]",
            fetchedAt = cachedAt,
        )
        every {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("AAPL", "income-statement")
        } returns staleSnapshot

        val savedSlot = slot<FmpFinancialSnapshot>()
        every { financialRepo.save(capture(savedSlot)) } answers { savedSlot.captured }

        val fresh = listOf(IncomeStatementDto(symbol = "AAPL", calendarYear = "2025", revenue = 200.0))
        var fetchFnCalls = 0
        val service = newService()
        val result = service.getOrFetch("AAPL", "income-statement", incomeTypeRef) {
            fetchFnCalls++
            fresh
        }

        assertThat(fetchFnCalls).isOne()
        assertThat(result.value).isEqualTo(fresh)
        assertThat(result.fetchedAt).isEqualTo(now)
        assertThat(result.stale).isFalse()
        // Persisted snapshot reflects the new fetch time
        assertThat(savedSlot.captured.ticker).isEqualTo("AAPL")
        assertThat(savedSlot.captured.endpoint).isEqualTo("income-statement")
        assertThat(savedSlot.captured.fetchedAt).isEqualTo(now)
        assertThat(savedSlot.captured.isStale).isFalse()
    }

    @Test
    fun `getOrFetch invokes fetchFn on cold cache (no prior snapshot)`() {
        every {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("MSFT", "income-statement")
        } returns null
        every { financialRepo.save(any()) } answers { firstArg() }

        val fresh = listOf(IncomeStatementDto(symbol = "MSFT", calendarYear = "2024"))
        var fetchFnCalls = 0
        val service = newService()
        val result = service.getOrFetch("MSFT", "income-statement", incomeTypeRef) {
            fetchFnCalls++
            fresh
        }

        assertThat(fetchFnCalls).isOne()
        assertThat(result.value).isEqualTo(fresh)
        verify(exactly = 1) { financialRepo.save(any()) }
    }

    @Test
    fun `getOrFetch uppercases ticker before repository lookup`() {
        every {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("AAPL", "income-statement")
        } returns FmpFinancialSnapshot(
            ticker = "AAPL",
            endpoint = "income-statement",
            payload = "[]",
            fetchedAt = now.minus(Duration.ofMinutes(5)),
        )

        val service = newService()
        service.getOrFetch("aapl", "income-statement", incomeTypeRef) { emptyList() }

        verify(exactly = 1) {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("AAPL", "income-statement")
        }
    }

    // ---------- 1h profile cache ----------

    @Test
    fun `getOrFetchProfile returns cached profile when fresh (less than 1h old)`() {
        val cachedAt = now.minus(Duration.ofMinutes(30))
        val cachedProfile = ProfileDto(symbol = "AAPL", price = 150.0, marketCap = 2_500_000_000.0, sector = "Tech")
        val snapshot = FmpProfileSnapshot(
            ticker = "AAPL",
            rawPayload = objectMapper.writeValueAsString(cachedProfile),
            fetchedAt = cachedAt,
        )
        every { profileRepo.findFirstByTickerOrderByFetchedAtDesc("AAPL") } returns snapshot

        val service = newService()
        val result = service.getOrFetchProfile("AAPL") {
            error("fetchFn should NOT be invoked on a fresh profile cache hit")
        }

        assertThat(result.value.price).isEqualTo(150.0)
        assertThat(result.fetchedAt).isEqualTo(cachedAt)
        verify(exactly = 0) { profileRepo.save(any()) }
        verify(exactly = 0) { stockRepo.save(any()) }
    }

    @Test
    fun `getOrFetchProfile refetches when profile cache older than 1h`() {
        val cachedAt = now.minus(Duration.ofMinutes(61))
        every { profileRepo.findFirstByTickerOrderByFetchedAtDesc("AAPL") } returns
            FmpProfileSnapshot(ticker = "AAPL", rawPayload = "{}", fetchedAt = cachedAt)
        every { profileRepo.save(any()) } answers { firstArg() }
        every { stockRepo.findById("AAPL") } returns Optional.empty()
        every { stockRepo.save(any()) } answers { firstArg() }

        val fresh = ProfileDto(symbol = "AAPL", price = 175.0, sector = "Tech")
        var calls = 0
        val service = newService()
        val result = service.getOrFetchProfile("AAPL") {
            calls++
            fresh
        }

        assertThat(calls).isOne()
        assertThat(result.value.price).isEqualTo(175.0)
        assertThat(result.fetchedAt).isEqualTo(now)
        verify(exactly = 1) { profileRepo.save(any()) }
    }

    @Test
    fun `getOrFetchProfile lazily populates stocks row when ticker is new`() {
        every { profileRepo.findFirstByTickerOrderByFetchedAtDesc("NVDA") } returns null
        every { profileRepo.save(any()) } answers { firstArg() }
        every { stockRepo.findById("NVDA") } returns Optional.empty()
        val stockSlot = slot<Stock>()
        every { stockRepo.save(capture(stockSlot)) } answers { stockSlot.captured }

        val fresh = ProfileDto(
            symbol = "NVDA",
            price = 900.0,
            marketCap = 2_300_000_000_000.0,
            companyName = "NVIDIA Corp",
            sector = "Tech",
            industry = "Semiconductors",
        )
        val service = newService()
        service.getOrFetchProfile("NVDA") { fresh }

        assertThat(stockSlot.captured.ticker).isEqualTo("NVDA")
        assertThat(stockSlot.captured.companyName).isEqualTo("NVIDIA Corp")
        assertThat(stockSlot.captured.sector).isEqualTo("Tech")
        assertThat(stockSlot.captured.industry).isEqualTo("Semiconductors")
        assertThat(stockSlot.captured.lastRefreshedAt).isEqualTo(now)
        verify(exactly = 1) { stockRepo.save(any()) }
    }

    // ---------- getStale fallback ----------

    @Test
    fun `getStale returns expired entry marked stale (used by US-006 fallback)`() {
        val cachedAt = now.minus(Duration.ofDays(3)) // way past 24h
        val payload = listOf(IncomeStatementDto(symbol = "AAPL", revenue = 50.0))
        every {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("AAPL", "income-statement")
        } returns FmpFinancialSnapshot(
            ticker = "AAPL",
            endpoint = "income-statement",
            payload = objectMapper.writeValueAsString(payload),
            fetchedAt = cachedAt,
        )

        val service = newService()
        val result = service.getStale("AAPL", "income-statement", incomeTypeRef)

        assertThat(result).isNotNull
        assertThat(result!!.value[0].revenue).isEqualTo(50.0)
        assertThat(result.fetchedAt).isEqualTo(cachedAt)
        assertThat(result.stale).isTrue()
    }

    @Test
    fun `getStale returns null when no snapshot exists at all`() {
        every {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("UNKNOWN", "income-statement")
        } returns null

        val service = newService()
        val result = service.getStale("UNKNOWN", "income-statement", incomeTypeRef)

        assertThat(result).isNull()
    }

    // ---------- TTL boundary using Clock.offset (advance virtual time) ----------

    @Test
    fun `clock advanced past 24h boundary triggers refetch where 23h ago did not`() {
        // Same cached entry, two different "now" clocks: one inside TTL, one outside.
        val cachedAt = Instant.parse("2026-05-20T10:00:00Z")
        val snapshot = FmpFinancialSnapshot(
            ticker = "AAPL",
            endpoint = "income-statement",
            payload = objectMapper.writeValueAsString(emptyList<IncomeStatementDto>()),
            fetchedAt = cachedAt,
        )
        every {
            financialRepo.findFirstByTickerAndEndpointOrderByFetchedAtDesc("AAPL", "income-statement")
        } returns snapshot
        every { financialRepo.save(any()) } answers { firstArg() }

        // Clock at 23h after cache → HIT (no fetch)
        val freshClock = Clock.fixed(cachedAt.plus(Duration.ofHours(23)), ZoneOffset.UTC)
        var hitFetchCalls = 0
        newService(freshClock).getOrFetch("AAPL", "income-statement", incomeTypeRef) {
            hitFetchCalls++
            emptyList()
        }
        assertThat(hitFetchCalls).isZero()

        // Clock at 25h after cache → MISS (fetch invoked)
        val expiredClock = Clock.fixed(cachedAt.plus(Duration.ofHours(25)), ZoneOffset.UTC)
        var missFetchCalls = 0
        newService(expiredClock).getOrFetch("AAPL", "income-statement", incomeTypeRef) {
            missFetchCalls++
            emptyList()
        }
        assertThat(missFetchCalls).isOne()
    }
}
