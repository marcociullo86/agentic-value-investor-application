-- V014: enable pgvector extension + filing_chunks schema + HNSW index
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/TSK-098.md]
-- Embedding dimension: 1024 (Qwen3-Embedding-0.6B, fallback Arctic Embed L v2.0)
-- HNSW params: m=16, ef_construction=64 (pgvector recommended general-purpose)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE filing_chunks (
    id              BIGSERIAL    PRIMARY KEY,
    filing_blob_id  BIGINT       NOT NULL REFERENCES filing_blob(id) ON DELETE CASCADE,
    ticker          VARCHAR(20)  NOT NULL,
    filing_type     VARCHAR(10)  NOT NULL,
    filing_date     DATE,
    chunk_index     INT          NOT NULL,
    content         TEXT         NOT NULL,
    embedding       vector(1024),
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_filing_chunks_blob_chunk UNIQUE (filing_blob_id, chunk_index)
);

CREATE INDEX idx_filing_chunks_hnsw ON filing_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_filing_chunks_ticker ON filing_chunks (ticker, filing_type);
