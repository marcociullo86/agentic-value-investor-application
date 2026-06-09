-- =============================================================================
-- V034 — Add 'technical-indicators-macd' + 'technical-indicators-atr' +
--        'technical-indicators-obv' to fmp_financial_snapshot endpoint whitelist
--        (EP-024, US-098, TSK-324) — pipeline Technical Analysis (Triple Screen
--        Elder + struttura Murphy).
--
-- Estende la CHECK constraint `fmp_fin_snap_endpoint_chk` (creata in V003,
-- estesa in V011 'dividends', V012 'sec-filings', V020 'company-screener',
-- V021 'search-cusip', V024 'technical-indicators-rsi/sma') per accettare i
-- nuovi label usati da `ResilientFmpAdapter` quando wrappa
-- `FmpAdapter.getMacd/getAtr/getObv` (entry-points che riusano
-- `getTechnicalIndicator` con label endpoint `technical-indicators-{indicator}`).
--
-- Scope EP-024 / US-098 §"Indicatori in scope (sub-set canonico)":
--   - `macd`: MACD(12,26,9) daily + weekly per Triple Screen Elder §39.
--   - `atr`:  ATR14 daily per stop-placement Elder/Murphy §Page 82 (US-100).
--   - `obv`:  On-Balance Volume daily per conferma volume Murphy.
-- Altri indicator FMP (ema, wma, dema, tema, standarddeviation, williams, adx —
-- raw/fmp_docs.md §Technical Indicators) restano fuori scope per evitare il
-- "voting rigging" Elder §39: aggiungerli SOLO con una nuova US con rationale
-- di valore esplicito.
--
-- Endpoint label naming: `technical-indicators-{indicator}` segue la
-- convenzione di ResilientFmpAdapter (vedi `execute("technical-indicators-$indicator", ...)`)
-- per separare le metriche/eventi per indicator type (Resilience4j per-call
-- instrumentation + FmpEventLogger granular tracking).
--
-- NB: queste righe sono whitelist forward-compat. La pipeline TA della Fase 1
-- (TechnicalAnalysisService) NON usa `FmpCacheService.getOrFetch` su questi
-- label — riusa il pattern legacy (LongTermTrendEvaluator/RsiContextEvaluator)
-- e si appoggia direttamente alla chain Resilience4j del ResilientFmpAdapter.
-- La whitelist e' aggiunta qui per non disallineare lo schema DB con il
-- contract degli indicator caricabili e per essere pronti a uno switch a
-- caching cache-aside DB senza migration aggiuntive.
--
-- [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-324.md]
-- [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/US-098.md §"Indicatori in scope"]
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
            'technical-indicators-sma',
            'technical-indicators-macd',
            'technical-indicators-atr',
            'technical-indicators-obv'
        ));
