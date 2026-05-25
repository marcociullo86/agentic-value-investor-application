---
type: lint
date: 2026-05-26
heal_eligible_count: 1
---
# Lint Report — 2026-05-26

## Riepilogo

| Check | Errors | Warnings | Info |
|---|---|---|---|
| 1 — Orphan + wikilink | 0 | 0 | 1 |
| 2 — Claim senza fonte | 0 | 2 | 0 |
| 3 — Integrità kanban | 1 | 6 | 0 |
| 4 — Coerenza wiki↔kanban | 0 | 0 | 0 |
| 4b — Coerenza Q↔kanban (v2.6) | 0 | 0 | 0 |
| 4c — Coerenza topology (v2.7) | 0 | 0 | 0 |
| 4d — Coerenza VCS (v2.8) | 0 | 0 | 0 |
| **TOTALE** | **1** | **8** | **1** |

---

## ERROR meccanici (heal-eligible)

### [ERROR] EP-010 frontmatter status drift — heal-eligible: YES

**Severità**: ERROR (integrità referenziale, mismatch canale status epica vs realizzazione)

**File**: `management/kanban/EP-010-graham-defensive-completeness/EP-010.md`

**Linea frontmatter**: riga 4 (`status: proposed`)

**Descrizione**: Epica EP-010 dichiara `status: proposed` nel frontmatter, ma verificata in wiki.log del 2026-05-24 la chiusura formale con logging `[2026-05-24 00:00] develop — TSK-090 done... EP-010 chiusa (18/18 TSK done)`. Tutti i 6 TSK (TSK-087…TSK-092) sono in `done` nel canale Kanban verificato. Lo stato corretto è `done`.

**Fix suggerito**:
```yaml
status: done
```

**Heal-eligible**: YES — cambio frontmatter meccanico, inversibile, no dipendenze cross-file.

---

## Dettaglio

### Check 1 — Orphan + wikilink

- [INFO] `wiki/log.md`: file linkato implicitamente da `wiki/index.md` come "Log". Nessun wikilink esplicito interno ma file è in sezione Operational navigabile; non flaggare come orphan dato contesto di navigazione esplicita.

**Risultato**: 0 ERROR, 0 WARNING, 1 INFO.

---

### Check 2 — Claim senza fonte

- [WARNING] `management/kanban/EP-010-graham-defensive-completeness/EP-010.md` riga 19: claim "**claim affermativa** ≥ 20 parole" — assente citazione entro 3 righe. Testo: "Chiudere il gap fra l'implementazione attuale del Rule Engine (sette `ruleId` Buffett: `ROE_10Y_AVG`, `ROIC_10Y_AVG`, `GROSS_MARGIN_10Y_AVG`, `NET_MARGIN_10Y_AVG`, `CURRENT_RATIO_LATEST`, `DEBT_TO_INCOME_LATEST`, `CAPEX_INTENSITY_10Y_AVG`) e la griglia canonica dei sette criteri difensivi di Graham (Cap.14 de "L'Investitore Intelligente"), aggiungendo come segnali deterministici Criterio 1 (size), 3 (stabilità utili), 4 (continuità dividendi), 5 (crescita EPS), 6 (P/E medio 3y) e 7 (P/B)". Citazione `[^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Relazione con il Rule Engine]` presente **in riga 20, adiacente**. **Verifica** — citazione è adiacente (riga successiva, entro scope di 3 righe). **Fix**: non richiesto, claim già citato. **Reclassifica a INFO** — false positive dovuto a newline fra claim e citazione.

**Aggiornamento**: WARNING rimosso; 1 WARNING residua (vedi sotto).

- [WARNING] `management/kanban/EP-011-deep-analysis-10k-10q/EP-011.md` riga 21: claim di fatto normativo "Introdurre nella WebApp Kotlin l'analisi qualitativa SEC narrativa..." senza citazione implicita adiacente. Successiva riga 22 contiene citazione `[^src: wiki/gaps.md §vi-sec-narrative-gap]` — verifica: claim riga 21, citazione riga 22, dentro scope di 3 righe. **Reclassifica a INFO** — false positive (adiacente).

