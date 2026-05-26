-- =============================================================================
-- V024 — Add 'technical-indicators-rsi' + 'technical-indicators-sma' to
--        fmp_financial_snapshot endpoint whitelist
--        (EP-013, US-056 + US-057, TSK-164) — Mr. Market Context Flags.
--
-- Estende la CHECK constraint `fmp_fin_snap_endpoint_chk` (creata in V003,
-- estesa in V011 'dividends', V012 'sec-filings', V020 'company-screener',
-- V021 'search-cusip') per accettare i nuovi label
-- 'technical-indicators-rsi' e 'technical-indicators-sma' usati da
-- ResilientFmpAdapter quando wrappa FmpAdapter.getTechnicalIndicator
-- (`/stable/technical-indicators/{indicator}`).
--
-- Scope EP-013: solo `rsi` (US-056 oversold/neutral/overbought flag) e `sma`
-- (US-057 SMA200 long-term trend). Altri indicator disponibili lato FMP (ema,
-- wma, dema, tema, standarddeviation, williams, adx — vedi raw/fmp_docs.md
-- §Technical Indicators) sono fuori scope EP-013 e NON sono whitelistati qui:
-- aggiungerli con una nuova migration quando entrano in scope (no breaking).
--
-- Endpoint label naming: `technical-indicators-{indicator}` segue la
-- convenzione di ResilientFmpAdapter (vedi `execute("technical-indicators-$indicator", ...)`)
-- per separare le metriche/eventi per indicator type (utile per Resilience4j
-- per-call instrumentation e FmpEventLogger granular tracking).
--
-- [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-164.md]
-- [^src: raw/fmp_docs.md §Technical Indicators]
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
            'search-cusip',
            'technical-indicators-rsi',
            'technical-indicators-sma'
        ));
