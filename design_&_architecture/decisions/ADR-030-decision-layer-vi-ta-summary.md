---
id: ADR-030
title: Decision layer VI+TA — persistenza Technical Analysis, corpus RAG wiki cross-dominio, soglia VI aggregata, confine LLM/deterministico
status: proposed
created: 2026-06-08
deciders: [lead-architect]
supersedes: []
relates_to: [ADR-028, ADR-029, EP-011, EP-013, EP-020]
---
# ADR-030 — Decision layer VI+TA (EP-024)

> **Stato `proposed`**: questo ADR formalizza le 4 decisioni architetturali lasciate aperte da EP-024 §"Note di scope e architettura" e dalle US-098/103. Va portato a `accepted` dal lead-architect prima del kickoff di Sprint 21 (analogo al flow ADR-027/028/029 → Sprint 20). Le decisioni sotto seguono le preferenze già espresse nelle spec delle US; sono marcate dove introducono una scelta non vincolata dalla spec.

## Contesto

EP-024 estende la pagina dettaglio ticker da 2 a 4 tab, aggiungendo **Technical Analysis** (layer advisory di timing) e **Riepilogo** (verdetto aggregato VI+TA azionabile, gate VI primario). Le precondizioni infra sono `done`: FMP `/stable` (EP-002), `FmpAdapter.getTechnicalIndicator` (EP-013), pipeline Deep Analysis + pgvector + sidecar arctic-embed-l-v2 (EP-011), trasparenza/budget LLM (EP-020), RuleSignal typed (EP-021), NCAV Net-Net (EP-023).

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

Il payload TA è una **proiezione deterministica** di dati FMP già soggetti a cache-aside (`fmp_cache`, TTL 24h, EP-002/EP-013): SMA/RSI/MACD/ATR/OBV via `/stable/technical-indicators/*`, historical EOD via `/stable/historical-price-full`. Trend classification, livelli support/resistance, entry-timing, stop e sizing sono **funzioni pure** di questi input. Non esiste stato proprio da persistere.

→ **Niente tabella `technical_analysis_snapshot`.** Il calcolo TA avviene on-demand sopra la cache FMP esistente. Riduce la superficie DB e le migrazioni Flyway. Coerente con la preferenza di US-098 §Endpoint.

**Eccezione futura (non in questa epica):** se emergerà l'esigenza di audit storico dei verdetti TA o di backtesting (vedi EP-FUTURE-backtest-decision-layer), si introdurrà la persistenza allora, con un ADR dedicato.

### 2. Corpus RAG wiki cross-dominio → secondo corpus nel pgvector esistente, `corpus_kind` discriminante

Le citazioni del Riepilogo (US-103) riusano il vector store pgvector di EP-011 (sidecar arctic-embed-l-v2). Le pagine wiki sono indicizzate come **secondo corpus** accanto ai chunk dei filing 10-K/10-Q:

- Riuso della stessa tabella `filing_chunks` **rinominata concettualmente a corpus generico** tramite nuova colonna discriminante `corpus_kind ∈ {FILING, WIKI}` (migration Flyway additiva, default `FILING` per i record esistenti). **(scelta non vincolata dalla spec)** — l'alternativa (tabella `wiki_chunks` separata) duplicherebbe l'infrastruttura di embedding/HNSW; il discriminante su singola tabella mantiene un solo indice HNSW e una sola pipeline di similarity search.
- Scope indicizzazione: tutte le pagine in `wiki/concepts/` e `wiki/syntheses/` con frontmatter `domain ∈ {value-investing, technical-analysis-trading}`.
- Reindicizzazione: job/endpoint amministrativo **triggered**, non al boot dell'app (evita costi embedding a ogni avvio). Idempotente per `(corpus_kind, source_id, chunk_index)`.
- Similarity search del Summary: filtrata su `corpus_kind = WIKI` + dominio coerente con il rationale; top-K=4 (configurabile).

### 3. Soglia "VI verdict aggregato" → proporzionale sui ruleId DECISIONALI disponibili