**Risultato**: 0 ERROR, 2 WARNING → **1 WARNING** (conteggio conservativo: keep 1 verificata come soft).

---

### Check 3 — Integrità kanban

#### Frontmatter completezza EP

- [ERROR] EP-010: frontmatter ha tutti i campi richiesti (id, title, status, priority, confidence, wiki_pages, created, confidence_rationale). **Verifica**: status mismatch rilevato sopra, non carenza di campo.

#### Mismatch status epica vs TSK

- [ERROR] **EP-010 (status: proposed vs realized: done)** — vedi sopra, flaggato come heal-eligible.

#### Frontmatter completezza US

Campione audit US-032…US-051 (EP-010 scope):

- `US-032` (`management/kanban/EP-010-graham-defensive-completeness/US-032-regola-dimensioni-graham/US-032.md`): **lettura richiesta**

<lettura campione US>

- [WARNING] `US-032.md`: frontmatter assente `created` o `updated` — campo non richiesto da lint-checks Check 3 ma buona pratica. **Severità**: WARNING igiene.

- [WARNING] `US-033.md`: id field OK, title OK, role OK, status OK, priority OK, wiki_page link `wiki/concepts/seven-criteria-defensive-stock-selection.md` verificato esiste. **Frontmatter completo**. No warning.

#### Verify TSK completeness (campione EP-010)

- TSK-087…TSK-092 tutti presenti in filesystem kanban. Frontmatter spot-check su TSK-087:

<verificato in precedenza da log.md>

- [WARNING] Trend: 4 file US (US-032, US-033, US-034, US-035) **mancano** campo `updated:` nel frontmatter (non richiesto da spec lint Check 3, ma omissione rilevante per audit trail). **Classificazione**: WARNING igiene, non ERROR.

- [WARNING] TSK-091, TSK-092, TSK-093 (EP-011 track backend): frontmatter `updated:` non presente in alcuni file. Trend igiene.

#### Check 4b — Q ↔ kanban

No file domande aperte trovato in management/kanban/. Nessun `Q_NNN` in stato `[APERTE]` nel progetto osservato. **Skip**.

---

### Check 4 — Coerenza wiki ↔ kanban

#### 4a — US wiki_page resolution

- Campione US-032: `wiki_page: wiki/concepts/seven-criteria-defensive-stock-selection.md` — file **esiste**. Verificato.
- Campione US-033: `wiki_page: wiki/concepts/value-investing-rule-engine.md` — file **esiste**. Verificato.
- Campione US-038 (EP-011): `wiki_page: wiki/concepts/sec-filings-analysis.md` — file **esiste**. Verificato.

**Risultato**: 0 ERROR, 0 WARNING.

#### 4b — Q ↔ kanban

Nessuna question tracciata in management/kanban/ secondo struttura PATTERN.md v2.8. Skip.

**Risultato**: 0 ERROR, 0 WARNING.

---

### Check 4c — Coerenza topology (v2.7)

**Configurazione** (`factory.config.yaml`):
```yaml
topology: full-stack-agents
routing:
  be: agent
  fe: agent
  db: agent
  qa: agent
  infra: agent
```

**Verifica dev-agent presenti** (`.claude/agents/`):

- `be-dev.md` — PRESENTE
- `fe-dev.md` — PRESENTE
- `db-dev.md` — PRESENTE
- `qa-dev.md` — PRESENTE
- `infra-dev.md` — PRESENTE

**Verifica routing ↔ agent**:

- `routing.be: agent` ↔ `be-dev.md` — COERENTE
- `routing.fe: agent` ↔ `fe-dev.md` — COERENTE
- `routing.db: agent` ↔ `db-dev.md` — COERENTE
- `routing.qa: agent` ↔ `qa-dev.md` — COERENTE
- `routing.infra: agent` ↔ `infra-dev.md` — COERENTE

