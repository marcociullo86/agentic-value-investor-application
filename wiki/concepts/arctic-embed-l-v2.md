---
type: concept
sources:
  - "management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md"
  - "design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md"
  - "raw/agent.py"
status: review
created: 2026-05-25
tags: [embedding, llm, qwen3, arctic-embed, pgvector, rag, ep-011, a-b-test]
---
# Modello di Embedding — Qwen3-Embedding-0.6B (e A/B test con Arctic Embed L v2.0)

> Spec del modello di embedding scelto per la pipeline RAG EP-011: `Qwen/Qwen3-Embedding-0.6B` (1024 dim Matryoshka, 32K token context, MTEB ~64.6 retrieval), deployato localmente via sidecar Python FastAPI e configurabile via `embeddings.model.name` per A/B test.

## Nota sulla storia della selezione

Il gap `wiki-promote-arctic-embed-spec` è stato aperto il 2026-05-23 con riferimento al modello `Snowflake/snowflake-arctic-embed-l-v2.0` (1024 dim, 8192 ctx, MTEB ~55.6 retrieval, 2.3 GB RAM). Lo stesso giorno, a seguito di analisi comparativa con l'utente, il modello è stato cambiato in **`Qwen/Qwen3-Embedding-0.6B`** (MTEB ~64.6 retrieval, 32K ctx, ~2.5 GB RAM). Il cambio è recepito in [ADR-018](../../design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md) §"Aggiornamento 2026-05-23". Arctic Embed L v2.0 rimane il **fallback documentato** per A/B test. [^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §Aggiornamento 2026-05-23]

## Modello attuale di produzione: `Qwen/Qwen3-Embedding-0.6B`

| Parametro | Valore |
|-----------|--------|
| **HuggingFace ID** | `Qwen/Qwen3-Embedding-0.6B` |
| **Dimensioni output** | 1024 (Matryoshka, configurabile da 64 a 1024) |
| **Context window** | **32K token** |
| **MTEB retrieval score** | ~64.6 |
| **Footprint RAM** | ~2.5 GB (modello in RAM su CPU) |
| **Ecosistema** | `sentence-transformers` + HuggingFace native |
| **Rilascio** | Q4 2025 |
| **Normalizzazione** | L2 (`normalize_embeddings=True`, coerente con Cosine Similarity pgvector) |

### Vantaggi rispetto ad Arctic Embed L v2.0

- **+9 punti MTEB retrieval** (64.6 vs 55.6) a parità sostanziale di footprint RAM (+200 MB).
- **Context 32K** elimina i problemi di frammentazione su 10-K Item lunghi (Item 1A Risk Factors spesso > 8K token; con Arctic 8192-ctx richiedeva chunking aggressivo dispersivo).
- **Drop-in compatibile** con `sentence-transformers` (stesso ecosistema HuggingFace ufficiale; nessuna conversione ONNX necessaria).
- **Output dim 1024 Matryoshka** → schema `filing_chunks.embedding vector(1024)` invariato rispetto ad Arctic. [^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §Aggiornamento 2026-05-23]

## Modello fallback A/B test: `Snowflake/snowflake-arctic-embed-l-v2.0`

| Parametro | Valore |
|-----------|--------|
| **HuggingFace ID** | `Snowflake/snowflake-arctic-embed-l-v2.0` |
| **Dimensioni output** | 1024 |
| **Context window** | 8192 token |
| **MTEB retrieval score** | ~55.6 |
| **Footprint RAM** | ~2.3 GB |
| **Ecosistema** | `sentence-transformers` (drop-in) |

Più maturo del Qwen3 (rilasciato 2024), ma inferiore in retrieval score e context. Rimane disponibile come fallback documentato e non richiede modifiche al codice — solo la property `embeddings.model.name`. [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md §Business Rules]

## Configurazione applicativa

La property `embeddings.model.name` esposta in `application.yaml` consente A/B test senza ricompilazione: [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/US-040.md §Business Rules]

```yaml
embeddings:
  model:
    name: Qwen/Qwen3-Embedding-0.6B   # A/B: Snowflake/snowflake-arctic-embed-l-v2.0 | Qwen/Qwen3-Embedding-4B
  base-url: http://embeddings-sidecar:8000
  dimension: 1024
  batch:
    size: 100
  timeout-seconds: 30
  normalize: true
```

