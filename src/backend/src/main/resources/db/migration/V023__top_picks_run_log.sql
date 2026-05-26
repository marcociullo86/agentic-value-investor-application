-- =============================================================================
-- V023 — TopValuePicksJob run log (EP-012, US-048, TSK-133)
-- Audit trail di ogni esecuzione del cron 02:00 UTC: durata totale, ticker
-- processati/falliti, count top30, stato esecuzione.
-- Rinumerata da V017 (TSK frontmatter) a V023 per collisione V015-V021.
-- [^src: management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-133.md]
-- =============================================================================

CREATE TABLE top_picks_run_log (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    run_date            DATE          NOT NULL,
    started_at          TIMESTAMPTZ   NOT NULL,
    finished_at         TIMESTAMPTZ,
    duration_seconds    BIGINT,
    tickers_processed   INT           NOT NULL DEFAULT 0,
    tickers_failed      INT           NOT NULL DEFAULT 0,
    top30_count         INT           NOT NULL DEFAULT 0,
    top30_tickers       TEXT,
    status              VARCHAR(20)   NOT NULL,
    error_message       TEXT,
    CONSTRAINT top_picks_run_log_status_chk
        CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED', 'ABORTED'))
);

CREATE INDEX idx_run_log_date ON top_picks_run_log (run_date DESC);
CREATE INDEX idx_run_log_status ON top_picks_run_log (status, started_at DESC);

-- Rollback (manual):
-- DROP INDEX IF EXISTS idx_run_log_status;
-- DROP INDEX IF EXISTS idx_run_log_date;
-- DROP TABLE IF EXISTS top_picks_run_log;
