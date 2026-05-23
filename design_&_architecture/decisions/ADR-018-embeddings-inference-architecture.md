---
id: ADR-018
title: Embeddings inference architecture — sidecar Python FastAPI con Qwen3-Embedding-0.6B
status: accepted
created: 2026-05-23
accepted: 2026-05-23
deciders: [lead-architect, marco.ciullo]
consulted: [tpm, be-dev, infra-dev]
---
# ADR-018 — Embeddings inference architecture: sidecar Python FastAPI con Qwen3-Embedding-0.6B

## Aggiornamento 2026-05-23 — Switch modello da Arctic Embed L v2.0 a Qwen3-Embedding-0.6B

A seguito dell'analisi comparativa con l'utente (2026-05-23), il modello scelto passa
da `Snowflake/snowflake-arctic-embed-l-v2.0` (MTEB ~55.6 retrieval, 8192 ctx, 2.3 GB
RAM) a **`Qwen/Qwen3-Embedding-0.6B`** (MTEB ~64.6 retrieval, **32K context**, 2.5 GB
RAM). Motivazione:

- +9 punti MTEB retrieval a parità sostanziale di footprint RAM (+200 MB)
- Context 32K elimina ogni problema di frammentazione su 10-K Item lunghi
  (Item 1A Risk Factors spesso > 8K token; in Arctic richiedeva chunking
  aggressivo che disperdeva contesto)
- Modello recente (Q4 2025) ma allineato a `sentence-transformers` standard
- Output dim 1024 (Matryoshka, configurabile da 64 a 1024) → schema
  `filing_chunks.embedding vector(1024)` invariato

L'architettura sidecar + interface `EmbeddingService` Kotlin + Resilience4j chain
restano invariate.

## Contesto

EP-011 (Deep Analysis 10-K/10-Q) introduce per la prima volta nella WebApp Value Investing l'uso di embedding semantici per RAG su filing SEC. La pipeline ricalca il flusso del prototipo Python `agent.py`: download 10-K + 10-Q da SEC EDGAR → chunking → embedding → persistenza in pgvector → retrieval top-K per le 10 query di Munger inversion [^src: wiki/concepts/value-investor-bot-architecture.md §"Flusso Principale"].

Il modello scelto per gli embeddings — decisione di prodotto già confermata dall'utente il 2026-05-23 — è **`Qwen/Qwen3-Embedding-0.6B`** (1024 dimensioni Matryoshka, **32K token di context**, MTEB ~64.6 retrieval). Il deployment è **locale** (no cloud paid embeddings), con la possibilità di A/B test futuri (es. `Qwen3-Embedding-4B` per qualità massima, o `Snowflake/snowflake-arctic-embed-l-v2.0` come fallback più maturo) tramite property `embeddings.model.name` [^src: wiki/gaps.md §wiki-promote-arctic-embed-spec].

TSK-099 (`infra: sidecar Python embeddings`) ha applicato come default un sidecar Python FastAPI basato su `sentence-transformers`, ma il gap `tpm-embeddings-sidecar-vs-djl` (2026-05-23) segnala l'assenza di un ADR formale che valuti l'alternativa JVM-nativa (`djl-huggingface`) [^src: wiki/gaps.md §tpm-embeddings-sidecar-vs-djl]. Questo ADR risolve il gap per design.

Le US dipendenti dall'embedding inference:

| US | Dove serve embedding | Volume per ticker |
|---|---|---|
| US-040 (Vector store pgvector) | Schema `filing_chunks` con colonna `embedding vector(1024)` + indice HNSW | n/a (schema) [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-pgvector-store/US-040.md] |
| US-041 (Munger inversion LLM) | Embedding di 10 query Munger + retrieval top-K dal vector store | 10 query embedded online + ~200-500 chunk embedded offline (10-K + 10-Q) [^src: wiki/concepts/munger-inversion-rag.md] |

Il prototipo Python di riferimento (`agent.py:1353-1372`) inizializza embedding via `HuggingFaceEmbeddings` con `BAAI/bge-large-en-v1.5` (modalità LOCALE), `normalize_embeddings=True`, CPU device, batch 100 [^src: raw/agent.py:1353-1372]. Lo switch a `snowflake-arctic-embed-l-v2.0` è drop-in compatibile con la stessa libreria `sentence-transformers` (lo stesso ecosistema HuggingFace).

