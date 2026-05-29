-- =============================================================================
-- V027 — deep_analysis_run (EP-011, deep-analysis async execution)
-- Audit trail + result cache di ogni esecuzione asincrona della deep analysis
-- pipeline (DeepAnalysisService.analyze) lanciata via POST
-- /api/analysis/{ticker}/deep/runs. Una row per run; il GET /latest legge
-- l'ultima esecuzione persistita per ticker (status RUNNING|SUCCESS|FAILED).
--
-- result_json   — serializzato DeepAnalysisResponse (populated solo su SUCCESS)
-- error_reason  — allineato alle reason di GlobalExceptionHandler:
--                 not_found | no_sec_filings | llm_unavailable |
--                 embedding_unavailable | internal_error
-- =============================================================================

CREATE TABLE deep_analysis_run (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker          VARCHAR(16)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    invoke_llm      BOOLEAN      NOT NULL,
    requested_at    TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    result_json     TEXT,
    error_reason    VARCHAR(64),
    error_message   TEXT,
    CONSTRAINT deep_analysis_run_status_chk
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_deep_analysis_run_ticker_requested_at
    ON deep_analysis_run (ticker, requested_at DESC);

CREATE INDEX idx_deep_analysis_run_ticker_status
    ON deep_analysis_run (ticker, status);

-- Rollback (manual):
-- DROP INDEX IF EXISTS idx_deep_analysis_run_ticker_status;
-- DROP INDEX IF EXISTS idx_deep_analysis_run_ticker_requested_at;
-- DROP TABLE IF EXISTS deep_analysis_run;