Override via env var `EMBEDDINGS_MODEL_NAME`. Il sidecar legge `MODEL_NAME` al boot e carica il modello corrispondente; il backend Kotlin non dipende dal nome modello (solo da `embeddings.dimension`). [^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §4]

### Tabella modelli disponibili per A/B test

| Property value | MTEB retrieval | Context | RAM | Note |
|---------------|---------------|---------|-----|------|
| `Qwen/Qwen3-Embedding-0.6B` | ~64.6 | 32K | ~2.5 GB | **Produzione** |
| `Snowflake/snowflake-arctic-embed-l-v2.0` | ~55.6 | 8K | ~2.3 GB | Fallback maturo |
| `Qwen/Qwen3-Embedding-4B` | TBD (atteso > 67) | 32K | ~8 GB | Futura A/B qualità massima |
| `BAAI/bge-large-en-v1.5` | ~54 | 512 token | ~1.3 GB | Baseline agent.py v2.6.1 |

## Deployment — sidecar Python FastAPI

Il modello gira nel container `embeddings-sidecar` (Docker Compose, [ADR-018](../../design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md)):

```
POST /embed
  Body: {"texts": ["...", ...], "normalize": true}   # max 100 testi per batch
  Response 200: {"embeddings": [[...1024 floats...]], "model": "...", "dim": 1024, "tokens_used": N}
  Response 400: lista vuota o testi > context window
  Response 503: model not loaded (cold start in corso)

GET /healthz
  Response 200: {"status": "ready", "model": "...", "dim": 1024, "loaded_at": "..."}
```

**Cold start**: primo avvio scarica ~1.3 GB da HuggingFace Hub (~30-60s); restart successivi caricano da volume `embeddings-model-cache` (~3-5s). `start_period: 90s` in Docker Compose healthcheck. [^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §5]

## Integrazione con pgvector e pipeline RAG

Il sidecar produce embedding 1024-dim normalizzati L2, compatibili con l'operatore Cosine Distance `<=>` di pgvector. Lo schema `filing_chunks.embedding vector(1024)` e l'indice HNSW (`m=16`, `ef_construction=64`) sono descritti in [[pgvector-vector-store]].

Il flusso completo:

```
10-K HTML → chunking (6000 char, overlap 400)
  → POST /embed (batch da 100 chunk)
  → embeddings 1024-dim
  → INSERT filing_chunks ... ON CONFLICT DO NOTHING
```

Consumer RAG: `FilingRagService` (TSK-102) esegue 10 query Munger inversion, ognuna embedded online via `POST /embed` (1 testo), retrieval top-5 da pgvector. Vedi [[munger-inversion-rag]]. [^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §Validation]

## Resilience chain (lato Kotlin)

`EmbeddingRestClient` è protetto dalla catena Resilience4j normativa `RateLimiter → CircuitBreaker → Retry → HTTP`: [^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §4]

| Pattern | Config |
|---------|--------|
| Rate Limiter | 100 req/min (protezione thread starvation, nessun limite esterno) |
| Circuit Breaker | failureRate 50%, window 10, waitOpen 30s |
| Retry | max 3 tentativi, backoff `1s → 2s → 4s`, retry su 503/5xx (cold start), mai su 400 |
| Bulkhead | semaphore 8 concurrent calls |
| Timeout HTTP | 30s |

## Riferimento al prototipo agent.py

Il prototipo Python `agent.py` v2.6.1 usa `HuggingFaceEmbeddings(BAAI/bge-large-en-v1.5, normalize=True, device=cpu, batch=100)` con FAISS in-memory. Il porting Kotlin sceglie `Qwen3-Embedding-0.6B` (qualità superiore) con pgvector (persistenza). L'interfaccia `EmbeddingService` Kotlin isola i caller dal modello concreto, analogamente alla strategia LLM in ADR-017. [^src: raw/agent.py:1353-1372]

## Concetti correlati

[[pgvector-vector-store]]
[[munger-inversion-rag]]
[[value-investor-bot-architecture]]
[[analysis-api-pipeline]]

## Pagine collegate

[ADR-018 — Embeddings inference architecture](../../design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md)
[[fmp-financial-statements-stable]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-011: US-040 (vector store + embedding pipeline), US-041 (Munger inversion RAG)
