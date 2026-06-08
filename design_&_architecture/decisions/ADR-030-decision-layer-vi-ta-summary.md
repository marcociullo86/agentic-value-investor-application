---
id: ADR-030
title: Decision layer VI+TA — persistenza Technical Analysis, corpus RAG wiki cross-dominio, soglia VI aggregata, confine LLM/deterministico
status: accepted
created: 2026-06-08
deciders: [lead-architect]
supersedes: []
relates_to: [ADR-018, ADR-028, ADR-029, EP-011, EP-013, EP-020]
---
# ADR-030 — Decision layer VI+TA (EP-024)

> **Stato `accepted` (2026-06-08, lead-architect)**: questo ADR formalizza le 4 decisioni architetturali lasciate aperte da EP-024 §"Note di scope e architettura" e dalle US-098/103, sbloccando il kickoff di Sprint 21 (analogo al flow ADR-027/028/029 → Sprint 20). Le decisioni sotto seguono le preferenze già espresse nelle spec delle US; sono marcate dove introducono una scelta non vincolata dalla spec.
>
> **Verifica fattuale pre-accept (lead-architect)**: i nomi di tabelle/componenti/migration citati sono stati riconciliati con il codebase reale (`src/backend`) e con ADR-018. Correzioni applicate: (a) la migration pgvector reale è **`V014__pgvector_enable_filing_chunks.sql`** — il titolo di TSK-098 ("V012") è stale (V012 è in realtà `fmp_cache_add_sec_filings_endpoint`); (b) il modello di embedding effettivo è **`Qwen3-Embedding-0.6B`** (1024-dim, property `embeddings.model.name`), con `snowflake-arctic-embed-l-v2.0` come fallback documentato (ADR-018); il concept wiki `arctic-embed-l-v2` resta il riferimento storico; (c) lo schema reale di `filing_chunks` **non ha** una colonna `source_id` e vincola l'unicità su `UNIQUE (filing_blob_id, chunk_index)` con `filing_blob_id NOT NULL` — vedi §2 per l'impatto sulla migration del secondo corpus. Componenti `AnthropicClient`, `LlmBudgetGuard`, `LlmCostCounterService`, `FmpAdapter.getTechnicalIndicator` verificati esistenti in `src/backend`. Cache-aside FMP TTL 24h fisso confermata (`FmpCacheService.getOrFetch`, ADR-004) e applicabile ai nuovi endpoint technical-indicators (migration `V024`).

## Contesto

EP-024 estende la pagina dettaglio ticker da 2 a 4 tab, aggiungendo **Technical Analysis** (layer advisory di timing) e **Riepilogo** (verdetto aggregato VI+TA azionabile, gate VI primario). Le precondizioni infra sono `done`: FMP `/stable` (EP-002), `FmpAdapter.getTechnicalIndicator` (EP-013), pipeline Deep Analysis + pgvector (`filing_chunks`, migration `V014`) + sidecar embeddings Qwen3-Embedding-0.6B/1024-dim (EP-011, ADR-018), trasparenza/budget LLM `AnthropicClient`/`LlmBudgetGuard`/`LlmCostCounterService` (EP-011/EP-020), RuleSignal typed (EP-021), NCAV Net-Net (EP-023).

Quattro decisioni erano delegate al lead-architect:
1. Persistenza del payload TA (tabella dedicata vs cache-aside).
2. Indicizzazione delle pagine wiki come secondo corpus RAG per le citazioni cross-dominio del Riepilogo.
3. Ricalibrazione della soglia "VI verdict aggregato" ora che EP-023 ha portato i ruleId da 13 a 15.
4. Forma degli endpoint (advisor inclusi nella response `/technical` vs endpoint separati).

[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md §"Note di scope e architettura"]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/US-098.md §"Endpoint"]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-103-aggregatore-riepilogo-cross-dominio-be/US-103.md §"Citazioni RAG cross-dominio"]

## Decisione

### 1. Persistenza Technical Analysis → cache-aside 24h, NESSUNA tabella dedicata

