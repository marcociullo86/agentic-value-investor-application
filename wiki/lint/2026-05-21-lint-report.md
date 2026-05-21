---
type: lint
date: 2026-05-21
heal_eligible_count: 0
---
# Lint Report — 2026-05-21

## Riepilogo

| Check | Errors | Warnings |
|---|---|---|
| 1 — Orphan + wikilink | 0 | 0 |
| 2 — Claim senza fonte | 0 | 0 |
| 3 — Integrità kanban | 0 | 0 |
| 4 — Coerenza wiki↔kanban | 0 | 0 |
| 4b — Coerenza Q↔kanban (v2.6) | 0 | 0 |
| 4c — Coerenza topology (v2.7) | 0 | 0 |
| 4d — Coerenza VCS (v2.8) | 0 | 0 |

| Level | Count (re-check finale) |
|---|---|
| ERROR | 0 |
| WARNING | 0 |
| INFO | 4 |

**Verdict:** green / pass

---

## ERROR meccanici (heal-eligible)

Nessuno. Tutti gli ERROR richiedono decisione umana (gap-slug vs wikilink, prefisso `runbook:` invalido, routing `infra` senza agente).

---

## Dettaglio

### Check 1 — Orphan + wikilink

- 47 pagine wiki linkate in `wiki/index.md`; **0 orphan**.
- **7 broken-link** rilevati:

| Severità | File | Dettaglio |
|---|---|---|
| ERROR | `wiki/log.md` (×5) | Wikilink a gap-slug chiusi (`[[vi-webapp-owner-earnings-formula]]`, `[[vi-webapp-spa-framework-decision]]` ×3, `[[vi-webapp-screener-criteria]]`) — non sono slug wiki; sono ID gap in `wiki/gaps.md`. Fix suggerito: testo plain o riferimento al gap senza wikilink. |
| ERROR | `wiki/concepts/sec-filings-analysis.md` | `[[runbook: sec-10k-10q-analysis-playbook]]` — prefisso `runbook:` invalido; target corretto: `[[sec-10k-10q-analysis-playbook]]` (fuzzy 0.87, sotto soglia heal 0.90). |
| ERROR | `wiki/sources/vi-05-analisi-10k-10q-buffett.md` | Stesso anti-pattern `[[runbook: sec-10k-10q-analysis-playbook]]`. |

---

### Check 2 — Claim senza fonte

Campione su 47 pagine wiki. **9 WARNING unsourced-claim** (claim ≥20 parole o fatti verificabili senza `[^src:]` / `[[wikilink]]` entro 3 righe):

1. `wiki/concepts/openapi-contract-check.md` ~L28 — regola "No drift in uscita"
2. `wiki/concepts/analysis-api-pipeline.md` ~L19 — consumer frontend US-014
3. `wiki/concepts/sec-filings-analysis.md` ~L34 — Step 2 Item 1A
4. `wiki/concepts/sec-filings-analysis.md` ~L40 — Step 5 Note al Bilancio
5. `wiki/sources/vi-07-risoluzione-q002-q003.md` ~L26 — manutenibilità React
6. `wiki/sources/vi-07-risoluzione-q002-q003.md` ~L27 — community React/Meta
7. `wiki/sources/vi-06-webapp-value-investing-fsd.md` ~L69 — proiezione DCF
8. `wiki/syntheses/fmp-api-overview.md` ~L3 — frontmatter `sources:` (falso positivo parziale)
9. `wiki/entities/fmp-api.md` ~L3 — frontmatter `sources:` (falso positivo parziale)

---

### Check 3 — Integrità kanban (v2.7)

- **6 EP**, **17 US**, **38 TSK** — frontmatter completo su tutti i campi obbligatori.
- Nessun campo legacy `team:`.
- ID TSK univoci globalmente.
- Tutti i `wiki_page` US risolvono a file esistenti.

---

### Check 4 — Coerenza wiki ↔ kanban