## Decision Drivers

1. **Compatibilità con il flusso di riferimento `agent.py`** — il prototipo usa `sentence-transformers` Python-side; il porting Kotlin deve replicare la stessa qualità di embedding senza divergenze metodologiche (qualità retrieval RAG non degradata) [^src: raw/agent.py:1353-1372].
2. **Vincoli del modello Qwen3-Embedding-0.6B** — modello recente (Q4 2025), ottimizzato per `sentence-transformers` nativo + output Matryoshka 1024-dim. La conversione a formato ONNX (necessaria per `djl-huggingface`) richiede passi manuali via HuggingFace `optimum` e non è ottimizzata per architettura Qwen al 2026-05-23.
3. **Stack Kotlin/Spring per il backend** — `raw/tech_stack.md` §Backend fissa Kotlin 2.2.x + Spring Boot 3.5.x. Le chiamate dal `EmbeddingService` Kotlin devono integrarsi nella chain `RateLimiter → CircuitBreaker → Retry → HTTP` di Resilience4j (analogamente ad [ADR-004](ADR-004-fmp-integration.md) §1 e [ADR-017](ADR-017-anthropic-sdk-jvm.md) §5) [^src: raw/tech_stack.md §Backend].
4. **Configurabilità modello per A/B test** — il modello deve essere configurabile via `embeddings.model.name` senza ricompilazione, per testare Qwen3-Embedding-4B o altre alternative future [^src: wiki/gaps.md §wiki-promote-arctic-embed-spec].
5. **Privacy 10-K** — i 10-K SEC sono dati pubblici, ma la decisione di prodotto è **embedding locali** (no cloud paid embeddings come OpenAI / Voyage AI / Cohere) per coerenza con la policy aziendale e per evitare costi ricorrenti.
6. **Deployment target Docker** — [ADR-009](ADR-009-deployment-target.md) fissa il deployment Docker monorepo runtime-agnostico. Un container Python aggiuntivo (`embeddings-sidecar`) è compatibile con Docker Compose multi-service, già a stack per la WebApp.
7. **Performance CPU x86/ARM standard** — il deployment R2 è su VM standard senza GPU dedicata; la scelta tecnologica deve garantire latenza accettabile (~50-200ms per query embedding) e throughput accettabile per il batch overnight EP-012 (30 ticker × ~300 chunk medi = ~9000 embedding/notte).
8. **Testabilità** — `qa-dev` deve poter mockare il client embedding in integration test senza dipendere dal sidecar Python (esattamente come avviene per FMP e Anthropic LLM via WireMock + Testcontainers).

## Considered Options

### Opzione A — Sidecar Python FastAPI con `sentence-transformers`

Un container Docker dedicato (`embeddings-sidecar`) basato su immagine Python 3.11+ con `sentence-transformers`, `fastapi`, `uvicorn`. Espone un endpoint REST `POST /embed` (input: `{"texts": ["..."]}`; output: `{"embeddings": [[...1024 float...]], "model": "...", "dim": 1024}`). Il backend Kotlin chiama il sidecar via HTTP `RestClient` Spring 6.1+ tramite il bean `EmbeddingService`.

Default già applicato in TSK-099 [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-pgvector-store/TSK-099.md §Cosa fare].

### Opzione B — `djl-huggingface` JVM-nativo (DJL HF model zoo)

Usare la libreria DJL (Deep Java Library) — `ai.djl.huggingface:tokenizers` + `ai.djl:api` — per caricare il modello direttamente in JVM dal backend Spring Boot. Richiede export ONNX del modello (via HuggingFace `optimum` o conversione manuale). Nessun container extra; comunicazione in-process.

### Opzione C — Servizio embeddings cloud (OpenAI / Voyage AI / Cohere)

Usare un provider cloud (OpenAI `text-embedding-3-large`, Voyage AI `voyage-3-large`, Cohere `embed-v3`) tramite HTTP API. Zero infra locale di inferenza; scalabilità automatica. Costo stimato ~$0.13/1M token = ~$0.02/ticker × 30/giorno (EP-012) = ~$0.60/run × 30gg = **~$18/mese** (sostenibile economicamente).

