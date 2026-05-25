-- =============================================================================
-- V012 — Add 'sec-filings' to fmp_financial_snapshot endpoint whitelist
--        (EP-011, US-039, TSK-094)
--
-- Estende la CHECK constraint `fmp_fin_snap_endpoint_chk` (creata in V003,
-- estesa in V011 per 'dividends') per accettare il nuovo label 'sec-filings'
-- usato da Filing10KQDownloaderService (TSK-096) per cache-aside del response
-- di FmpAdapter.getSecFilings (`/stable/sec-filings-search/symbol`).
--
-- Strategia A "cache centralizzata": riusa la tabella JSONB fmp_financial_snapshot
-- per evitare frammentazione dell'astrazione cache su 6 endpoint analoghi.
-- Tabella dedicata `filing_blob` (V013, TSK-095) cache invece il body HTML del
-- filing (size MB-scale), per il quale JSONB non è appropriato.
--
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-094.md]
-- [^src: raw/fmp_docs.md §Sec Filings — SEC Filings By Symbol API]
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
            'sec-filings'
        ));