Il payload TA è una **proiezione deterministica** di dati FMP già soggetti a cache-aside (`FmpCacheService.getOrFetch` → tabella `fmp_financial_snapshot`, TTL 24h fisso per ADR-004; gli endpoint technical-indicators sono registrati in cache dalla migration `V024__fmp_cache_add_technical_indicators_endpoints.sql`): SMA/RSI/MACD/ATR/OBV via `/stable/technical-indicators/*`, historical EOD via `/stable/historical-price-full`. Trend classification, livelli support/resistance, entry-timing, stop e sizing sono **funzioni pure** di questi input. Non esiste stato proprio da persistere.

→ **Niente tabella `technical_analysis_snapshot`.** Il calcolo TA avviene on-demand sopra la cache FMP esistente. Riduce la superficie DB e le migrazioni Flyway. Coerente con la preferenza di US-098 §Endpoint.

**Eccezione futura (non in questa epica):** se emergerà l'esigenza di audit storico dei verdetti TA o di backtesting (vedi EP-FUTURE-backtest-decision-layer), si introdurrà la persistenza allora, con un ADR dedicato.

### 2. Corpus RAG wiki cross-dominio → secondo corpus nel pgvector esistente, `corpus_kind` discriminante

Le citazioni del Riepilogo (US-103) riusano il vector store pgvector di EP-011 — tabella reale **`filing_chunks`** (`embedding vector(1024)`, indice HNSW `idx_filing_chunks_hnsw` su `vector_cosine_ops`, migration `V014__pgvector_enable_filing_chunks.sql`), sidecar embeddings Qwen3-Embedding-0.6B (ADR-018). Le pagine wiki sono indicizzate come **secondo corpus** accanto ai chunk dei filing 10-K/10-Q:

- Riuso della stessa tabella `filing_chunks` **generalizzata a corpus multi-tipo** tramite nuova colonna discriminante `corpus_kind ∈ {FILING, WIKI}` (migration Flyway **additiva**, prossima versione libera **`V033`**, default `FILING` per i record esistenti — backfill banale dal default). **(scelta non vincolata dalla spec)** — l'alternativa (tabella `wiki_chunks` separata) duplicherebbe l'infrastruttura di embedding/HNSW; il discriminante su singola tabella mantiene un solo indice HNSW e una sola pipeline di similarity search.
- **Vincolo schema reale da rispettare nella migration** (verificato su `V014`): lo schema esistente ha `filing_blob_id BIGINT NOT NULL REFERENCES filing_blob(id) ON DELETE CASCADE` e unicità `uq_filing_chunks_blob_chunk UNIQUE (filing_blob_id, chunk_index)`. **Non esiste** colonna `source_id`. Per ospitare i chunk WIKI (che non hanno un `filing_blob`) la migration `V033` deve:
  1. aggiungere `corpus_kind VARCHAR(10) NOT NULL DEFAULT 'FILING'`;
  2. rendere `filing_blob_id` **nullable** (i record WIKI hanno `filing_blob_id IS NULL`) — il `REFERENCES ... ON DELETE CASCADE` resta valido su valori NULL;
  3. aggiungere una colonna identificativa della sorgente wiki `wiki_source_id VARCHAR(255) NULL` (es. path/slug della pagina, p.es. `ta-vs-vi-decision-layer`) + `wiki_domain VARCHAR(40) NULL`;
  4. sostituire/affiancare il vincolo di unicità con uno **partial unique index** per corpus: `UNIQUE (filing_blob_id, chunk_index)` resta per i FILING; per i WIKI aggiungere `CREATE UNIQUE INDEX uq_wiki_chunks ON filing_chunks (wiki_source_id, chunk_index) WHERE corpus_kind = 'WIKI'`. Idempotenza WIKI = `(corpus_kind=WIKI, wiki_source_id, chunk_index)`.
  > La definizione DDL puntuale è demandata al db-dev nel TSK di migration; qui si fissano nomi reali e invarianti. NON usare la chiave `(corpus_kind, source_id, chunk_index)` citata in bozza: la colonna `source_id` non esiste.
