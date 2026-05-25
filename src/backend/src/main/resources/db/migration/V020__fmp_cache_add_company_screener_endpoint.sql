-- =============================================================================
-- V020 — Add 'company-screener' to fmp_financial_snapshot endpoint whitelist
--        (EP-012, US-047, TSK-129) — Sprint 9 Top Value Picks batch.
--
-- Estende la CHECK constraint `fmp_fin_snap_endpoint_chk` (creata in V003,
-- estesa in V011 per 'dividends', V012 per 'sec-filings') per accettare il
-- nuovo label 'company-screener' usato da UniverseScreenerService (TSK-126)
-- per cache-aside del response di FmpAdapter.screen
-- (`/stable/company-screener`).
--
-- Strategia A "cache centralizzata": riusa la tabella JSONB fmp_financial_snapshot
-- per evitare frammentazione dell'astrazione cache. TTL caller-side: 6h
-- (screener non cambia frequentemente).
--
-- [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-129.md]
-- [^src: raw/fmp_docs.md §Stock ScreenerAPI]
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
            'company-screener'
        ));