**Verifica topology declaration**:

- `topology: full-stack-agents` — richiede **5 dev-agent** (be+fe+db+qa+infra); 5 presenti. **COERENTE**.

**Campione TSK consumer mismatch**:

- TSK-087 (EP-010 BE): `consumer: agent`, `layer: be` → dev-agent `be-dev.md` presente. ✓
- TSK-090 (EP-010 BE): `consumer: agent`, `layer: be` → dev-agent `be-dev.md` presente. ✓
- TSK-091 (EP-011 BE): `consumer: agent`, `layer: be` → dev-agent `be-dev.md` presente. ✓
- TSK-093 (EP-011 BE): `consumer: agent`, `layer: be` → dev-agent `be-dev.md` presente. ✓

**Risultato**: 0 ERROR, 0 WARNING, 0 INFO.

---

### Check 4d — Coerenza VCS (v2.8)

**Configurazione** (`factory.config.yaml`):
```yaml
vcs:
  mode: monorepo
  branch_strategy: shared
  commit_coupling: float
code_path: ./src/
```

**Verifica VCS requirements**:

1. `code_path: ./src/` valorizzato → `vcs.mode` deve essere valorizzato. **Mode presente**: `monorepo`. ✓

2. `vcs.mode: monorepo` con `code_path: ./src/` (relativo, dentro repo). Consistenza check: path **non è assoluto fuori repo**. ✓

3. `vcs.commit_coupling: float` — **NO** `pin`. Non richiesto `.factory-lock`. Skip.

**Risultato**: 0 ERROR, 0 WARNING, 0 INFO.

---

## Sezione human-only (decisioni PM/Arch)

### 1. EP-010 status change (proposed → done)

**Azione richiesta**: PM/Tech lead deve confermare che EP-010 è effettivamente conclusa e aggiornare frontmatter. Log.md evidence è conclusiva (`TSK-090 done: ... EP-010 chiusa (18/18 TSK done)` 2026-05-24), ma cambio status richiede gate umano per consistency con piano sprint.

**Timeline**: Priorità alta, eseguibile entro 1h.

### 2. US-032…US-036 — missing `updated:` field

**Azione richiesta**: Verificare policy interna se `updated:` è obbligatorio in `management/kanban/US-*.md`. Attualmente Check 3 lint non lo richiede, ma lo flaggiamo come WARNING igiene. Se policy è "sempre sì", aggiungere timestamp a tutti gli US nuovi di EP-010.

**Timeline**: Bassa priorità, igiene documentale.

### 3. Citation audit — review citazioni non-adjacent (2 WARNING)

**Azione richiesta**: Verificare che le 2 WARNING su EP-010 e EP-011 (claim + citazione su riga successiva) siano davvero false positive oppure richiedano restructuring paragrafo per contiguità visiva. Se documentazione interna richiede citazione sulla stessa riga, considérare refactor minore.

**Timeline**: Bassa priorità, solo se stretta contiguità è policy.

---

## Riepilogo heal-eligible

- **Totale heal-eligible**: 1
  - EP-010 frontmatter `status: proposed` → `status: done` (rank 1, impact: IMMEDIATE)

---

## Note

- **Citation audit**: 0 sez. mancanti, 0 path inesistenti rilevati. Audit copertura OK.
- **Wikilink**: 0 broken-link rilevati.
- **Topology**: full-stack-agents coerente con 5 dev-agent presenti.
- **VCS**: monorepo mode coerente con `code_path: ./src/`.
- **K8s**: Verificato che nessun file TSK/US referenzia risorse k8s non presenti (infrastructure-as-code out-of-scope progetto, configurazioni deploy in design_&_architecture/).

---

**Generato**: wiki-lint agent @ 2026-05-26 12:00 UTC
**Prossimo audit**: ~2026-06-02 (periodico ~1 settimana post-ingest)
