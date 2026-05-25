-- V016: price_action_snapshot cache table (US-043)
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-043-price-action-analyzer/TSK-114.md]

CREATE TABLE price_action_snapshot (
    id                    BIGSERIAL    PRIMARY KEY,
    ticker                VARCHAR(20)  NOT NULL,
    calc_date             DATE         NOT NULL,
    price_now             DECIMAL(12,4),
    max_52w               DECIMAL(12,4),
    min_52w               DECIMAL(12,4),
    drawdown_pct          DECIMAL(8,4),
    trend_3m_pct          DECIMAL(8,4),
    ma50                  DECIMAL(12,4),
    ma200                 DECIMAL(12,4),
    panic_discount        BOOLEAN,
    deterioration_warning BOOLEAN,
    series_days           INT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_price_action_ticker_date UNIQUE (ticker, calc_date)
);
