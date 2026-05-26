-- =============================================================================
-- V022 — Top Value Picks batch output (EP-012, US-049, TSK-135)
-- Persistenza idempotente dei top 30 candidati Graham/Buffett per run-date
-- giornaliero del TopValuePicksJob (US-048).
-- PK composta (run_date, ticker) per upsert idempotente su rerun stessa data.
-- TTL retention rolling 90 giorni applicata in TSK-137 (job di cleanup).
-- Rinumerata da V016 (TSK frontmatter) a V022 per collisione V015-V019 EP-011 +
-- V020-V021 EP-012/US-047.
-- [^src: management/kanban/EP-012-batch-top-value-picks/US-049-persistenza-top-picks/TSK-135.md]
-- =============================================================================

CREATE TABLE top_value_picks (
    run_date           DATE          NOT NULL,
    ticker             VARCHAR(10)   NOT NULL REFERENCES stocks(ticker),
    verdetto_classe    VARCHAR(40)   NOT NULL,
    margin_of_safety   NUMERIC(10, 4),
    posizionamento     VARCHAR(40),
    sector             VARCHAR(80),
    market_cap_usd     BIGINT,
    rank_position      INT           NOT NULL,
    source             VARCHAR(20)   NOT NULL,
    company_name       VARCHAR(255),
    rule_signal_summary JSONB,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT top_value_picks_pk PRIMARY KEY (run_date, ticker),
    CONSTRAINT top_value_picks_verdetto_chk
        CHECK (verdetto_classe IN ('APPROVATO', 'APPROVATO_PANIC_BUY',
                                    'WATCHLIST', 'SCARTATO', 'INDETERMINATO')),
    CONSTRAINT top_value_picks_source_chk
        CHECK (source IN ('SCREENER', 'THIRTEEN_F', 'NEWS_SCOUT'))
);

CREATE INDEX idx_top_picks_run_rank
    ON top_value_picks (run_date DESC, rank_position ASC);
CREATE INDEX idx_top_picks_verdetto
    ON top_value_picks (verdetto_classe, run_date DESC);
CREATE INDEX idx_top_picks_ticker
    ON top_value_picks (ticker, run_date DESC);

-- Rollback (manual):
-- DROP INDEX IF EXISTS idx_top_picks_ticker;
-- DROP INDEX IF EXISTS idx_top_picks_verdetto;
-- DROP INDEX IF EXISTS idx_top_picks_run_rank;
-- DROP TABLE IF EXISTS top_value_picks;