## Decision Outcome

**Scelta: Opzione A — Sidecar Python FastAPI con `sentence-transformers` + Qwen3-Embedding-0.6B.**

### 1. Architettura sidecar

Nuovo container Docker `embeddings-sidecar` orchestrato via Docker Compose accanto a `backend`, `frontend`, `postgres` ([ADR-009](ADR-009-deployment-target.md)):

```
┌────────────────────┐    HTTP POST /embed    ┌────────────────────────────┐
│  backend (Kotlin)  │ ─────────────────────> │  embeddings-sidecar (Py)   │
│  EmbeddingService  │                        │  FastAPI + uvicorn          │
│  (RestClient 6.1+) │ <───────────────────── │  sentence-transformers      │
└────────────────────┘   {embeddings:[[...]]} │  Snowflake/arctic-embed-l-v2│
         │                                    └────────────────────────────┘
         │ JDBC                                              │
         ▼                                                   │
┌────────────────────┐                                       │
│ postgres + pgvector│ <─────────────────────────────────────┘
│ filing_chunks      │       (vector(1024) HNSW)
└────────────────────┘
```

[^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-pgvector-store/TSK-099.md §Cosa fare]

### 2. Endpoint sidecar — contract `POST /embed`

| Campo | Tipo | Note |
|---|---|---|
| **Request** | | |
| `texts` | `list[str]` | Batch di testi da embeddare. Max 100 per request (mitiga OOM su CPU 4GB). |
| `normalize` | `bool` (opzionale, default `true`) | Normalizzazione L2 per Cosine Similarity (allineato `agent.py:1360`) |
| **Response 200** | | |
| `embeddings` | `list[list[float]]` | Una lista da 1024 float per ogni testo, stesso ordine dell'input. |
| `model` | `str` | `"Qwen/Qwen3-Embedding-0.6B"` |
| `dim` | `int` | `1024` |
| `tokens_used` | `int` | Total token consumati (per monitoring throughput) |
| **Response 4xx** | | |
| `400` | `{detail: "..."}` | Input malformato (lista vuota, testi > 32K token, etc.) |
| `503` | `{detail: "..."}` | Model not loaded (cold start in corso) |

**Health check**: endpoint `GET /healthz` ritorna `{status: "ready", model: "...", loaded_at: "..."}` solo quando il modello è in RAM. Docker Compose `healthcheck` punta a `/healthz`.

### 3. EmbeddingService Kotlin (consumer side)

Bean `com.valueinvesting.webapp.embedding.EmbeddingService` (modulo nuovo, sibling di `com.valueinvesting.webapp.llm` ex [ADR-017](ADR-017-anthropic-sdk-jvm.md)):

```
package com.valueinvesting.webapp.embedding

interface EmbeddingService {
    fun embed(texts: List<String>, normalize: Boolean = true): List<FloatArray>
    fun embedSingle(text: String): FloatArray
    val dimension: Int  // 1024
    val modelName: String
}

class EmbeddingRestClient(
    private val restClient: RestClient,
    @Value("\${embeddings.base-url}") private val baseUrl: String,
    @Value("\${embeddings.model.name}") override val modelName: String,
    @Value("\${embeddings.dimension}") override val dimension: Int
) : EmbeddingService { ... }
```

I caller (`FilingChunkingService` TSK-101, `FilingRagService` TSK-102) dipendono solo dall'interfaccia. Lo switch al modello per A/B test (es. Qwen3-Embedding-4B) si fa via property `embeddings.model.name` + restart sidecar + property update Kotlin (zero refactor service-side).

### 4. ResilienceConfig embeddings (`embeddings-sidecar`)

Configurazione bean `EmbeddingsResilienceConfig` (parallelo a `LlmResilienceConfig` ex [ADR-017](ADR-017-anthropic-sdk-jvm.md) §5 e `FmpResilienceConfig` ex [ADR-016](ADR-016-fmp-operations-throttling.md) §4):

