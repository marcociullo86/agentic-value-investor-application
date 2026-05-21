-- =============================================================================
-- V005 - fmp_api_event_log (EP-002, US-006, TSK-011)
-- Audit/observability per gli eventi FMP (rate limiting, 5xx, CB open, fallback
-- stale, ticker non trovato). Popolata in modo asincrono da FmpEventLogger.
--
-- Canonicamente V008 in design_&_architecture/data/schema.sql:155-167, ma il
-- TPM non ha emesso TSK separati per V005-V008 e TSK-011 dichiara dipendenza
-- dura su questa tabella -> migration creata qui come V005 (naming locale).
-- DDL verbatim da schema.sql; nessuna decisione architetturale nuova.
-- [^src: design_&_architecture/data/schema.sql §fmp_api_event_log]
-- [^src: design_&_architecture/data/er-diagram.md §fmp_api_event_log]
-- [^src: design_&_architecture/decisions/ADR-008-observability-logging.md]
-- [^src: management/kanban/.../TSK-011.md §Scope tecnico]
-- =============================================================================

CREATE TABLE fmp_api_event_log (
    id           BIGSERIAL PRIMARY KEY,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type   VARCHAR(40) NOT NULL
                   CHECK (event_type IN ('FMP_429_RATE_LIMITED','FMP_5XX',
                                         'FMP_CIRCUIT_OPEN','FMP_FALLBACK_STALE',
                                         'FMP_TICKER_NOT_FOUND')),
    ticker       VARCHAR(10) REFERENCES stocks(ticker),
    endpoint     VARCHAR(40),
    http_status  INT,
    detail       TEXT
);

-- Lookup: feed dashboard "ultimi eventi per tipo" + cleanup retention job.
-- [^src: design_&_architecture/data/er-diagram.md §fmp_api_event_log] (Indice)
CREATE INDEX fmp_api_event_log_type_time_idx
    ON fmp_api_event_log (event_type, occurred_at DESC);
