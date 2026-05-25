-- V018: deep_analysis_report cache table (US-041, EP-011)
-- Caching dei risultati analisi Munger-inversione su 10-K + 10-Q.
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-106.md]

CREATE TABLE deep_analysis_report (
    id                   BIGSERIAL    PRIMARY KEY,
    ticker               VARCHAR(20)  NOT NULL,
    filing_combo_hash    VARCHAR(64)  NOT NULL,
    report_json          JSONB        NOT NULL,
    livello_rischio      VARCHAR(30)  NOT NULL,
    generated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ  NOT NULL,
    llm_calls_count      INT,
    CONSTRAINT uq_deep_report_ticker_hash UNIQUE (ticker, filing_combo_hash)
);

CREATE INDEX idx_deep_report_ticker_date ON deep_analysis_report (ticker, generated_at DESC);
CREATE INDEX idx_deep_report_expires ON deep_analysis_report (expires_at);

-- Rollback (manual):
-- DROP INDEX IF EXISTS idx_deep_report_expires;
-- DROP INDEX IF EXISTS idx_deep_report_ticker_date;
-- DROP TABLE IF EXISTS deep_analysis_report;
