-- =============================================================================
-- V002 — stocks catalog (EP-001, US-001, US-002)
-- Lazy-populated catalog of known tickers (source: FMP profile/search).
-- [^src: design_&_architecture/data/schema.sql §stocks]
-- [^src: design_&_architecture/data/er-diagram.md §stocks]
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: stocks
-- ticker is the natural primary key (uppercase, max 10 chars per FMP convention).
-- [^src: design_&_architecture/data/er-diagram.md §stocks]
-- -----------------------------------------------------------------------------
CREATE TABLE stocks (
    ticker             VARCHAR(10)    PRIMARY KEY,
    company_name       VARCHAR(255),
    sector             VARCHAR(80),
    industry           VARCHAR(120),
    market_cap_usd     NUMERIC(20, 2),
    last_refreshed_at  TIMESTAMPTZ,
    CONSTRAINT stocks_ticker_uppercase_chk CHECK (ticker = UPPER(ticker))
);

-- Index on sector for screener filter (US-002).
-- [^src: design_&_architecture/data/er-diagram.md §stocks] (Indice: (sector))
CREATE INDEX stocks_sector_idx ON stocks (sector);

-- Index on market_cap_usd for screener range queries (US-002).
-- [^src: design_&_architecture/data/er-diagram.md §stocks] (Indice: (market_cap_usd))
CREATE INDEX stocks_market_cap_idx ON stocks (market_cap_usd);
