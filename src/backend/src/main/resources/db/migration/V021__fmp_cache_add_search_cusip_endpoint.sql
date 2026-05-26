-- =============================================================================
-- V021 — Add 'search-cusip' to fmp_financial_snapshot endpoint whitelist
--        (EP-012, US-047, TSK-127) — Sprint 9 Top Value Picks batch.
--
-- Estende la CHECK constraint `fmp_fin_snap_endpoint_chk` (creata in V003,
-- estesa in V011 'dividends', V012 'sec-filings', V020 'company-screener')
-- per accettare il nuovo label 'search-cusip' usato (potenzialmente) da
-- InstitutionalHoldingsService (TSK-127) per il caching della risoluzione
-- CUSIP→ticker via FmpAdapter.searchCusip (`/stable/search-cusip`).
--
-- NB: il caching DB per search-cusip e' OPZIONALE — la versione iniziale di
-- InstitutionalHoldingsService caches l'intero risultato 13-F (per fund CIK)
-- in una cache Caffeine in-memory (institutionalHoldingsCache, TTL 7gg);
-- il CUSIP lookup individuale puo' essere ri-eseguito on cache miss. Questa
-- migration prepara la whitelist per estensioni future senza ulteriori DDL.
--
-- [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-127.md]
-- [^src: raw/fmp_docs.md §CUSIPAPI]
-- =============================================================================

ALTER TABLE fmp_financial_snapshot
    DROP CONSTRAINT IF EXISTS fmp_fin_snap_endpoint_chk;

ALTER TABLE fmp_financial_snapshot
    ADD CONSTRAINT fmp_fin_snap_endpoint_chk
        CHECK (endpoint IN (
            'income-statement',
            'balance-sheet-statement',
            'cash-flow-statement',
            'key-metrics',
            'dividends',
            'sec-filings',
            'company-screener',
            'search-cusip'
        ));
