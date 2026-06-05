---
type: concept
sources:
  - "management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md"
  - "management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/TSK-098.md"
  - "design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md"
  - "raw/agent.py"
status: review
created: 2026-05-25
tags: [pgvector, vector-store, postgresql, rag, ep-011, sec-filings, embedding, platform-domain]
domain: platform
---
# pgvector Vector Store — `filing_chunks`

> Strato di persistenza vettoriale della pipeline RAG EP-011: estensione PostgreSQL 17 `vector`, schema `filing_chunks` con colonna `embedding vector(1024)`, indice HNSW e idempotenza per chunk di filing 10-K/10-Q.

## Contesto

EP-011 (Deep Analysis 10-K/10-Q) introduce RAG su filing SEC nella WebApp Value Investing. La scelta del vector store — **pgvector** integrato in PostgreSQL 17 — è una decisione di prodotto confermata dall'utente il 2026-05-23. La decisione non aggiunge dipendenze infrastrutturali: pgvector è un'estensione del DB PostgreSQL già presente nello stack (ADR-003). [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md §Business Rules]

Alternativa esaminata e scartata: FAISS in-memory (usato dal prototipo Python `agent.py`, ma non persistente tra restart). [^src: raw/agent.py:1353-1372]

## Abilitazione dell'estensione

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Richiede l'immagine Docker `pgvector/pgvector:pg17` invece di `postgres:17` nel `docker-compose.yml`. Il backend Kotlin usa l'extension trasparentemente tramite JDBC. [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/TSK-098.md §Note implementative]

## Schema `filing_chunks`

Migration Flyway: `V013__filing_blob.sql` (prerequisite FK) + `V014__pgvector_enable_filing_chunks.sql` (extension + schema + HNSW). [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/TSK-098.md §Cosa fare]

```sql
CREATE TABLE filing_chunks (
  id              BIGSERIAL PRIMARY KEY,
  filing_blob_id  BIGINT       NOT NULL REFERENCES filing_blob(id) ON DELETE CASCADE,
  ticker          VARCHAR(20)  NOT NULL,
  filing_type     VARCHAR(10)  NOT NULL,   -- '10-K' | '10-Q'
  filing_date     DATE,
  chunk_index     INT          NOT NULL,
  content         TEXT         NOT NULL,
  embedding       vector(1024),            -- Qwen3-Embedding-0.6B, Matryoshka 1024-dim
  metadata        JSONB,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_filing_chunks_blob_chunk UNIQUE (filing_blob_id, chunk_index)
);
```

### Colonne principali

| Colonna | Tipo | Nota |
|---------|------|------|
| `filing_blob_id` | `BIGINT FK` | Riferimento alla riga `filing_blob` (US-039) con ON DELETE CASCADE |
| `ticker` | `VARCHAR(20)` | Denormalizzato per query efficiente senza JOIN |
| `filing_type` | `VARCHAR(10)` | `'10-K'` o `'10-Q'` |
| `chunk_index` | `INT` | Indice 0-based all'interno del filing |
| `content` | `TEXT` | Testo del chunk (6000 caratteri, overlap 400) |
| `embedding` | `vector(1024)` | Embedding semantico generato da [[arctic-embed-l-v2]] (Qwen3-Embedding-0.6B) |
| `metadata` | `JSONB` | Sezione del filing di provenienza, page ref, etc. (opzionale) |

## Indice HNSW

```sql
CREATE INDEX idx_filing_chunks_hnsw ON filing_chunks
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_filing_chunks_ticker ON filing_chunks (ticker, filing_type);
```

### Parametri HNSW

| Parametro | Valore | Razionale |
|-----------|--------|-----------|
| `m` | `16` | Default pgvector raccomandato per uso generale; bilancia recall/throughput |
| `ef_construction` | `64` | Default pgvector; tunable in migration per recall > 99% su corpus > 1000 chunk |
| `vector_cosine_ops` | — | Cosine Similarity coerente con L2-normalization degli embedding (`normalize=true`) |