- Scope indicizzazione: tutte le pagine in `wiki/concepts/` e `wiki/syntheses/` con frontmatter `domain ∈ {value-investing, technical-analysis-trading}`.
- Reindicizzazione: job/endpoint amministrativo **triggered**, non al boot dell'app (evita costi embedding a ogni avvio). Idempotente per `(corpus_kind=WIKI, wiki_source_id, chunk_index)`.
- Similarity search del Summary: filtrata su `corpus_kind = WIKI` + dominio coerente con il rationale; top-K=4 (configurabile). L'indice HNSW unico copre entrambi i corpus; il filtro `corpus_kind` è applicato come predicato nella query (riuso pattern retrieval Munger EP-011).

### 3. Soglia "VI verdict aggregato" → proporzionale sui ruleId DECISIONALI disponibili

US-103 usava "≥ 8/13 GREEN" cablato. Con EP-023 i ruleId sono 15, ma `NCAV_LATEST` è **informativo** (mai decisionale; vedi ADR-029 §2). Per non riscrivere la soglia a ogni nuova regola:

- Si conta sui **ruleId decisionali disponibili** (esclusi `NCAV_LATEST` informativo ed esclusi i ruleId `INDETERMINATE`/`NOT_CALCULABLE`).
- `GREEN_DOMINANT` = quota GREEN **≥ 60%** dei decisionali disponibili.
- `RED_DOMINANT` = quota GREEN **< 33%**.
- `YELLOW_DOMINANT` = intervallo intermedio.
- `INDETERMINATE_DOMINANT` = ≥ 1/3 dei ruleId sono INDETERMINATE/NOT_CALCULABLE → il Summary degrada a `INSUFFICIENT_DATA`.

Con 14 decisionali (13 storici + `NET_NET_RATIO`; `NCAV_LATEST` escluso come informativo, confermato da ADR-029 §2 e §64) la proporzione si traduce in: GREEN_DOMINANT ≥ 9 GREEN, RED_DOMINANT < 5 GREEN. La proporzione (~0.60 / 0.33) replica l'intento originale di "8/13" ed è stabile rispetto a future regole. **(scelta non vincolata dalla spec)**

> **Riconciliazione con US-103 (autorità architetturale).** Il corpo di US-103 §"Tabella di mapping" e §"Allineamento epic in flight" usa ancora le soglie cablate "≥ 8/13 GREEN" / "4-7 GREEN" / "< 4 GREEN" e annota esplicitamente che vanno "ricalibrate in handoff — decisione da documentare in ADR". **Questo ADR è quella decisione: si adotta la regola proporzionale, NON le soglie cablate.** La US-103 va riallineata in fase di refinement/TSK: (a) sostituire i conteggi assoluti con le quote proporzionali sui ruleId **decisionali disponibili** (esclusi `NCAV_LATEST` e i `INDETERMINATE`/`NOT_CALCULABLE`); (b) la soglia `INDETERMINATE_DOMINANT` passa da "≥ 4 segnali" a "≥ 1/3 dei ruleId" coerentemente con §3. Le AC funzionali di US-103 (tutte le righe della tabella coperte da test, gate VI hardcoded, anti-COPART, invarianza LLM) restano valide: cambia solo la **definizione numerica** delle classi `*_DOMINANT`, non la struttura del gate. Nota di handoff al TPM/PM in §Conseguenze.

### 4. Forma endpoint → advisor inclusi in `TechnicalAnalysisResponse`, Summary separato

- `GET /api/analysis/{ticker}/technical` ritorna **un'unica** `TechnicalAnalysisResponse` che include i 6 blocchi indicatori (US-098) **più** `entryTimingAdvisor` (US-099) e `stopSuggestion` + `positionSizing` + `rewardRiskRatio` (US-100). Un solo round-trip per il tab Technical Analysis. Coerente con la preferenza espressa in US-099 §Output e US-100.
- `equity` per il position sizing è **query param** (`?equity=...`, default 50000), **mai persistito** server-side.
- `GET /api/analysis/{ticker}/summary` resta endpoint separato: orchestra `/technical` + VI + Deep, è per-user cache-aware e fa l'unica call LLM.

### 5. Confine LLM ↔ deterministico (ribadito come invariante architetturale)