US-103 usava "≥ 8/13 GREEN" cablato. Con EP-023 i ruleId sono 15, ma `NCAV_LATEST` è **informativo** (mai decisionale; vedi ADR-029 §2). Per non riscrivere la soglia a ogni nuova regola:

- Si conta sui **ruleId decisionali disponibili** (esclusi `NCAV_LATEST` informativo ed esclusi i ruleId `INDETERMINATE`/`NOT_CALCULABLE`).
- `GREEN_DOMINANT` = quota GREEN **≥ 60%** dei decisionali disponibili.
- `RED_DOMINANT` = quota GREEN **< 33%**.
- `YELLOW_DOMINANT` = intervallo intermedio.
- `INDETERMINATE_DOMINANT` = ≥ 1/3 dei ruleId sono INDETERMINATE/NOT_CALCULABLE → il Summary degrada a `INSUFFICIENT_DATA`.

Con 14 decisionali (13 storici + `NET_NET_RATIO`): GREEN_DOMINANT ≥ 9 GREEN, RED_DOMINANT < 5 GREEN. La proporzione (~0.60 / 0.33) replica l'intento originale di "8/13" ed è stabile rispetto a future regole. **(scelta non vincolata dalla spec)**

### 4. Forma endpoint → advisor inclusi in `TechnicalAnalysisResponse`, Summary separato

- `GET /api/analysis/{ticker}/technical` ritorna **un'unica** `TechnicalAnalysisResponse` che include i 6 blocchi indicatori (US-098) **più** `entryTimingAdvisor` (US-099) e `stopSuggestion` + `positionSizing` + `rewardRiskRatio` (US-100). Un solo round-trip per il tab Technical Analysis. Coerente con la preferenza espressa in US-099 §Output e US-100.
- `equity` per il position sizing è **query param** (`?equity=...`, default 50000), **mai persistito** server-side.
- `GET /api/analysis/{ticker}/summary` resta endpoint separato: orchestra `/technical` + VI + Deep, è per-user cache-aware e fa l'unica call LLM.

### 5. Confine LLM ↔ deterministico (ribadito come invariante architetturale)

- **Tutti i verdetti tipati** (`TrendClassification`, `EntryTimingVerdict`, `StopType`, `SummaryVerdict`, gate VI) sono prodotti da **funzioni pure Kotlin** con tabelle di mapping dichiarative (data class + match function, no if/else annidati > 1 livello).
- L'LLM (Claude Opus via `AnthropicClient`, EP-011) è usato **solo** per generare il testo narrativo dei `rationale.*Summary` del Riepilogo: **1 sola call** per Summary (vs 11 di Munger), con logging gated (EP-020 US-088) e `LlmBudgetGuard`/`LlmCostCounterService` (EP-011 TSK-156).
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

**Negative / rischi**
- La migration `corpus_kind` tocca la tabella chunk esistente di EP-011: richiede backfill `FILING` e regression test sulla similarity search dei filing (mitigazione: default colonna + test di non-regressione in TSK dedicato).
- Il calcolo TA on-demand sopra la cache FMP può avere latenza al primo hit dopo scadenza TTL (mitigazione: lazy load del tab, skeleton FE).
- Il corpus wiki va ri-indicizzato manualmente dopo modifiche sostanziali alle pagine (mitigazione: endpoint admin idempotente; fuori scope l'auto-reindex su `/sync-docs`).

## Fonti

[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md §"Dipendenze", §"Note di scope e architettura"]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/US-098.md]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-099-entry-timing-advisor-be/US-099.md]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-100-stop-placement-position-sizing-be/US-100.md]
[^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-103-aggregatore-riepilogo-cross-dominio-be/US-103.md]
[^src: design_&_architecture/decisions/ADR-028-rulesignal-typed-oneof-discriminator.md]
[^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §2]
[^src: wiki/concepts/pgvector-vector-store.md]
[^src: wiki/concepts/arctic-embed-l-v2.md]
[^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