Target performance: query similarity top-K su corpus di 10 filing deve completare in < 200 ms. [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md §Acceptance Criteria]

## Parametri chunking

Esposti in `application.yaml` per tuning senza ricompilazione: [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md §Business Rules]

| Property | Default | Env var |
|----------|---------|---------|
| `chunking.chunk-size` | `6000` (char) | `CHUNKING_CHUNK_SIZE` |
| `chunking.chunk-overlap` | `400` (char) | `CHUNKING_CHUNK_OVERLAP` |

Un 10-K tipico (HTML, ~500K caratteri) produce circa 80-100 chunk con questi parametri. Il prototipo Python `agent.py` usa chunking FAISS con parametri simili; il porting Kotlin (TSK-101) mantiene la coerenza metodologica. [^src: raw/agent.py:1353-1372]

## Idempotenza

Il vincolo `UNIQUE (filing_blob_id, chunk_index)` garantisce che riprocessare lo stesso filing non produca righe duplicate. Il service di chunking usa `INSERT ... ON CONFLICT DO NOTHING` (upsert idempotente). [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md §Business Rules]

## Flusso ingest

```
SEC EDGAR download (US-039)
  → filing_blob (HTML raw, tabella US-039)
  → FilingChunkingService (TSK-101)
      → chunking HTML → N chunk di ~6000 char
      → EmbeddingService.embed(batch) via HTTP → embeddings-sidecar (ADR-018)
      → upsert filing_chunks (chunk_index = 0..N-1)
  → indice HNSW aggiornato automaticamente
```

Il sidecar Python FastAPI (`embeddings-sidecar`) espone `POST /embed` e restituisce vettori 1024-dim normalizzati L2. Vedi [[arctic-embed-l-v2]] per la spec del modello, il fix di trasporto del client e il supporto GPU. Vedi [ADR-018](../../design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md) per l'architettura completa del sidecar.

Nota (split EP-011 V028): l'embedding è prodotto e persistito **solo durante l'operazione INGEST** della deep analysis async; l'ANALYSIS deterministica non tocca pgvector e solo il ramo ANALYSIS-con-LLM (Munger inversion) **riusa** gli embedding già indicizzati per il retrieval. Vedi [[analysis-api-pipeline]] §Aggiornamenti (v2026-05-30). [^src: src/backend/src/main/resources/db/migration/V028__deep_analysis_run_kind.sql]

## Query similarity (retrieval RAG)

Esempio query pgvector per top-K chunk dato un vettore di query:

```sql
SELECT id, ticker, filing_type, content,
       1 - (embedding <=> $1::vector) AS cosine_score
FROM filing_chunks
WHERE ticker = $2
ORDER BY embedding <=> $1::vector
LIMIT $3;
```

L'operatore `<=>` è Cosine Distance (pgvector). Il `FilingRagService` (TSK-102) esegue 10 query Munger inversion per ticker, ognuna con top-K = 5 chunk. [^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §Validation]

## Politica costi embedding

L'ingest di tutti i chunk di un singolo 10-K (tipicamente 30-80 chunk) deve completare in < 30 secondi su hardware standard (M-series Mac o x86 con 16 GB RAM). Il batch overnight EP-012 (30 ticker × ~300 chunk) deve completare in < 30 minuti. [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md §Business Rules]

## Concetti correlati

[[arctic-embed-l-v2]]
[[munger-inversion-rag]]
[[analysis-api-pipeline]]
[[value-investor-bot-architecture]]
[[sec-filings-analysis]]

## Pagine collegate

[[fmp-financial-statements-stable]]
[[value-investing-rule-engine]]
[ADR-018 — Embeddings inference architecture](../../design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md)
[ADR-003 — Database PostgreSQL](../../design_&_architecture/decisions/ADR-003-database-postgresql.md)

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-011: US-040 (Vector store pgvector), US-041 (Munger inversion RAG)