- **Tutti i verdetti tipati** (`TrendClassification`, `EntryTimingVerdict`, `StopType`, `SummaryVerdict`, gate VI) sono prodotti da **funzioni pure Kotlin** con tabelle di mapping dichiarative (data class + match function, no if/else annidati > 1 livello).
- L'LLM (Claude Opus via `AnthropicClient`, EP-011 — bean verificato in `src/backend/.../llm/AnthropicClient.kt`) è usato **solo** per generare il testo narrativo dei `rationale.*Summary` del Riepilogo: **1 sola call** per Summary (vs 11 di Munger), con logging gated (EP-020 US-088) e `LlmBudgetGuard`/`LlmCostCounterService` (EP-011 TSK-156 — entrambi verificati in `src/backend/.../llm/`).
- Test di invarianza obbligatorio (US-103 AC): variare solo il prompt mantenendo gli input strutturati non deve cambiare `summaryVerdict`.

### 6. Allineamento RuleSignal typed (ADR-028)

EP-021 è `done`: i sub-segnali TA che producono verdetto (trend, RSI zone, entry-timing) nascono **typed-native** come sotto-tipi della sealed interface RuleSignal dove rientrano nel Rule Engine. I verdetti TA che NON sono ValuationRule (entry-timing, stop, sizing) restano DTO dedicati del dominio TA, non sealed RuleSignal.

## Conseguenze

**Positive**
- Nessuna nuova tabella per la TA → zero migration per il payload tecnico, superficie DB invariata.
- Un solo vector store, un solo indice HNSW, una sola pipeline embedding per due corpus.
- Soglia VI robusta a future regole (proporzionale, non cablata).
- Singolo round-trip per il tab Technical Analysis.
- Confine LLM/deterministico esplicito e testabile → niente "scatola nera" sul verdetto.

**Handoff TPM/PM (azione richiesta)**
- **US-103 va riallineata** prima dell'implementazione: sostituire le soglie cablate "8/13 / 4-7 / <4 GREEN" e "≥4 INDETERMINATE" con le quote proporzionali di §3 (≥60% / <33% sui decisionali disponibili; ≥1/3 INDETERMINATE → `INSUFFICIENT_DATA`). È un riallineamento di spec, non un blocco: il PM aggiorna il corpo US, il TPM ne tiene conto nei TSK derivati. Le AC strutturali di US-103 restano invariate.
- **TSK migration `V033`** (db-dev): vedi §2 per gli invarianti di schema reali (colonna `corpus_kind`, `filing_blob_id` nullable, `wiki_source_id`/`wiki_domain`, partial unique index WIKI). Non esiste `source_id`.

**Negative / rischi**
- La migration `V033` (colonna `corpus_kind` + nullable `filing_blob_id` + colonne wiki) tocca la tabella `filing_chunks` esistente di EP-011: richiede backfill `FILING` (banale, da default) e regression test sulla similarity search dei filing 10-K/10-Q (mitigazione: default colonna + test di non-regressione in TSK dedicato; HNSW invariato, filtro `corpus_kind` come predicato).
- Il calcolo TA on-demand sopra la cache FMP può avere latenza al primo hit dopo scadenza TTL (mitigazione: lazy load del tab, skeleton FE).
- Il corpus wiki va ri-indicizzato manualmente dopo modifiche sostanziali alle pagine (mitigazione: endpoint admin idempotente; fuori scope l'auto-reindex su `/sync-docs`).

## Fonti

[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md §"Dipendenze", §"Note di scope e architettura"]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/US-098.md]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-099-entry-timing-advisor-be/US-099.md]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-100-stop-placement-position-sizing-be/US-100.md]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-103-aggregatore-riepilogo-cross-dominio-be/US-103.md]
[^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md §schema filing_chunks / modello Qwen3]
[^src: design_&_architecture/decisions/ADR-028-rulesignal-typed-oneof-discriminator.md]
[^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §2 §64 — NCAV_LATEST informativo / NET_NET_RATIO decisionale]
[^src: src/backend/src/main/resources/db/migration/V014__pgvector_enable_filing_chunks.sql — schema reale filing_chunks]
[^src: src/backend/src/main/resources/application.yml §embeddings.model.name — Qwen3-Embedding-0.6B]
[^src: wiki/concepts/pgvector-vector-store.md]
[^src: wiki/concepts/arctic-embed-l-v2.md (modello fallback documentato)]
[^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