- 17 US con `wiki_page` valido.
- Sezioni `## Storie collegate` in 47 pagine wiki: tutti i riferimenti `US-NNN` risolvono.

---

### Check 4b — Coerenza Q ↔ kanban (v2.6)

- `management/questions.md` `[APERTE]` = ∅.
- Q_001, Q_002, Q_003 in `[RISOLTE]` con `**Bloccante:**` presente.
- **WARNING stale-blocked-by:** `US-013` ha ancora `pending_clarification: [Q_001]` nonostante Q_001 risolta (2026-05-20). Nessun marker `reconcile-needed` in `wiki/log.md` per US-013.
- **WARNING orphan-pending-clarification:** `US-013` / Q_001 non compare in `pending_clarification:` di alcun ADR.

---

### Check 4c — Coerenza topology (v2.7)

`factory.config.yaml`: `topology: full-stack-agents`, 4 dev-agent in `.claude/agents/` (be, fe, db, qa).

| Severità | Dettaglio |
|---|---|
| ERROR | `routing.infra: agent` ma `.claude/agents/infra-dev.md` assente → **topology-routing-mismatch** |
| ERROR | `TSK-031` — `consumer: agent`, `layer: infra` → **tsk-consumer-mismatch** |
| ERROR | `TSK-032` — `consumer: agent`, `layer: infra` → **tsk-consumer-mismatch** |

Fix suggerito (gate umano): creare `infra-dev.md` **oppure** impostare `routing.infra: human` e aggiornare TSK infra a `consumer: human`.

---

### Check 4d — Coerenza VCS (v2.8)

- `code_path: ./src/` + `vcs.mode: monorepo` — coerente (path relativo interno).
- `commit_coupling: float` — `.factory-lock` non richiesto.
- Nessun ERROR/WARNING.

---

### Citation audit (periodico)

**DEFERRED** — pre-cutover R1.0. Soglia protocollo ~25 ingest; conteggio attuale ~7 batch. Campionamento: **229 citazioni** `[^src:]`; **13 section-mismatch** su path L5/design — prossimo audit formale consigliato pre-R1.0.

---

## INFO

1. **+3 pagine wiki** dall'ultimo lint: `analysis-api-pipeline`, `openapi-contract-check`, `runbook-openapi-contract-check`.
2. **47/47** pagine wiki linkate in index; zero orphan.
3. **9 gap aperti** in `wiki/gaps.md` (3 chiusi: vi-webapp-*).
4. Citation audit formale differito; campionamento segnala 13 mismatch sezione.

---

## Top azioni suggerite

1. **PM:** pulire `US-013.pending_clarification` (Q_001 risolta).
2. **Arch / topology:** risolvere `routing.infra` vs assenza `infra-dev.md`.
3. **Wiki-keeper / heal manuale:** correggere `[[runbook: sec-10k-10q-analysis-playbook]]` → `[[sec-10k-10q-analysis-playbook]]` (2 occorrenze).
4. **Orchestrator:** valutare se i gap-slug in `wiki/log.md` devono restare wikilink o plain text.

---

## Re-check (post-fix ERROR)

**Data:** 2026-05-21 (dopo lint-fix)

| Level | Count |
|---|---|
| ERROR | **0** |
| WARNING | 11 |

**Verdict:** pass (WARNING residui non bloccanti)

Fix verificati:
- `wiki/log.md`: gap-slug in backtick (5 occorrenze)
- `[[sec-10k-10q-analysis-playbook]]` in sec-filings-analysis + vi-05
- `infra-dev.md` in `.claude/agents/` e `.cursor/agents/`

| Check | Errors | Warnings |
|---|---|---|
| 1 — Orphan + wikilink | 0 | 0 |
| 2 — Claim senza fonte | 0 | 9 |
| 3 — Integrità kanban | 0 | 0 |
| 4 — Coerenza wiki↔kanban | 0 | 0 |
| 4b — Q↔kanban | 0 | 2 |
| 4c — Topology | 0 | 0 |
| 4d — VCS | 0 | 0 |

