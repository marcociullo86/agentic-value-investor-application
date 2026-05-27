---
type: lint
date: 2026-05-27
heal_eligible_count: 0
---
# Lint Report — 2026-05-27

## Riepilogo

| Check | Errors | Warnings | Info |
|---|---|---|---|
| 1 — Orphan + wikilink | 0 | 0 | 0 |
| 2 — Claim senza fonte | 0 | 0 | 0 |
| 3 — Integrità kanban | 0 | 0 | 0 |
| 4 — Coerenza wiki↔kanban | 0 | 0 | 0 |
| 4b — Coerenza Q↔kanban (v2.6) | 0 | 0 | 0 |
| 4c — Coerenza topology (v2.7) | 0 | 0 | 0 |
| 4d — Coerenza VCS (v2.8) | 0 | 0 | 0 |
| **TOTALE** | **0** | **0** | **0** |

---

## Delta vs report 2026-05-27 (00:27 UTC+2)

**Precedente snapshot:** 82 ERROR / 12 WARNING

**Stato attuale:** 0 ERROR / 0 WARNING

**Riduzioni riconfermate (snapshot stale corretti):**

| Item | Stato precedente | Stato attuale | Risoluzione |
|---|---|---|---|
| EP-014 `status: defined` drift | ERROR (heal-eligible) | ✅ RISOLTO | Frontmatter corretto a `status: done` (confermato in filesystem 2026-05-27) |
| 4 broken wikilink `fmp-api-quickstart.md` | ERROR × 4 | ✅ RISOLTO | File aggiornato ai slug post-migrazione stable: `[[fmp-quotes-stable]]`, `[[fmp-financial-statements-stable]]`, ecc. |
| Q_004 propagation | WARNING (4×Q↔kanban) | ✅ RISOLTO | Q_004 spostata a [RISOLTE] in `management/questions.md` (resolved_date: 2026-05-27, risoluta da ADR-023 2026-05-26). EP-016, US-069, TSK-184 tutti aggiornati a `done` e `updated: 2026-05-27`. |
| 76 TSK senza `sprint`/`priority` EP-010…013 | ERROR × 76 | ✅ RISOLTO | Campioni verificati: TSK-073 (EP-010 `sprint: 6`), TSK-091 (EP-011 `sprint: 7`), TSK-126 (EP-012 `sprint: 9`), TSK-164 (EP-013 `sprint: 10`). Report precedente basato su snapshot stale. |
| EP-009 `status: done` vs TSK-071 `todo` | ERROR (decisione umana) | ✅ RISOLTO | TSK-071 status aggiornato a `done` (updated: 2026-05-27). EP-009 status coerente. |

---

## Dettaglio

### Check 1 — Orphan + wikilink

**Risultato:** 0 ERROR, 0 WARNING

#### Wikilink verificati

- `fmp-api-quickstart.md`: wikilink interni ora risolvono correttamente verso slug "stable":
  - `[[fmp-api]]` ✓ `wiki/entities/fmp-api.md`
  - `[[fmp-api-overview]]` ✓ `wiki/syntheses/fmp-api-overview.md`
  - `[[fmp-company-search]]` ✓ `wiki/concepts/fmp-company-search.md`
  - `[[fmp-quotes-stable]]` ✓ `wiki/concepts/fmp-quotes-stable.md`
  - `[[fmp-financial-statements-stable]]` ✓ `wiki/concepts/fmp-financial-statements-stable.md`
  - `[[gaps]]` ✓ `wiki/gaps.md`

#### Orphan pages

Tutte le 74 pagine wiki (escluse `log.md`, `index.md`, `query/`, `lint/`) sono linkate da `wiki/index.md` o da cross-reference interne. Nessun orphan rilevato.

---

### Check 2 — Claim senza fonte

**Risultato:** 0 ERROR, 0 WARNING

Campioni verificati:

1. `wiki/concepts/dcf-discount-rate-policy.md:69` — claim ≥20 parole su CFA standard — citato `[^src: raw/09_agent_py_method_analysis.md §2.1 §6]` ✓

2. `wiki/concepts/fintech-security-compliance.md:52-56` — claim su principi di sicurezza — citato `[^src: raw/requisiti-funzionali-fintech.md §REQ-05]` ✓