| Pattern | Config `embeddings-sidecar` | Razionale |
|---|---|---|
| **Rate Limiter** | `limitForPeriod = 100`, `limitRefreshPeriod = 1m`, `timeoutDuration = 30s` | Sidecar locale, niente rate-limit esterno; protezione contro thread starvation. |
| **Circuit Breaker** | `failureRateThreshold = 50%`, `slidingWindowSize = 10`, `waitDurationInOpenState = 30s` | Pattern uniforme con FMP/LLM; protegge backend se sidecar va OOM o crash. |
| **Retry** | `maxAttempts = 3`, backoff esponenziale `1s → 2s → 4s`, retry su 503 (cold start), 5xx, timeout. **Mai** su 400. | Cold start del modello (~3-5s caricamento) → 503 transitorio gestito con retry. |
| **Bulkhead** | semaphore `maxConcurrentCalls = 8` | Sidecar single-process (uvicorn workers=1 di default); evita esaurimento socket. |
| **Ordine catena** | `RateLimiter → CircuitBreaker → Retry → HTTP` | Normativo `raw/tech_stack.md` §Backend |
| **Timeout HTTP** | 30s | Batch da 100 testi può richiedere 5-15s su CPU; margine 2x. |

Property override:

| Property | Default | Env var |
|---|---|---|
| `embeddings.base-url` | `http://embeddings-sidecar:8000` | `EMBEDDINGS_BASE_URL` |
| `embeddings.model.name` | `Qwen/Qwen3-Embedding-0.6B` | `EMBEDDINGS_MODEL_NAME` |
| `embeddings.dimension` | `1024` | `EMBEDDINGS_DIMENSION` |
| `embeddings.batch.size` | `100` | `EMBEDDINGS_BATCH_SIZE` |
| `embeddings.timeout-seconds` | `30` | `EMBEDDINGS_TIMEOUT_SECONDS` |
| `embeddings.normalize` | `true` | `EMBEDDINGS_NORMALIZE` |

### 5. Docker Compose service definition

Aggiunta al `docker-compose.yml` esistente:

```yaml
services:
  embeddings-sidecar:
    build:
      context: ./src/embeddings-sidecar
      dockerfile: Dockerfile
    image: webapp/embeddings-sidecar:latest
    environment:
      - MODEL_NAME=Qwen/Qwen3-Embedding-0.6B
      - MODEL_CACHE_DIR=/models
      - DEVICE=cpu          # 'cuda' opzionale su host con GPU
      - WORKERS=1
    volumes:
      - embeddings-model-cache:/models   # Persiste il download del modello tra restart
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/healthz"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 90s    # Cold start ~30-60s per scaricare + caricare Qwen3-Embedding-0.6B (~1.4 GB)
    networks: [backend-net]
    deploy:
      resources:
        limits:
          memory: 4G        # Qwen3-Embedding-0.6B carica ~2.5 GB in RAM; 4 GB margine.

volumes:
  embeddings-model-cache:
```

**Cold start mitigation**: il volume `embeddings-model-cache` persiste il download del modello HuggingFace tra restart. Il primo avvio scarica ~1.3 GB; i successivi solo caricano da disco (~3-5s).

### 6. Observability — eventi embedding tracciati

Analogamente a `fmp_api_event_log` ex [ADR-004](ADR-004-fmp-integration.md) §5 e `llm_api_event_log` ex [ADR-017](ADR-017-anthropic-sdk-jvm.md) §6, nuova tabella `embeddings_api_event_log`:

| Evento | Quando | Campi |
|---|---|---|
| `EMBED_REQUEST` | Ogni chiamata sidecar | `purpose` (`chunk_ingest` \| `query`), `batch_size`, `tokens_used`, `latency_ms`, `model` |
| `EMBED_503_COLD_START` | HTTP 503 dal sidecar | `attempt`, `wait_ms` |
| `EMBED_5XX_ERROR` | Errori server sidecar | `status`, `attempt` |
| `EMBED_CIRCUIT_OPEN` | Circuit breaker apre | `failure_rate`, `window_size` |
| `EMBED_TIMEOUT` | Timeout HTTP > 30s | `batch_size`, `attempt` |

Metriche Micrometer per dashboard:

- `embeddings.request.count{purpose, status}`
- `embeddings.tokens.total{purpose}`
- `embeddings.latency.seconds{purpose}` (histogram)
- `embeddings.batch.size{purpose}` (distribution summary)