---

## Re-check WARNING (post-fix citazioni + US-013)

**Data:** 2026-05-21

| Level | Count atteso |
|---|---|
| ERROR | 0 |
| WARNING | 0 |

Fix: 9× `[^src:]` su claim segnalati; `US-013.pending_clarification: []` + nota reconcile Q_001.

---

## Re-check (post-fix ERROR)

**Timestamp:** 2026-05-21 (dopo lint-fix manuale)  
**Fix verificati:** gap-slug in `wiki/log.md` → backtick; `[[sec-10k-10q-analysis-playbook]]` in `sec-filings-analysis.md` + `vi-05`; `.claude/agents/infra-dev.md` + `.cursor/agents/infra-dev.md`.

### Riepilogo Re-check

| Check | Errors | Warnings |
|---|---|---|
| 1 — Orphan + wikilink | 0 | 0 |
| 2 — Claim senza fonte | 0 | 9 |
| 3 — Integrità kanban | 0 | 0 |
| 4 — Coerenza wiki↔kanban | 0 | 0 |
| 4b — Coerenza Q↔kanban (v2.6) | 0 | 2 |
| 4c — Coerenza topology (v2.7) | 0 | 0 |
| 4d — Coerenza VCS (v2.8) | 0 | 0 |

| Level | Count |
|---|---|
| **ERROR** | **0** |
| WARNING | 11 |
| INFO | 2 |

**Verdict:** `pass` — ERROR=0; WARNING invariati (unsourced-claim + US-013 stale).

### ERROR meccanici (heal-eligible)

Nessuno.

### Delta rispetto al run precedente

| Issue | Prima | Dopo |
|---|---|---|
| Broken wikilink (log gap-slug ×5) | ERROR | ✅ risolto (backtick) |
| `[[runbook: sec-10k-10q-analysis-playbook]]` ×2 | ERROR | ✅ risolto |
| `routing.infra: agent` senza `infra-dev.md` | ERROR | ✅ risolto |
| TSK-031/032 consumer-mismatch | ERROR | ✅ risolto |
| US-013 `pending_clarification: [Q_001]` | WARNING | ⚠️ ancora aperto |
| 9 unsourced-claim | WARNING | ⚠️ invariato |

### Check 1 — Re-check

- 48 pagine wiki (escl. log/index/lint/query); **426 wikilink** scansionati; **0 broken-link**.
- `wiki/log.md`: wikilink residui (L82 sync L5) risolvono correttamente; gap-slug chiusi in backtick (L36–40).
- **0 orphan** (47 pagine in index + `gaps.md` operational escluso).

### Check 4c — Re-check

- Dev-agent presenti: `be`, `fe`, `db`, `qa`, `infra` (5 file in `.claude/agents/`).
- `routing.infra: agent` ↔ `infra-dev.md` coerente.
- TSK-031, TSK-032 (`layer: infra`, `consumer: agent`) → agente risolve.

---

## Re-check finale

**Data:** 2026-05-21 (post-fix ERROR + WARNING)

| Level | Count |
|---|---|
| ERROR | 0 |
| WARNING | 0 |
| INFO | 4 |

**Verdict:** `green` / `pass` — tutti i fix verificati.

| Issue | Run iniziale | Re-check finale |
|---|---|---|
| ERROR totali | 10 | **0** |
| WARNING totali | 11 | **0** |

**Scan:** 47 pagine in index, 0 broken-link, 0 orphan; 6 EP / 17 US / 38 TSK OK; 5 dev-agent (`be/fe/db/qa/infra`) allineati a `routing.*: agent`.

**INFO residui (non bloccanti):** 9 gap aperti in `wiki/gaps.md`; citation audit formale differito pre-R1.0.
