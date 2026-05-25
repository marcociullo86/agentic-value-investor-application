-- V018: audit trail for deep analysis pipeline executions (US-045, TSK-119).
-- Each GET /api/analysis/{ticker}/deep invocation inserts one row.
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-045-endpoint-deep-analysis/TSK-119.md]

CREATE TABLE deep_analysis_event_log (
    id                BIGSERIAL    PRIMARY KEY,
    ticker            VARCHAR(20)  NOT NULL,
    generated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cache_hits        INT,
    llm_calls         INT,
    total_duration_ms BIGINT
);

CREATE INDEX idx_deep_event_log_ticker ON deep_analysis_event_log (ticker, generated_at DESC);

-- Rollback (manual):
-- DROP INDEX IF EXISTS idx_deep_event_log_ticker;
-- DROP TABLE IF EXISTS deep_analysis_event_log;
