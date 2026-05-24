-- =============================================================================
-- V011 — Extend fmp_financial_snapshot.endpoint CHECK with 'dividends' (TSK-085)
--
-- Rationale (decisione Strategia A vs B documentata in TSK-085 §Scope 1):
--   A) aggiungere 'dividends' al CHECK di fmp_financial_snapshot.endpoint cosi'
--      DividendContinuityRule riusa la cache JSONB centralizzata gia' adottata
--      dai 4 endpoint pesanti (income/balance/cash-flow/key-metrics) via
--      FmpCacheService.getOrFetch(ticker, "dividends", ...). SCELTA.
--   B) bypassare FmpCacheService e scrivere su fmp_dividend_history_snapshot
--      (V010) con repo JPA dedicato. SCARTATA per non frammentare l'astrazione
--      di cache su 5 endpoint analoghi (sempre payload JSON ordinato per data,
--      stesso TTL 24h, stesso fallback stale-on-failure).
--
-- La tabella dedicata fmp_dividend_history_snapshot creata in V010 NON viene
-- rimossa: resta disponibile per analytics future (es. dividend-paying universe
-- screener) che necessitano query SQL dirette su record_date senza JSON
-- unmarshaling. Per il rule engine la cache JSONB e' sufficiente — il rule
-- raggruppa per anno solare lato application.
--
-- [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-085.md §Scope 1]
-- [^src: src/backend/src/main/resources/db/migration/V003__create_fmp_cache.sql §fmp_fin_snap_endpoint_chk]
-- [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Cache layer 24h]
-- =============================================================================

ALTER TABLE fmp_financial_snapshot DROP CONSTRAINT IF EXISTS fmp_fin_snap_endpoint_chk;

ALTER TABLE fmp_financial_snapshot ADD CONSTRAINT fmp_fin_snap_endpoint_chk
    CHECK (endpoint IN ('income-statement',
                        'balance-sheet-statement',
                        'cash-flow-statement',
                        'key-metrics',
                        'dividends'));