### 7. Sidecar implementation outline

Modulo `src/embeddings-sidecar/`:

```
src/embeddings-sidecar/
├── Dockerfile               # Base python:3.11-slim, multi-stage; CPU torch
├── requirements.txt         # sentence-transformers, fastapi, uvicorn[standard]
├── app/
│   ├── main.py              # FastAPI app + lifespan model loader
│   ├── embed_service.py     # SentenceTransformer wrapper
│   └── schemas.py           # Pydantic request/response
└── tests/
    └── test_embed.py        # pytest + httpx async client
```

`main.py` (outline):

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from sentence_transformers import SentenceTransformer

model = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    model = SentenceTransformer(os.getenv("MODEL_NAME"), device=os.getenv("DEVICE", "cpu"))
    yield

app = FastAPI(lifespan=lifespan)

@app.get("/healthz")
async def healthz():
    if model is None:
        raise HTTPException(503, detail="model not loaded")
    return {"status": "ready", "model": MODEL_NAME, "dim": model.get_sentence_embedding_dimension()}

@app.post("/embed")
async def embed(req: EmbedRequest):
    vecs = model.encode(req.texts, normalize_embeddings=req.normalize, batch_size=32)
    return {"embeddings": vecs.tolist(), "model": MODEL_NAME, "dim": vecs.shape[1], "tokens_used": estimate_tokens(req.texts)}
