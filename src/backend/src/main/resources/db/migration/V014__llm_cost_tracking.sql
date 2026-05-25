-- V014: LLM cost telemetry tables (ADR-019)
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-055-llm-budget-admin-config/TSK-155.md]

CREATE TABLE llm_cost_counter (
    year_month       VARCHAR(7)     PRIMARY KEY,
    total_cost_usd   NUMERIC(10,4)  NOT NULL DEFAULT 0,
    total_calls      BIGINT         NOT NULL DEFAULT 0,
    total_tokens_in  BIGINT         NOT NULL DEFAULT 0,
    total_tokens_out BIGINT         NOT NULL DEFAULT 0,
    cache_hits       BIGINT         NOT NULL DEFAULT 0,
    alert_80_sent_at  TIMESTAMPTZ   NULL,
    alert_100_sent_at TIMESTAMPTZ   NULL,
    last_updated     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE llm_call_log (
    id           BIGSERIAL   PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    endpoint     VARCHAR(64),
    purpose      VARCHAR(32),
    ticker       VARCHAR(16),
    user_id      UUID        NULL,
    request_id   UUID,
    model        VARCHAR(64),
    input_tokens INT         NOT NULL,
    output_tokens INT        NOT NULL,
    cost_usd     NUMERIC(10,6) NOT NULL,
    cache_hit    BOOLEAN     NOT NULL DEFAULT false,
    error_code   VARCHAR(32) NULL,
    latency_ms   INT         NOT NULL
);

CREATE INDEX idx_llm_call_log_created ON llm_call_log (created_at);
CREATE INDEX idx_llm_call_log_purpose ON llm_call_log (purpose, created_at);
CREATE INDEX idx_llm_call_log_ticker ON llm_call_log (ticker, created_at);

CREATE TABLE llm_budget_config (
    id                     SMALLINT      PRIMARY KEY CHECK (id = 1),
    monthly_cap_usd        NUMERIC(10,2) NOT NULL,
    alert_threshold_percent SMALLINT     NOT NULL DEFAULT 80,
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by             UUID          NULL REFERENCES users(id)
);

INSERT INTO llm_budget_config (id, monthly_cap_usd) VALUES (1, 50.00) ON CONFLICT DO NOTHING;
