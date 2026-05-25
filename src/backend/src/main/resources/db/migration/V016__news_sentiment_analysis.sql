-- V016: news_classification table for sentiment analysis (US-042)
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-042-news-sentiment-classifier/TSK-110.md]

CREATE TABLE news_classification (
    id              BIGSERIAL    PRIMARY KEY,
    ticker          VARCHAR(20)  NOT NULL,
    news_id         VARCHAR(200) NOT NULL,
    published_at    TIMESTAMPTZ,
    headline        TEXT,
    url             TEXT,
    sentiment_class VARCHAR(30)  NOT NULL,
    motivazione     VARCHAR(250),
    classified_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_news_classification_newsid UNIQUE (news_id)
);

CREATE INDEX idx_news_class_ticker_date ON news_classification (ticker, classified_at DESC);
