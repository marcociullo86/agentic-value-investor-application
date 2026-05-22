-- =============================================================================
-- V009 — refresh_tokens.first_issued_at (EP-006, US-019, ADR-010 §3)
-- Tracks the original login timestamp at the head of a refresh chain so that
-- AuthService.refresh() can enforce the absolute 30-day cap (US-019 AC#6).
-- Sliding TTL (7 days) lives in `expires_at`; `first_issued_at` is preserved
-- across refresh rotation (catena conservata).
-- [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §3]
-- [^src: management/kanban/EP-006-watchlist-utente/US-019-login-logout/TSK-040.md]
-- =============================================================================

ALTER TABLE refresh_tokens ADD COLUMN first_issued_at TIMESTAMPTZ;

-- Backfill conservativo per righe pre-esistenti: assume worst-case che il login
-- originale sia avvenuto a (expires_at - 30d). Questo non altera i refresh
-- attivi (expires_at non viene toccato), ma posiziona il cap assoluto in modo
-- che non venga retroattivamente esteso.
UPDATE refresh_tokens
SET first_issued_at = expires_at - INTERVAL '30 days'
WHERE first_issued_at IS NULL;

ALTER TABLE refresh_tokens ALTER COLUMN first_issued_at SET NOT NULL;
