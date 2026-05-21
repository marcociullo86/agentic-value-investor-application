-- =============================================================================
-- V003 — FMP cache snapshots (EP-002, US-004, US-005, US-006)
-- fmp_financial_snapshot: cache JSONB delle 4 chiamate FMP "pesanti" (TTL 24h).
-- fmp_profile_snapshot:   cache prezzo + meta profilo (TTL piu' breve, proposta 1h).
-- [^src: design_&_architecture/data/schema.sql §fmp_financial_snapshot,§fmp_profile_snapshot]
-- [^src: design_&_architecture/data/er-diagram.md §fmp_financial_snapshot,§fmp_profile_snapshot]
-- [^src: design_&_architecture/decisions/ADR-003-database-postgresql.md] (JSONB rationale)
-- [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md] (cache 24h, TTL profilo)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: fmp_financial_snapshot
-- Una riga per (ticker, endpoint, fetched_at). Payload JSONB con array di
-- periodi (fino a 10). `is_stale` marcato true quando servito come fallback
-- (US-006). Lookup pattern: ultima versione per (ticker, endpoint).
-- [^src: design_&_architecture/data/er-diagram.md §fmp_financial_snapshot]
-- -----------------------------------------------------------------------------
CREATE TABLE fmp_financial_snapshot (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker        VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    endpoint      VARCHAR(40)  NOT NULL,
    payload       JSONB        NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_stale      BOOLEAN      NOT NULL DEFAULT false,
    stale_reason  TEXT,
    CONSTRAINT fmp_fin_snap_endpoint_chk
        CHECK (endpoint IN ('income-statement',
                            'balance-sheet-statement',
                            'cash-flow-statement',
                            'key-metrics'))
);

-- Lookup ultima versione per (ticker, endpoint) — query principale TTL check.
-- [^src: design_&_architecture/data/er-diagram.md §fmp_financial_snapshot] (Indice)
CREATE INDEX fmp_fin_snap_lookup_idx
    ON fmp_financial_snapshot (ticker, endpoint, fetched_at DESC);

-- -----------------------------------------------------------------------------
-- Table: fmp_profile_snapshot
-- Cache prezzo + meta profilo. price/market_cap denormalizzati per query veloci;
-- raw_payload conserva profilo FMP completo per evoluzione futura.
-- [^src: design_&_architecture/data/er-diagram.md §fmp_profile_snapshot]
-- -----------------------------------------------------------------------------
CREATE TABLE fmp_profile_snapshot (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker       VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    price        NUMERIC(18, 4),
    market_cap   NUMERIC(20, 2),
    sector       VARCHAR(80),
    industry     VARCHAR(120),
    raw_payload  JSONB,
    fetched_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Lookup ultima versione per ticker (TTL profilo, ADR-004 default 1h).
-- [^src: design_&_architecture/data/er-diagram.md §fmp_profile_snapshot] (Indice)
CREATE INDEX fmp_profile_lookup_idx
    ON fmp_profile_snapshot (ticker, fetched_at DESC);
