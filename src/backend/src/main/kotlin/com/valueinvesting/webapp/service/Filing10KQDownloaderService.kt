package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.SecFilingFmpDto
import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import com.valueinvesting.webapp.secedgar.SecEdgarAdapter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// Orchestrator: discovery via FmpAdapter.getSecFilings → download HTML via
// SecEdgarAdapter → strip HTML (Jsoup) → persist to filing_blob with 90-day TTL.
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-096.md]
// [^src: wiki/runbooks/sec-10k-10q-analysis-playbook.md §Step 1 — Recupero filing]
// [^src: wiki/concepts/sec-filings-analysis.md §Accesso ai filing]
@Service
class Filing10KQDownloaderService(
    private val fmpAdapter: FmpAdapter,
    private val secEdgarAdapter: SecEdgarAdapter,
    private val filingBlobRepository: FilingBlobRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        val CACHE_TTL: Duration = Duration.ofDays(90)
        const val MAX_BLOB_SIZE_BYTES: Long = 50L * 1024 * 1024 // 50 MB
    }

    /**
     * Fetches the latest 10-K/10-Q filings for [ticker], downloads & caches
     * any not already present (or expired), and returns all valid cached blobs.
     */
    @Transactional
    fun fetchAndCache(ticker: String): List<FilingBlobEntity> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val upperTicker = ticker.uppercase()
        val now = Instant.now(clock)

        val fmpFilings = fmpAdapter.getSecFilings(upperTicker)
        if (fmpFilings.isEmpty()) {
            log.info("No SEC filings found via FMP for ticker={}", upperTicker)
            return filingBlobRepository
                .findByTickerAndExpiresAtAfterOrderByFilingDateDesc(upperTicker, now)
        }

        log.info("FMP returned {} filings for ticker={}", fmpFilings.size, upperTicker)

        for (filing in fmpFilings) {
            processOneFiling(filing, upperTicker, now)
        }

        return filingBlobRepository
            .findByTickerAndExpiresAtAfterOrderByFilingDateDesc(upperTicker, now)
    }

    private fun processOneFiling(filing: SecFilingFmpDto, ticker: String, now: Instant) {
        val accessionNumber = extractAccessionNumber(filing)
        if (accessionNumber == null) {
            log.warn(
                "Cannot extract accession number from filing link={} finalLink={} — skipping",
                filing.link, filing.finalLink,
            )
            return
        }

        val cached = filingBlobRepository.findByAccessionNumberAndExpiresAtAfter(accessionNumber, now)
        if (cached != null) {
            log.debug("Cache hit for accession={} ticker={}", accessionNumber, ticker)
            return
        }

        val downloadUrl = filing.finalLink ?: filing.link
        if (downloadUrl.isNullOrBlank()) {
            log.warn("No download URL for accession={} ticker={} — skipping", accessionNumber, ticker)
            return
        }

        val html = try {
            secEdgarAdapter.downloadFilingHtml(downloadUrl)
        } catch (ex: Exception) {
            log.error(
                "Failed to download filing HTML for accession={} url={}: {}",
                accessionNumber, downloadUrl, ex.message,
            )
            return
        }

        if (html.isNullOrBlank()) {
            log.warn("Empty HTML response for accession={} url={}", accessionNumber, downloadUrl)
            return
        }

        val htmlSizeBytes = html.toByteArray(Charsets.UTF_8).size.toLong()
        if (htmlSizeBytes > MAX_BLOB_SIZE_BYTES) {
            log.warn(
                "Filing exceeds 50MB limit: accession={} size={} bytes — skipping persist",
                accessionNumber, htmlSizeBytes,
            )
            return
        }

        val extractedText = Jsoup.parse(html).text()
        val extractedSizeBytes = extractedText.toByteArray(Charsets.UTF_8).size.toLong()

        val existing = filingBlobRepository.findByAccessionNumber(accessionNumber)
        if (existing != null) {
            existing.htmlBody = html
            existing.htmlSizeBytes = htmlSizeBytes
            existing.extractedText = extractedText
            existing.extractedSizeBytes = extractedSizeBytes
            existing.fetchedAt = now
            existing.expiresAt = now.plus(CACHE_TTL)
            existing.primaryDocUrl = downloadUrl
            filingBlobRepository.save(existing)
            log.info("Refreshed expired filing accession={} ticker={}", accessionNumber, ticker)
        } else {
            val entity = FilingBlobEntity(
                ticker = ticker,
                cik = filing.cik ?: "",
                formType = filing.formType ?: "",
                accessionNumber = accessionNumber,
                filingDate = parseFilingDate(filing.filingDate),
                primaryDocUrl = downloadUrl,
                htmlBody = html,
                htmlSizeBytes = htmlSizeBytes,
                extractedText = extractedText,
                extractedSizeBytes = extractedSizeBytes,
                fetchedAt = now,
                expiresAt = now.plus(CACHE_TTL),
            )
            filingBlobRepository.save(entity)
            log.info(
                "Persisted new filing accession={} ticker={} formType={} size={}",
                accessionNumber, ticker, filing.formType, htmlSizeBytes,
            )
        }
    }

    /**
     * Extracts the SEC accession number from the FMP-provided link or finalLink.
     * SEC EDGAR URL format:
     *   `https://www.sec.gov/Archives/edgar/data/{cik}/{accessionNoDashes}/{doc}`
     * The accession folder is the 7th path segment (0-indexed: 5).
     * Formatted with dashes: `XXXXXXXXXX-YY-ZZZZZZ` (10-2-6).
     */
    internal fun extractAccessionNumber(filing: SecFilingFmpDto): String? {
        val url = filing.finalLink ?: filing.link ?: return null
        return extractAccessionFromUrl(url)
    }

    internal fun extractAccessionFromUrl(url: String): String? {
        val segments = url.removePrefix("https://").removePrefix("http://")
            .split("/")

        // Expected: www.sec.gov / Archives / edgar / data / {cik} / {accNoDashes} / {doc}
        //           0            1          2       3      4        5               6
        if (segments.size < 7) return null

        val raw = segments[5]
        if (raw.length < 18) return null

        // Format: 10-char CIK + 2-char year + 6-char sequence → dashed
        return "${raw.substring(0, 10)}-${raw.substring(10, 12)}-${raw.substring(12)}"
    }

    private fun parseFilingDate(dateStr: String?): LocalDate {
        if (dateStr.isNullOrBlank()) return LocalDate.EPOCH
        return try {
            LocalDate.parse(dateStr)
        } catch (_: DateTimeParseException) {
            log.warn("Unparseable filing date: {}", dateStr)
            LocalDate.EPOCH
        }
    }
}
