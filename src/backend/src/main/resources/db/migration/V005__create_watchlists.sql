-- =============================================================================
-- V005 — watchlists + watchlist_items (EP-006, US-017, TSK-028)
-- Watchlist personale per utente: una sola default per utente (partial unique
-- index su is_default = true), idempotenza upsert ticker via UNIQUE
-- (watchlist_id, ticker).
-- [^src: design_&_architecture/data/schema.sql §watchlists §watchlist_items]
-- [^src: design_&_architecture/data/er-diagram.md §watchlists §watchlist_items]
-- [^src: management/kanban/EP-006-watchlist-utente/US-017-gestione-watchlist/TSK-028.md §Scope tecnico]
-- =============================================================================

CREATE TABLE watchlists (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL DEFAULT 'My Watchlist',
    is_default  BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Una sola watchlist default per utente (US-017 single-default invariant).
-- [^src: design_&_architecture/data/er-diagram.md §watchlists] (Indice partial)
CREATE UNIQUE INDEX watchlists_one_default_per_user_uidx
    ON watchlists (user_id) WHERE is_default = true;

CREATE TABLE watchlist_items (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    watchlist_id  UUID         NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    ticker        VARCHAR(10)  NOT NULL REFERENCES stocks(ticker),
    added_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (watchlist_id, ticker)
);

-- Lookup: caricamento items di una watchlist ordinati per data di aggiunta.
CREATE INDEX watchlist_items_watchlist_added_idx
    ON watchlist_items (watchlist_id, added_at DESC);
