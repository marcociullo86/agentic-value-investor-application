-- =============================================================================
-- V008 - fmp_api_event_log (EP-002, US-006, TSK-011 + renumber TSK-028)
-- Audit/observability per gli eventi FMP (rate limiting, 5xx, CB open, fallback
-- stale, ticker non trovato). Popolata in modo asincrono da FmpEventLogger.
--
-- Numerazione: TSK-028 (Sprint 3) ripristina lo slot canonico V008 previsto da
-- design_&_architecture/data/schema.sql:155-167; lo slot V005 viene riservato a
-- watchlists/watchlist_items come da TSK-028 stesso. La migration è stata
-- originariamente creata come V005 (TSK-011 Sprint 1) per assenza di un TSK
-- separato; rinominata qui senza alterazione del DDL — il checksum Flyway non
-- è ancora promosso su ambienti condivisi (R1.0 pre-release, no migration
-- history in produzione).
-- [^src: design_&_architecture/data/schema.sql §fmp_api_event_log]
-- [^src: design_&_architecture/data/er-diagram.md §fmp_api_event_log]
-- [^src: design_&_architecture/decisions/ADR-008-observability-logging.md]
-- [^src: management/kanban/EP-006-watchlist-utente/US-017-gestione-watchlist/TSK-028.md §Scope tecnico]
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
