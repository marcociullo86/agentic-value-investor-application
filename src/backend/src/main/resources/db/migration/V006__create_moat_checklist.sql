-- =============================================================================
-- V006 — moat_checklist_entry (EP-005, US-016, TSK-025)
-- Annotazione qualitativa Moat per (utente, ticker, tipo). Non altera il
-- RuleEngineResult (US-016 AC).
-- [^src: design_&_architecture/data/schema.sql §moat_checklist_entry]
-- [^src: design_&_architecture/data/er-diagram.md §moat_checklist_entry]
-- [^src: management/kanban/EP-005-dashboard-traffic-light-moat/US-016-checklist-moat/TSK-025.md §Scope tecnico]
-- =============================================================================

CREATE TABLE moat_checklist_entry (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker      VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    moat_type   VARCHAR(40)  NOT NULL
                    CHECK (moat_type IN ('INTANGIBLE_ASSETS','SWITCHING_COSTS',
                                         'NETWORK_EFFECT','COST_ADVANTAGE')),
    status      VARCHAR(20)  NOT NULL
                    CHECK (status IN ('PRESENT','PARTIAL','ABSENT')),
    note        TEXT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, ticker, moat_type)
);
