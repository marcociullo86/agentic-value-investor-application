-- V012: filing_blob table — cache HTML filing 10-K/10-Q (prerequisite for V013 filing_chunks FK)
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-095.md]

CREATE TABLE filing_blob (
    id               BIGSERIAL    PRIMARY KEY,
    ticker           VARCHAR(20)  NOT NULL,
    cik              VARCHAR(10),
    form_type        VARCHAR(10)  NOT NULL,
    accession_number VARCHAR(50),
    filed_at         DATE,
    html_blob        TEXT,
    chunkable_text   TEXT,
    fetched_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    blob_size_bytes  INT,
    CONSTRAINT uq_filing_blob_accession UNIQUE (accession_number)
);

CREATE INDEX idx_filing_blob_ticker_form ON filing_blob (ticker, form_type, filed_at DESC);
CREATE INDEX idx_filing_blob_expires ON filing_blob (expires_at);
