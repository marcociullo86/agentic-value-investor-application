---
type: concept
sources: ["raw/agent.py", "raw/09_agent_py_method_analysis.md"]
status: draft
created: 2026-05-23
updated: 2026-05-30
tags: [value-investing, munger, inversion, rag, faiss, 10k, 10q, sec-edgar, llm, qualitative-analysis, vi-domain]
domain: value-investing
---
# Munger Inversion RAG — Analisi Qualitativa 10-K/10-Q

> Il nodo `node_leggi_report_10k` di agent.py implementa il principio di inversione di Charlie Munger ("Invert, always invert") attraverso un sistema RAG (Retrieval-Augmented Generation) su testo 10-K + 10-Q scaricato da SEC EDGAR. Dieci query Munger-style identificano proattivamente i rischi catastrofici prima di valutare i pregi del business.

## Contesto

Charlie Munger ha reso celebre l'inversione come strumento di analisi: "Dimmi dove morro' e non ci andero' mai." Applicato all'analisi aziendale: prima cerca cosa puo' distruggere l'investimento, poi valuta i pregi. Questo approccio e' complementare al Capitolo 15 de L'Investitore Intelligente (intraprendente qualitativo), dove Graham descrive l'analisi qualitativa come necessaria per l'investitore che vuole andare oltre i semplici filtri quantitativi. [^src: raw/09_agent_py_method_analysis.md §2.4]

## Flusso del Nodo `node_leggi_report_10k`

```
1. FMP /stable/sec-filings-search/symbol → lista filing (10-K, 10-Q) con finalLink
2. Download HTML del 10-K piu' recente da SEC (via finalLink)
3. Download HTML del 10-Q piu' recente da SEC
4. BSHTMLLoader + RecursiveCharacterTextSplitter → chunks 1500 token
5. FAISS vectorstore (embeddings BAAI/bge-large-en o gemini-embedding-001)
6. 10 query Munger-style → retrieval top-K chunks per ogni query
7. Concatenazione contesto (max 8000 token) → prompt Claude Opus (4.8 nel WebApp; 4.7 nel prototipo agent.py)
8. Risposta strutturata: RISCHIO_ESTREMO + MOTIVAZIONE + RISCHI + FORZE + SEGNALI_10Q
```

[^src: raw/agent.py:1304-1494] [^src: raw/09_agent_py_method_analysis.md §1]

## Le 10 Query Munger-Style

Le query sono formulate per identificare i rischi catastrofici, non per trovare aspetti positivi:

1. **Cause legali esistenziali** — "pending litigation material adverse", "class action settlement"
2. **Debito e sopravvivenza** — "going concern", "debt covenant violation", "liquidity risk"
3. **Obsolescenza tecnologica** — "technology obsolescence", "competitive disruption", "market share loss"
4. **Concentrazione cliente** — "customer concentration", "single customer more than 10% revenue"
5. **Segnali di frode** — "restatement", "material weakness", "internal control deficiency"
6. **Regulatory ban** — "regulatory investigation", "FDA warning letter", "SEC inquiry"
7. **Guidance ridotta** — "lowered guidance", "revenue shortfall", "earnings miss"
8. **Subsequent events negativi** — "subsequent events", "post-balance-sheet events"
9. **Deterioramento margini** — "margin compression", "pricing pressure", "cost inflation"
10. **Dipendenza geografica** — "geographic concentration", "single market risk", "export controls"

[^src: raw/09_agent_py_method_analysis.md §2.4] [^src: raw/agent.py:1304-1494]

## Prompt Template Charlie Munger

```python
# agent.py:1461-1476
sys_p = """Sei Charlie Munger. Applichi il principio di INVERSIONE: prima cerca cosa puo'
distruggere l'investimento. Sei brutalmente onesto. Analizzi DUE fonti: il 10-K annuale
(visione strategica) e l'ultimo 10-Q (segnali emergenti).
Presta particolare attenzione ai segnali del 10-Q: guidance ridotta, ricavi in calo,
subsequent events negativi.

Rispondi SEMPRE con questo formato esatto:

RISCHIO_ESTREMO: [SI/NO]
MOTIVAZIONE: [3-4 frasi dirette, nessuna diplomazia]
RISCHI_IDENTIFICATI:
- [rischio]
PUNTI_DI_FORZA:
- [forza]
SEGNALI_RECENTI_10Q:
- [eventuali segnali emersi nell'ultimo trimestre, oppure "nessun segnale di rilievo"]

RISCHIO_ESTREMO: SI solo se: cause legali esistenziali, debito minaccia la sopravvivenza,
obsolescenza totale entro 5 anni, un cliente >50% ricavi, segnali di frode, oppure
deterioramento severo confermato dal 10-Q."""
```

[^src: raw/agent.py:1461-1476]

## Output e Integrazione nella Cascade

L'output del nodo viene scritto in `state["analisi_qualitativa_testo"]` e `state["rischio_estremo_pdf"]` (booleano). La cascade decisionale `munger_decision` controlla questo flag come **primo veto** (priorita' assoluta):