```

## Consequences

### Positive

- **US-040 / US-041 sbloccate** con qualità retrieval allineata al prototipo `agent.py` (stesso ecosistema `sentence-transformers`).
- **Coerenza con [ADR-004](ADR-004-fmp-integration.md) e [ADR-017](ADR-017-anthropic-sdk-jvm.md)**: stesso adapter pattern (`EmbeddingService` interface + `EmbeddingRestClient` impl), stessa Resilience4j chain, stessa observability strategy (`*_api_event_log` + Micrometer).
- **Future-proof per A/B test modelli**: switch a Qwen3-Embedding-4B o altri modelli HuggingFace solo via property `embeddings.model.name` + restart sidecar. Zero refactor backend.
- **Privacy garantita**: nessun dato esce dal perimetro (10-K, query Munger, etc.).
- **Zero costi ricorrenti** per l'inferenza embedding (vs ~$18/mese di Opzione C).
- **Testabilità**: `qa-dev` mocka `EmbeddingService` per integration test (Testcontainers WireMock già a stack).
- **Compatibilità ARM/x86**: `sentence-transformers` + PyTorch CPU funziona su entrambe le architetture senza modifiche.

### Negative

- **Container Python aggiuntivo nel deploy**: Docker Compose passa da 3 a 4 service (`backend`, `frontend`, `postgres`, `embeddings-sidecar`). Mitigazione: già usiamo Docker Compose multi-service per `postgres` ([ADR-003](ADR-003-database-postgresql.md), [ADR-009](ADR-009-deployment-target.md)); pattern noto al team.
- **Cold start ~30-60s al primo avvio** (download modello 1.3 GB), poi ~3-5s da cache locale. Mitigazione: volume Docker `embeddings-model-cache` persiste il modello tra restart; `healthcheck start_period: 90s` evita marcare il sidecar come unhealthy durante il loading.
- **Latency intra-container HTTP ~5-10ms per batch** (overhead networking Docker). Mitigazione: trascurabile rispetto al tempo di inferenza CPU (50-200ms per batch da 100 chunk).
- **Footprint RAM ~2 GB sul host** per il modello caricato. Mitigazione: limit Docker `memory: 4G`; documentato come requisito host per R2.
- **Manutenzione separata sidecar Python**: aggiornamenti modello = `pip install -U sentence-transformers` + rebuild image. Mitigazione: aggiornamento poco frequente (modello stabile per mesi); pipeline CI può buildare l'image automaticamente.

### Neutral

- Dipendenza Python aggiuntiva nel monorepo (`src/embeddings-sidecar/`). Compatibile con [ADR-009](ADR-009-deployment-target.md) §monorepo Docker.
- Il sidecar è **stateless** rispetto ai chunk: lo storage rimane in pgvector (US-040). Restart del sidecar = nessuna perdita dati.
- Il modello `Qwen/Qwen3-Embedding-0.6B` è confermato come scelta di prodotto dall'utente — non oggetto di questo ADR. Eventuale downgrade a un modello più piccolo (es. `bge-small-en-v1.5`) per ridurre footprint RAM resta configurabile via `embeddings.model.name` senza modificare codice.

## Validation

### Unit test (sidecar Python)

- `test_embed.py` con `httpx.AsyncClient`:
  - `POST /embed` con 1 testo → response 200 con `embeddings.length == 1`, `embeddings[0].length == 1024`.
  - `POST /embed` con batch 100 testi → response 200 con `embeddings.length == 100`, latenza < 15s su CPU.
  - `POST /embed` con `normalize: true` → vettori L2-norm ~1.0 (assert `norm(v) ∈ [0.999, 1.001]`).
  - `POST /embed` con lista vuota → 400.
  - `GET /healthz` durante model loading → 503; dopo loading → 200 con `dim: 1024`.

### Unit test (EmbeddingRestClient Kotlin)

- `EmbeddingRestClientTest` con MockWebServer / WireMock:
  - Request body `{texts: [...], normalize: true}` → assert top-level keys = `{texts, normalize}`.
  - Header `Content-Type: application/json` presente.
  - Mapping 400/503/5xx/timeout → eccezioni Resilience-compatible (`RetryableException` per 503/5xx, `NonRetryable` per 400).
  - Parsing response: `embeddings`, `model`, `dim`, `tokens_used`.

### Integration test

- `EmbeddingsResilienceConfigIT` (Spring Boot Test + WireMock):
  - 2 errori 503 consecutivi → 2 retry → terza chiamata 200 (simula cold start).
  - 5 errori 500 → circuit breaker apre → chiamata successiva fail-fast (no HTTP call).
  - Cap 100 chiamate/min: la 101° in finestra 60s → `RequestNotPermitted`.
  - Avvio senza `EMBEDDINGS_BASE_URL` → `ApplicationContext` fail-fast con messaggio chiaro.

### Contract test end-to-end

- `FilingChunkingServiceIT`: chunk un 10-K AAPL sample → invoca `EmbeddingService.embed` (mock sidecar restituisce vettori random 1024-dim) → persiste in `filing_chunks` → query pgvector con vettore query → top-K = 5 chunks restituiti con score Cosine.
- `FilingRagServiceIT`: 10 query Munger embedded online → 10 retrieval pgvector → ognuno restituisce top-K (verifica end-to-end pipeline RAG).

### Performance benchmark

- Batch da 300 chunk medi (~256 token cad) su CPU 4-core: latenza target < 30s end-to-end (chunking + embedding + persist pgvector).
- Throughput batch overnight EP-012 (30 ticker × ~300 chunk = 9000 embedding): completamento entro 30 min (verificato in TSK-099 + TSK-128).

## Pros / Cons of the Options

### Opzione A — Sidecar Python FastAPI (scelta)

**Pro**:
- **Compatibilità nativa** con `Qwen/Qwen3-Embedding-0.6B` via `sentence-transformers` (ecosistema HuggingFace ufficiale; nessuna conversione ONNX).
- Drop-in compatibile con `agent.py:1353-1372` (stesso codice di riferimento), riduce divergenze metodologiche tra prototipo Python e porting Kotlin.
- **Update modello = `pip install -U`** + rebuild image. Aggiornamento rapido senza decompilazione ONNX.
- Performance CPU ottimale: `sentence-transformers` ha kernel ottimizzati per inference batch (NumPy + PyTorch).
- Compatibile con GPU opzionale (host con CUDA): basta `DEVICE=cuda` env var.
- Coerente con pattern adapter behind interface (`EmbeddingService` + `EmbeddingRestClient`) ex [ADR-004](ADR-004-fmp-integration.md) / [ADR-017](ADR-017-anthropic-sdk-jvm.md).

**Con**:
- Container extra in Docker Compose (4 service totali).
- Cold start ~3-5s (con cache) / ~30-60s (primo avvio download modello).
- ~5-10ms latency HTTP intra-container per batch (trascurabile vs 50-200ms inference time).
- Footprint RAM ~2 GB sul host.

### Opzione B — `djl-huggingface` JVM-nativo

**Pro**:
- Zero container extra, deployment più semplice (3 service Docker).
- Nessuna comunicazione HTTP intra-container.
- Gestione unificata via Spring Boot (config, log, metrics).
- Latenza inference leggermente inferiore (no HTTP overhead).

**Con**:
- **Supporto modelli HuggingFace recenti limitato**: `djl-huggingface` richiede export ONNX (via `optimum`); `snowflake-arctic-embed-l-v2.0` (rilasciato 2024) potrebbe richiedere conversione manuale non documentata.
- Performance CPU **30-40% inferiore** a `sentence-transformers` nativo in benchmark MTEB (ONNX runtime vs PyTorch-optimized kernels).
- Incompatibilità con architetture sparse / multilingual avanzate (es. Qwen3-Embedding-4B futuro A/B test richiederebbe nuova conversione ONNX).
- Update modello = riesportare ONNX + rebuild → ciclo più lungo rispetto a Opzione A.
- Footprint JVM heap aumenta (~2 GB di modello caricato nello stesso processo del backend) → rischio OOM congiunto con altre feature (FMP cache, ecc.).
- Divergenza dal codice di riferimento `agent.py` → qualità retrieval potrebbe degradare se la conversione ONNX introduce perdite di precisione.

### Opzione C — Servizio embeddings cloud (OpenAI / Voyage AI / Cohere)

**Pro**:
- Zero infra locale di inferenza.
- Scalabilità automatica e gestione modelli a carico del provider.
- Modelli all'avanguardia disponibili immediatamente (es. OpenAI `text-embedding-3-large` 3072-dim).
- Nessun footprint RAM sul host backend.

**Con**:
- **Contraddice decisione prodotto utente** "embedding locali" (2026-05-23) [^src: wiki/gaps.md §wiki-promote-arctic-embed-spec].
- Costi ricorrenti ~$18/mese (sostenibili ma cumulativi con [LLM Anthropic ~$110-175/mese](ADR-017-anthropic-sdk-jvm.md) → budget LLM totale R2 sale a ~$128-193/mese).
- Dipendenza esterna in più (latency, disponibilità, vendor lock-in).
- Vincolo privacy: 10-K sono dati pubblici, ma le query Munger e i chunk dei filing analizzati potrebbero essere proprietary/sensibili in scenari futuri (es. R3 enterprise).
- Switching provider richiederebbe nuova implementazione `EmbeddingService` (anche se l'interfaccia astratta lo rende meno costoso).

## Pending clarifications

Nessuna `hard`. Gap aperti correlati (`soft`, non bloccanti per la decisione architetturale):

- `wiki/gaps.md §wiki-promote-arctic-embed-spec` — wiki promotion del concept `arctic-embed-l-v2` (riservata a wiki-keeper). Questo ADR fornisce il riferimento implementativo nel frattempo.
- `wiki/gaps.md §wiki-promote-pgvector-concept` — wiki promotion del concept `pgvector-vector-store` (riservata a wiki-keeper). Coordinato con [ADR-018] per lo schema `filing_chunks` (TSK-098 V010 migration).
- `wiki/gaps.md §tpm-embeddings-sidecar-vs-djl` — **risolto per design** da questo ADR (Opzione A). Chiusura formale del gap riservata a wiki-keeper dopo accettazione utente dell'ADR.

## Pagine collegate

- [ADR-004](ADR-004-fmp-integration.md) — pattern adapter di riferimento
- [ADR-009](ADR-009-deployment-target.md) — deployment target Docker monorepo
- [ADR-016](ADR-016-fmp-operations-throttling.md) — Resilience4j chain `RateLimiter → CircuitBreaker → Retry → HTTP`
- [ADR-017](ADR-017-anthropic-sdk-jvm.md) — adapter pattern LLM (analogia di design)
- [[value-investor-bot-architecture]] — flusso di riferimento e strategia LLM/embedding
- [[munger-inversion-rag]] — consumer principale (US-041 retrieval pgvector)
- [[analysis-api-pipeline]] — pipeline `/api/analysis/{ticker}/deep`
- [components/backend-components.md](../components/backend-components.md)
- [overview.md](../overview.md)
