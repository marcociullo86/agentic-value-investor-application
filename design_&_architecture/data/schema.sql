-- =============================================================================
-- Schema PostgreSQL — WebApp Value Investing
-- Riferimento: design_&_architecture/data/er-diagram.md
-- Note: DDL di riferimento; le migrations Flyway in src/backend/.../db/migration/
--       sono autoritative al momento del primo deploy.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- V001 - users + refresh_tokens (EP-006, ADR-006)
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(72)  NOT NULL,
    display_name    VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX users_email_lower_uidx ON users (LOWER(email));

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_value  VARCHAR(128) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX refresh_tokens_user_active_idx ON refresh_tokens (user_id, revoked_at);

-- -----------------------------------------------------------------------------
-- V002 - stocks catalog
-- -----------------------------------------------------------------------------

CREATE TABLE stocks (
    ticker             VARCHAR(10) PRIMARY KEY,
    company_name       VARCHAR(255),
    sector             VARCHAR(80),
    industry           VARCHAR(120),
    market_cap_usd     NUMERIC(20,2),
    last_refreshed_at  TIMESTAMPTZ
);
CREATE INDEX stocks_sector_idx ON stocks (sector);
CREATE INDEX stocks_market_cap_idx ON stocks (market_cap_usd);

-- -----------------------------------------------------------------------------
-- V003 - FMP cache (US-004, US-005, US-006)
-- -----------------------------------------------------------------------------

CREATE TABLE fmp_financial_snapshot (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker        VARCHAR(10) NOT NULL REFERENCES stocks(ticker),
    endpoint      VARCHAR(40) NOT NULL
                    CHECK (endpoint IN ('income-statement','balance-sheet-statement',
                                        'cash-flow-statement','key-metrics')),
    payload       JSONB NOT NULL,
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_stale      BOOLEAN NOT NULL DEFAULT false,
    stale_reason  TEXT
);
CREATE INDEX fmp_fin_snap_lookup_idx ON fmp_financial_snapshot (ticker, endpoint, fetched_at DESC);

CREATE TABLE fmp_profile_snapshot (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker        VARCHAR(10) NOT NULL REFERENCES stocks(ticker),
    price         NUMERIC(18,4),
    market_cap    NUMERIC(20,2),
    sector        VARCHAR(80),
    industry      VARCHAR(120),
    raw_payload   JSONB,
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX fmp_profile_lookup_idx ON fmp_profile_snapshot (ticker, fetched_at DESC);

-- -----------------------------------------------------------------------------
-- V004 - rule_engine_result (EP-003, EP-004, US-014)
-- -----------------------------------------------------------------------------

CREATE TABLE rule_engine_result (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker                      VARCHAR(10) NOT NULL REFERENCES stocks(ticker),
    evaluated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    signals                     JSONB NOT NULL,
    graham_number               NUMERIC(18,4),
    dcf_intrinsic_value         NUMERIC(18,4),
    dcf_method                  VARCHAR(32)
                                  CHECK (dcf_method IN ('GREENWALD','FCF_FALLBACK','NOT_APPLICABLE')),
    mos_signal                  VARCHAR(32) NOT NULL
                                  CHECK (mos_signal IN ('GREEN','YELLOW','RED','NOT_CALCULABLE')),
    current_price_at_eval       NUMERIC(18,4),
    source_snapshot_fetched_at  TIMESTAMPTZ
);
CREATE INDEX rule_engine_result_lookup_idx ON rule_engine_result (ticker, evaluated_at DESC);

-- -----------------------------------------------------------------------------
-- V005 - watchlists (US-017)
-- -----------------------------------------------------------------------------

CREATE TABLE watchlists (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL DEFAULT 'My Watchlist',
    is_default  BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX watchlists_one_default_per_user_uidx
    ON watchlists (user_id) WHERE is_default = true;

CREATE TABLE watchlist_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    watchlist_id  UUID NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    ticker        VARCHAR(10) NOT NULL REFERENCES stocks(ticker),
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (watchlist_id, ticker)
);

-- -----------------------------------------------------------------------------
-- V006 - moat_checklist_entry (US-016)
-- -----------------------------------------------------------------------------

CREATE TABLE moat_checklist_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker      VARCHAR(10) NOT NULL REFERENCES stocks(ticker),
    moat_type   VARCHAR(40) NOT NULL
                  CHECK (moat_type IN ('INTANGIBLE_ASSETS','SWITCHING_COSTS',
                                       'NETWORK_EFFECT','COST_ADVANTAGE')),
    status      VARCHAR(20) NOT NULL
                  CHECK (status IN ('PRESENT','PARTIAL','ABSENT')),
    note        TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, ticker, moat_type)
);

-- -----------------------------------------------------------------------------
-- V007 - dcf_method_override (US-012)
-- -----------------------------------------------------------------------------

CREATE TABLE dcf_method_override (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker         VARCHAR(10) NOT NULL REFERENCES stocks(ticker),
    forced_method  VARCHAR(32) NOT NULL
                     CHECK (forced_method IN ('GREENWALD','FCF_FALLBACK')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, ticker)
);

-- -----------------------------------------------------------------------------
-- V008 - fmp_api_event_log (US-006, ADR-008)
-- -----------------------------------------------------------------------------

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
CREATE INDEX fmp_api_event_log_type_time_idx ON fmp_api_event_log (event_type, occurred_at DESC);
