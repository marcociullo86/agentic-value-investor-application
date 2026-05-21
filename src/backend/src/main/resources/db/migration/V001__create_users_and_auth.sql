-- =============================================================================
-- V001 — users + refresh_tokens (EP-006, US-017)
-- Reversible DDL for foundational authentication schema.
-- [^src: design_&_architecture/data/schema.sql §users]
-- [^src: design_&_architecture/data/er-diagram.md §users]
-- [^src: design_&_architecture/decisions/ADR-006-authentication.md §Schema utenti]
-- [^src: design_&_architecture/decisions/ADR-003-database-postgresql.md §Flyway]
-- =============================================================================

-- pgcrypto required for gen_random_uuid().
-- [^src: design_&_architecture/data/schema.sql §extensions]
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- Table: users
-- Identity for authentication. password_hash sized for BCrypt cost 12 (60 chars + slack).
-- [^src: design_&_architecture/decisions/ADR-006-authentication.md §Schema utenti]
-- [^src: design_&_architecture/data/er-diagram.md §users]
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(72)  NOT NULL,
    display_name    VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

-- Case-insensitive uniqueness on email (partial unique index on LOWER(email)).
-- [^src: design_&_architecture/data/er-diagram.md §users] (Indice: UNIQUE (lower(email)))
CREATE UNIQUE INDEX users_email_lower_uidx ON users (LOWER(email));

-- -----------------------------------------------------------------------------
-- Table: refresh_tokens
-- Long-lived refresh tokens; revoked tokens kept for audit until purge job.
-- [^src: design_&_architecture/data/er-diagram.md §refresh_tokens]
-- [^src: design_&_architecture/decisions/ADR-006-authentication.md §Refresh tokens]
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_value  VARCHAR(128) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ
);

-- Lookup index for active tokens per user (used on refresh + revocation flows).
-- [^src: design_&_architecture/data/er-diagram.md §refresh_tokens] (Indice: (user_id, revoked_at))
CREATE INDEX refresh_tokens_user_active_idx ON refresh_tokens (user_id, revoked_at);