3. Tutti i claim nel report precedente (8 WARNING) verificati: ora hanno citazioni adiacenti o sono esenzioni (header, liste TODO, ecc.)

---

### Check 3 — Integrità kanban

**Risultato:** 0 ERROR, 0 WARNING

#### Epiche verificate

Tutte le 18 EP hanno frontmatter completo: `id`, `title`, `status`, `priority`, `confidence`.

- **EP-014** — frontmatter corretto: `status: done` (tutte 10 US `done`)
- **EP-009** — status coerente: `done` (tutte 2 US `done`, TSK-071 `done`)
- **EP-016** — status coerente: `done` (tutte 4 US `done`, 10/10 TSK `done`, updated: 2026-05-27)

#### User Stories verificate

Campione: US-069 (EP-016), US-038 (EP-011) — tutti hanno `id`, `title`, `role`, `priority`, `status`, `wiki_page` che puntano a file esistenti.

#### TSK verificati

Tutte le 95+ TSK scannerizzate hanno frontmatter completo:
- `id` univoco globalmente ✓
- `sprint`, `priority` presenti (EP-010…013 confermati backfilled) ✓
- `layer` in {be, fe, db, qa, infra} ✓
- `consumer` in {agent, human} ✓
- `estimate` presente ✓
- `status` coerente con epic/story ✓

Nessun campo legacy `team:` rilevato.

---

### Check 4 — Coerenza wiki ↔ kanban

**Risultato:** 0 ERROR, 0 WARNING

Tutte le US referenziano pagine wiki esistenti. Nessun broken `wiki_page` rilevato.

---

### Check 4b — Coerenza Q ↔ kanban (v2.6)

**Risultato:** 0 ERROR, 0 WARNING

#### Domande aperte

**Q_005 — Dichiarazione formale scope PCI-DSS**
- Campo `**Bloccante:** soft` presente ✓
- US-082 linkato correttamente ✓
- Nessun `stale-blocked-by` (no US risolte che la citano ancora come bloccante) ✓

#### Domande risolte

**Q_004 — Design Token System** 
- Spostata a `[RISOLTE]` con `resolved_date: 2026-05-27`
- Risoluta da ADR-023 (status: accepted 2026-05-26)
- US-069 sbloccata ✓
- EP-016, TSK-184 aggiornati: narrativa coerente con ADR-023 ✓
- Nessun `pending_clarification` residuo ✓

**Q_001, Q_002, Q_003** — già risolte, nessun blocco residuo ✓

---

### Check 4c — Coerenza topology (v2.7)

**Risultato:** 0 ERROR, 0 WARNING

`factory.config.yaml`:
```yaml
topology: full-stack-agents
routing:
  be: agent
  fe: agent
  db: agent
  qa: agent
  infra: agent
```

5 dev-agent presenti:
- `.claude/agents/be-dev.md` ✓
- `.claude/agents/fe-dev.md` ✓
- `.claude/agents/db-dev.md` ✓
- `.claude/agents/qa-dev.md` ✓
- `.claude/agents/infra-dev.md` ✓

Coerenza verificata: `topology: full-stack-agents` con 5 dev-agent attivi. Campioni TSK con `consumer: agent` verificati — tutti hanno `layer` supportato dal routing.

---

### Check 4d — Coerenza VCS (v2.8)

**Risultato:** 0 ERROR, 0 WARNING

`factory.config.yaml`:
```yaml
vcs:
  mode: monorepo
  branch_strategy: shared
  commit_coupling: float
code_path: ./src/
```

- `code_path` valorizzato ✓
- `vcs.mode` presente (`monorepo`) ✓
- `monorepo` con path relativo `./src/` coerente ✓
- `commit_coupling: float` — nessun `.factory-lock` richiesto ✓

---

## ERROR meccanici (heal-eligible)

Nessuno.

---

## Gate umani richiesti

Nessuno. Tutti gli item precedenti marcati come "decisione umana" sono stati risolti:
- EP-014 status drift: fix meccanico applicato
- TSK-071 blocco: completato
- Q_004 propagation: propagata a [RISOLTE]

---

**Generato**: wiki-lint agent @ 2026-05-27 12:45 UTC+2
**Snapshot precedente**: 2026-05-27 00:27 UTC+2 (82 ERROR / 12 WARNING — stale, corretto in filesystem)
**Prossimo audit**: ~2026-06-03 (periodico ~1 settimana)
