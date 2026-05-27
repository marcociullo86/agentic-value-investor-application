-- V025: login attempt audit for rate limiting and brute-force protection (US-081, TSK-226).
-- Retention purge (> 90 days) is TSK-230 (@Scheduled).
-- [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Schema DB]
-- [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-226.md]

CREATE TABLE login_attempts (
    id              BIGSERIAL       PRIMARY KEY,
    ip_address      VARCHAR(45)     NOT NULL,
    account_email   VARCHAR(255),
    attempted_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    success         BOOLEAN         NOT NULL,
    failure_reason  VARCHAR(100),
    user_agent      VARCHAR(500)
);

CREATE INDEX idx_login_attempts_ip_recent
    ON login_attempts (ip_address, attempted_at DESC);

CREATE INDEX idx_login_attempts_email_recent
    ON login_attempts (account_email, attempted_at DESC)
    WHERE account_email IS NOT NULL;

-- Rollback (manual):
-- DROP INDEX IF EXISTS idx_login_attempts_email_recent;
-- DROP INDEX IF EXISTS idx_login_attempts_ip_recent;
-- DROP TABLE IF EXISTS login_attempts;
