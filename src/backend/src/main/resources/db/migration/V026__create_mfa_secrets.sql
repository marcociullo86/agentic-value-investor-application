-- =============================================================================
-- V026 — mfa_secrets (US-081, EP-018, TSK-225)
-- TOTP secret (encrypted) and recovery-code hashes per user for MFA enrollment/login.
-- [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Schema DB]
-- [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-225.md]
-- Note: TSK-225 names V018 (occupied by filing_analysis); V025 used by TSK-226 login_attempts.
-- =============================================================================

CREATE TABLE mfa_secrets (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    totp_secret_encrypted   VARCHAR(255) NOT NULL,
    enabled                 BOOLEAN      NOT NULL DEFAULT false,
    enabled_at              TIMESTAMPTZ,
    recovery_codes_hash     TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Rollback (manual):
-- DROP TABLE IF EXISTS mfa_secrets;