```python
# agent.py:1911-1913
if state.get("rischio_estremo_pdf"):
    return "bocciato_qualitativo"
```

Un `RISCHIO_ESTREMO: SI` esclude il titolo indipendentemente da qualsiasi metrica quantitativa (ROE eccellente, DCF attraente, panic discount). La qualita' qualitativa veta la quantita'. [^src: raw/agent.py:1901-1913] [^src: raw/09_agent_py_method_analysis.md §5]

## Distinguere 10-K da 10-Q nella Risposta

Il prompt richiede esplicitamente di distinguere i segnali 10-K (annuali, strategici) dai segnali 10-Q (trimestrali, emergenti). Il campo `SEGNALI_RECENTI_10Q` nel formato di risposta serve a catturare il deterioramento recente che potrebbe non essere visibile nel 10-K annuale. [^src: raw/agent.py:1461-1476]

Questo e' particolarmente rilevante per:
- Guidance ridotta (annunciata nel 10-Q ma non nel 10-K precedente)
- Subsequent events (cambiano la situazione dopo il bilancio annuale)
- Variazioni rapide di market share nel trimestre

## Architettura RAG

- **Chunking**: `RecursiveCharacterTextSplitter` con chunk_size=1500, overlap=200.
- **Vectorstore**: FAISS (in-memory, non persistito — ricalcolato per ogni ticker).
- **Embeddings**: `BAAI/bge-large-en-v1.5` in modalita' locale (default, gratis) o `gemini-embedding-001` cloud.
- **Retrieval**: top-K chunks per ogni delle 10 query; contesto aggregato troncato a 8000 token per rispettare il context window del prompt.
- **LLM**: Claude Opus — scelto per il minor tasso di allucinazioni su compiti finanziari vs altri modelli. Il WebApp (EP-011) usa di default `claude-opus-4-8`, **configurabile via env `ANTHROPIC_MODEL`** (single source of truth: i caller costruiscono `LlmRequest` senza forzare un modello, quindi vince sempre il valore di `anthropic.model`). Il prototipo `agent.py` usava `claude-opus-4-7`.

[^src: raw/agent.py:58-65] [^src: raw/09_agent_py_method_analysis.md §1]

## Costo LLM (input per EP-011 budget)

Per analisi 10-K + 10-Q completa (estimato maggio 2026):
- Input: ~8000 token (contesto RAG + prompt)
- Output: ~2000 token (risposta strutturata)
- Costo Claude Opus (tier Opus, stima): ~$0.12 (input) + $0.15 (output) = **~$0.27/ticker**

Con cache 90gg per filing (TSK-095 V011__filing_blob): solo aggiornamenti trimestrali producono refresh → **costo reale ridotto significativamente** rispetto al run daily. [^src: raw/09_agent_py_method_analysis.md §9]

## Spec di Porting per EP-011 (WebApp)

Il porting Kotlin della Deep Analysis (US-041) deve replicare:
1. `SecFilingsAdapter.getLatest10K(ticker)` + `getLatest10Q(ticker)` — via FMP `/stable/sec-filings-search/symbol` + download HTML SEC
2. `FilingChunker.chunk(html)` — RecursiveCharacterTextSplitter equivalente JVM
3. `VectorStore.embed(chunks)` — sidecar Python sentence-transformers o djl-huggingface
4. `MungerInversionService.analyze(chunks, metrics)` — 10 query + Claude Opus (default `claude-opus-4-8`, configurabile via `ANTHROPIC_MODEL`) via `AnthropicClient`
5. Parsing della risposta strutturata → `MungerAnalysisResult`

Vedi gap `tpm-embeddings-sidecar-vs-djl` per la decisione architetturale sull'embedding service.

## Concetti correlati
[[panic-buy-vs-value-trap-detection]]
[[value-investor-bot-architecture]]
[[sec-filings-analysis]]
[[clone-investing-13f-overlay]]
[[graham-modern-bot-methodologies]]

## Pagine collegate
[[warren-buffett]]
[[benjamin-graham]]
[[intelligent-investor]]
[[seven-criteria-defensive-stock-selection]]
- [ADR-017](../../design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md) — Anthropic Claude Opus integration (adapter pattern + Resilience4j chain per US-041; modello configurabile via `ANTHROPIC_MODEL`, default `claude-opus-4-8`)
- [ADR-019](../../design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md) — LLM cost budget R2 + telemetry + kill-switch automatico (containment spesa Claude Opus per US-041/042/047)

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->

- [EP-024](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md) — Tab Riepilogo (riusa pgvector + arctic-embed indicizzando le pagine wiki come secondo corpus oltre ai filing 10-K/10-Q)
- [US-103](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-103-aggregatore-riepilogo-cross-dominio-be/US-103.md) — BE Aggregatore /summary con citazioni RAG cross-dominio (pattern Munger-inversion riusato); Munger `RISCHIO_ESTREMO` overrides anche su VI positivo
