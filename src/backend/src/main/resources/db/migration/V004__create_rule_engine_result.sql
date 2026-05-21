-- =============================================================================
-- V004 — rule_engine_result (EP-003, EP-004, US-014)
-- Verdetto Rule Engine per (ticker, momento di valutazione).
-- [^src: design_&_architecture/data/schema.sql §rule_engine_result]
-- [^src: design_&_architecture/data/er-diagram.md §rule_engine_result]
-- [^src: design_&_architecture/decisions/ADR-005-rule-engine-design.md]
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: rule_engine_result
-- signals JSONB: array [{ruleId, signal, observedValue, threshold, rationale}].
-- graham_number / dcf_intrinsic_value NULL ammessi (Not Applicable / Insufficient Data).
-- source_snapshot_fetched_at: tracciabilita' freschezza snapshot usato (US-005 AC).
-- [^src: design_&_architecture/data/er-diagram.md §rule_engine_result]
-- -----------------------------------------------------------------------------
CREATE TABLE rule_engine_result (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker                      VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    evaluated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    signals                     JSONB        NOT NULL,
    graham_number               NUMERIC(18, 4),
    dcf_intrinsic_value         NUMERIC(18, 4),
    dcf_method                  VARCHAR(32),
    mos_signal                  VARCHAR(32)  NOT NULL,
    current_price_at_eval       NUMERIC(18, 4),
    source_snapshot_fetched_at  TIMESTAMPTZ,
    CONSTRAINT rule_engine_result_dcf_method_chk
        CHECK (dcf_method IS NULL
               OR dcf_method IN ('GREENWALD', 'FCF_FALLBACK', 'NOT_APPLICABLE')),
    CONSTRAINT rule_engine_result_mos_signal_chk
        CHECK (mos_signal IN ('GREEN', 'YELLOW', 'RED', 'NOT_CALCULABLE'))
);

-- Lookup ultimo verdetto per ticker (storico ordinato discendente).
-- [^src: design_&_architecture/data/er-diagram.md §rule_engine_result] (Indice)
CREATE INDEX rule_engine_result_lookup_idx
    ON rule_engine_result (ticker, evaluated_at DESC);
