-- =============================================================================
-- V028 — deep_analysis_run.kind (EP-011, split INGEST vs ANALYSIS)
-- Splits the deep-analysis async pipeline into two distinct operations sharing
-- the deep_analysis_run audit table:
--
--   INGEST   — downloads SEC filings (10-K / 10-Q) and persists embeddings via
--              FilingRagService. Idempotent: re-runs skip already-indexed
--              filings. Has no LLM cost.
--   ANALYSIS — runs the deterministic rule engine + verdict and, when invokeLlm
--              is true, the Munger-inversion LLM step that REUSES the
--              embeddings already produced by a previous INGEST run.
--
-- Backfill: every pre-existing row predates the split and represents a legacy
-- combined run that was, semantically, an ANALYSIS (it produced result_json
-- with a full DeepAnalysisResponse). Default is 'ANALYSIS' so existing GET
-- /latest queries continue to match.
--
-- The (ticker, kind, requested_at DESC) index supports the kind-filtered
-- lookups added in DeepAnalysisRunRepository (latest INGEST vs latest
-- ANALYSIS, plus per-kind dedupe on status=RUNNING).
-- =============================================================================

ALTER TABLE deep_analysis_run
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'ANALYSIS';

ALTER TABLE deep_analysis_run
    ADD CONSTRAINT deep_analysis_run_kind_chk
    CHECK (kind IN ('INGEST', 'ANALYSIS'));

CREATE INDEX idx_deep_analysis_run_ticker_kind_requested_at
    ON deep_analysis_run (ticker, kind, requested_at DESC);

-- Rollback (manual):
-- DROP INDEX IF EXISTS idx_deep_analysis_run_ticker_kind_requested_at;
-- ALTER TABLE deep_analysis_run DROP CONSTRAINT IF EXISTS deep_analysis_run_kind_chk;
-- ALTER TABLE deep_analysis_run DROP COLUMN IF EXISTS kind;
