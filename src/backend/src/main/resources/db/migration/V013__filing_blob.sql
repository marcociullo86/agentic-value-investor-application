-- =============================================================================
-- V013 — Filing 10-K/10-Q HTML blob cache (EP-011, US-039, TSK-095)
--
-- Storage per il body HTML completo dei filing SEC scaricati da
-- SecEdgarAdapter.downloadFilingHtml (US-038, TSK-091) o linkati via
-- FmpAdapter.getSecFilings (TSK-094). TTL 90 giorni come da US-039
-- §Business Rules (10-K annuali e 10-Q trimestrali sono immutabili dopo
-- pubblicazione SEC — il TTL è cautelativo per refresh metadata).
--
-- Rinumerato da V011 (originale TSK frontmatter) a V013 per evitare
-- collisione con V011 (`fmp_cache_add_dividends_endpoint`, EP-010 TSK-085)
-- e V012 (`fmp_cache_add_sec_filings_endpoint`, TSK-094).
--
-- Campo `extracted_text`: testo plain estratto dall'HTML (BeautifulSoup-style
-- strip eseguito in Filing10KQDownloaderService, TSK-096), usato come input
-- per pgvector embedding (US-040, TSK-098) e LLM Munger inversion (US-041).
--
-- Tabella DEDICATA (non JSONB in fmp_financial_snapshot): il body HTML pesa
-- tipicamente 0.5-3 MB (limit applicativo 50 MB), inappropriato per JSONB.
-- Anche `html_size_bytes`/`extracted_size_bytes` sono BIGINT per supportare
-- file fino a 50 MB senza overflow su INT.
--
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-095.md]
-- [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md] (cache TTL pattern)
-- [^src: wiki/concepts/sec-filings-analysis.md §Accesso ai filing]
-- =============================================================================

CREATE TABLE filing_blob (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker                VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    cik                   VARCHAR(10)  NOT NULL,
    form_type             VARCHAR(10)  NOT NULL,
    accession_number      VARCHAR(30)  NOT NULL,
    filing_date           DATE         NOT NULL,
    primary_doc_url       TEXT,
    html_body             TEXT,
    html_size_bytes       BIGINT,
    extracted_text        TEXT,
    extracted_size_bytes  BIGINT,
    fetched_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT filing_blob_accession_unique UNIQUE (accession_number),
    CONSTRAINT filing_blob_form_type_chk
        CHECK (form_type IN ('10-K', '10-Q', '10-K/A', '10-Q/A'))
);

-- Query principale: "ultimi N filing di un ticker (10-K/10-Q)".
CREATE INDEX idx_filing_blob_ticker_date
    ON filing_blob (ticker, filing_date DESC);

-- Cleanup TTL job (job applicativo, scope post-TSK-097).
CREATE INDEX idx_filing_blob_expires
    ON filing_blob (expires_at);

-- Analytics future: "tutti gli ultimi 10-K dal 2024".
CREATE INDEX idx_filing_blob_form
    ON filing_blob (form_type, filing_date DESC);
