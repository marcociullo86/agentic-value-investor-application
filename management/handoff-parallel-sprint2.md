# Handoff lavoro parallelo — Sprint 2 / bootstrap FE

**Data:** 2026-05-21  
**Branch analisi (Simone):** `feature/sprint2-analysis`  
**Branch collega:** `master` (solo)

---

## Prompt per il collega (copia in Cursor / Claude)

```markdown
# Contesto

Stai lavorando sul repo **agentic-value-investor-application** (factory llm-wiki++ v2.8).
Un collega lavora in parallelo sul branch **`feature/sprint2-analysis`** (pipeline
`GET /api/analysis/{ticker}` — TSK-017→020). Tu lavori **solo su `master`**.

Leggi prima: `PATTERN.md`, `factory.config.yaml`, `raw/tech_stack.md`, `CURSOR.md`.
Per ogni TSK usa `/dev <TSK-id>` e rispetta `dev-protocol` (skill in `.cursor/skills/dev-protocol/`).

## Il tuo obiettivo (Ondata 1)

Sbloccare **tutto il frontend** e l’**infra CI**, senza toccare la pipeline di analisi.

### Sequenza obbligata

1. **TSK-030** (P0) — `src/frontend/` NUOVO
   - Next.js 16 App Router, Tailwind, Zustand, client API da `design_&_architecture/api/openapi.yaml`
   - Non modificare `src/backend/` se non indispensabile
   - Al termine: `status: done` sul TSK + append `wiki/log.md` (develop)

2. **TSK-032** (P1) — dopo TSK-030
   - Dockerfile multi-stage + workflow CI (build + test backend)
   - File tipici: `.github/workflows/`, `src/docker/`

### Opzionale (Ondata 1b, solo se TSK-030 è done)

- **TSK-005** — `ScreenerController` `GET /api/screener` (package dedicato, non ruleengine/dcf)
- **TSK-023** — `HistoricalController` `GET /api/historical/{ticker}`
- **TSK-025** — Flyway **V006** moat (`moat_checklist_entry`) — vedi sotto migrazioni

**Non fare mai:** TSK-017, TSK-018, TSK-019, TSK-020 (riservati al feature branch).

## Migrazioni Flyway — CRITICO

Stato repo: **V001–V005 già applicate**. V005 = `fmp_api_event_log` (già fatto).

| Versione | TSK | Chi |
|----------|-----|-----|
| V006 | TSK-025 moat | **Tu** (master) |
| V007 | TSK-017 dcf_overrides | Collega (feature branch) |
| V008 | TSK-028 watchlists | Tu, **dopo** V006 (Ondata 2) |

**TSK-028** nel kanban menziona V005 watchlist — **ignora**: V005 event log esiste già.
Per watchlist usa **V008** quando arriverai a TSK-028.

Non creare V007 sul master. Merge ordine: tu mergi V006 prima che arrivi V007 dal collega.

## Conflitti da evitare

| Risorsa | Regola |
|---------|--------|
| `src/frontend/**` | Solo tu (TSK-030+) |
| `src/backend/.../ruleengine/dcf/**`, `AnalysisController` | Solo collega — non toccare |
| `openapi.yaml` | Tu: tag `search`, `screener`, `historical`. Lui: tag `analysis` |
| `build.gradle.kts` backend | Coordinare; per TSK-030 preferisci package.json in frontend |

## Gate factory

- `consumer: agent`, `status: todo`, dipendenze `done` nel TSK
- Mai commit/push automatici — proponi messaggio conventional commit, attendi OK umano
- Edit TSK: solo `status:` e `updated:` in frontmatter

## Definition of Done (TSK-030)

- [ ] `src/frontend/` avviabile (`npm run dev`)
- [ ] Client API tipizzato verso `http://localhost:8080` (env configurabile)
- [ ] Nessuna modifica breaking al backend esistente
- [ ] TSK-030 frontmatter → `done`

## Comunicazione

Se devi toccare `FmpAdapter` (TSK-002) o `openapi.yaml` oltre search/screener, segnala
prima in chat al collega sul feature branch.

Inizia leggendo `management/kanban/EP-001-ricerca-e-screening/US-001-ricerca-ticker-simbolo/TSK-030.md`
e `design_&_architecture/decisions/ADR-001-frontend-stack.md`, poi implementa TSK-030.
```

---

## Prompt per Simone (feature branch)

```markdown
Branch corrente: `feature/sprint2-analysis` (da non mergiare finché TSK-019 non è done).

Sequenza: TSK-017 → TSK-018 → TSK-019 → TSK-020.
Non toccare `src/frontend/` (lo fa il collega su master).

Flyway: solo **V007** su questo branch.
openapi: solo sezioni `analysis` + schemas correlati.

Comandi: `/dev TSK-017` poi 018, 019, 020.
```
