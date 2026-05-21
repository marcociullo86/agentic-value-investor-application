-- =============================================================================
-- V007 — dcf_method_override (US-012, TSK-017)
-- Per-user forced DCF method (GREENWALD vs FCF_FALLBACK).
-- [^src: design_&_architecture/data/schema.sql §V007]
-- [^src: design_&_architecture/data/er-diagram.md §dcf_method_override]
-- =============================================================================

CREATE TABLE dcf_method_override (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker         VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    forced_method  VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT dcf_method_override_forced_method_chk
        CHECK (forced_method IN ('GREENWALD', 'FCF_FALLBACK')),
    CONSTRAINT dcf_method_override_user_ticker_uidx UNIQUE (user_id, ticker)
);

CREATE INDEX dcf_method_override_user_idx ON dcf_method_override (user_id);
