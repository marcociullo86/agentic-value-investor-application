-- =============================================================================
-- V010 — FMP dividend history snapshot (EP-010, US-037, TSK-084)
-- Cache della chiamata FMP /stable/dividends (storico dividendi annuali/quarterly).
-- Tabella DEDICATA (non JSONB in fmp_financial_snapshot) per consentire query
-- SQL dirette su record_date senza JSON unmarshaling — utile per analytics
-- future (es. dividend-paying universe screener) e per il rule
-- DividendContinuityRule (TSK-085) che conta anni consecutivi.
--
-- Decisione Strategia A vs B:
--   A) tabella dedicata fmp_dividend_history_snapshot con colonne tipizzate
--      (record_date DATE, dividend NUMERIC) — scelta.
--   B) aggiungere 'dividends' al CHECK di fmp_financial_snapshot.endpoint e
--      serializzare l'array in JSONB — scartata: il rule deve aggregare per
--      anno fiscale e contare gap; SQL diretto su DATE e' molto piu' semplice
--      e indicizzabile rispetto a unmarshaling JSON.
--
-- Pattern TTL allineato a V003 (fetched_at + expires_at, default 24h gestito
-- application-side per coerenza con ADR-004).
--
-- [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-084.md §Cosa fare]
-- [^src: raw/fmp_docs.md §Earnings, Dividends, Splits — /stable/dividends]
-- [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md] (cache TTL 24h)
-- [^src: design_&_architecture/decisions/ADR-003-database-postgresql.md] (UUID + gen_random_uuid)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: fmp_dividend_history_snapshot
-- Una riga per (ticker, record_date). record_date e' l'ex-dividend date
-- restituita da FMP. dividend = importo dichiarato, adj_dividend = importo
-- aggiustato per split. label e' la frequenza (Quarterly/Annual/Semi-Annual).
-- TTL gestito via expires_at; il job di cleanup elimina righe scadute.
-- [^src: raw/fmp_docs.md §/stable/dividends fields]
-- -----------------------------------------------------------------------------
CREATE TABLE fmp_dividend_history_snapshot (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker        VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    record_date   DATE         NOT NULL,
    dividend      NUMERIC(12, 6),
    adj_dividend  NUMERIC(12, 6),
    label         VARCHAR(100),
    fetched_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL
);

-- Lookup principale: ultimi N record per ticker ordinati per data discendente.
-- Usato da DividendContinuityRule (TSK-085) per scorrere gli ultimi 10 anni.
CREATE INDEX idx_fmp_div_hist_ticker_date
    ON fmp_dividend_history_snapshot (ticker, record_date DESC);

-- Scan per cleanup job TTL (WHERE expires_at < now()).
CREATE INDEX idx_fmp_div_hist_expires
    ON fmp_dividend_history_snapshot (expires_at);
