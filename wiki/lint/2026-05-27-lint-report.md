---
type: lint
date: 2026-05-27
heal_eligible_count: 1
---
# Lint Report — 2026-05-27

## Riepilogo

| Check | Errors | Warnings | Info |
|---|---|---|---|
| 1 — Orphan + wikilink | 4 | 0 | 0 |
| 2 — Claim senza fonte | 0 | 8 | 0 |
| 3 — Integrità kanban | 78 | 0 | 0 |
| 4 — Coerenza wiki↔kanban | 0 | 0 | 0 |
| 4b — Coerenza Q↔kanban (v2.6) | 0 | 4 | 0 |
| 4c — Coerenza topology (v2.7) | 0 | 0 | 0 |
| 4d — Coerenza VCS (v2.8) | 0 | 0 | 0 |
| **TOTALE** | **82** | **12** | **0** |

---

## ERROR meccanici (heal-eligible)

### [ERROR] EP-014 frontmatter status drift — heal-eligible: YES

**Severità**: ERROR (integrità referenziale, mismatch status epica vs realizzazione)

**File**: `management/kanban/EP-014-logging-strutturato-observability/EP-014.md`

**Linea frontmatter**: `status: defined`

**Descrizione**: Epica EP-014 dichiara `status: defined` nel frontmatter, ma tutti i 14 TSK (TSK-170…TSK-183) sono in `done`. Lo stato corretto è `done`.

**Fix suggerito**:
```yaml
status: done
```

**Heal-eligible**: YES — cambio frontmatter meccanico, inversibile, no dipendenze cross-file.

---

## ERROR non heal-eligible

### [ERROR] EP-009 status drift (decisione umana richiesta)

**File**: `management/kanban/EP-009-throttling-fmp-runbook/EP-009.md`

**Descrizione**: EP-009 dichiara `status: done` ma TSK-071 è `todo` (blocked su gap `fmp-stable-rate-limiting`). 3/4 TSK sono `done`. Serve decisione umana: completare TSK-071 o riportare EP a `in_progress`/`defined`.

**Heal-eligible**: NO — richiede decisione di prodotto.

---

### [ERROR] 76 TSK senza `sprint` e/o `priority` (EP-010..EP-013)

**File**: 76 file TSK in EP-010 (18), EP-011 (35), EP-012 (17), EP-013 (6)

**Descrizione**: Frontmatter ha `id`, `layer`, `consumer`, `estimate`, `status` ma manca `sprint:` e `priority:`. Esempio: `TSK-087.md` — no `sprint`/`priority`.

**Heal-eligible**: NO — valori non inferibili meccanicamente (richiede gate umano per assegnazione bulk).

---

## Dettaglio

### Check 1 — Orphan + wikilink

#### Broken wikilink (4 ERROR)

Tutti in `wiki/runbooks/fmp-api-quickstart.md`:

1. `[[fmp-auth]]` — nessun file con slug `fmp-auth`. Best match: `[[fmp-api]]` (fuzzy 0.67, < 0.90).
2. `[[fmp-search]]` — nessun file. Best match: `[[fmp-company-search]]` (fuzzy 0.72, < 0.90).
3. `[[fmp-quotes]]` — nessun file. Best match: `[[fmp-quotes-stable]]` (fuzzy 0.82, < 0.90).
4. `[[fmp-financial-statements]]` — nessun file. Best match: `[[fmp-financial-statements-stable]]` (fuzzy 0.85, < 0.90).

**Heal-eligible**: NO — tutti sotto soglia fuzzy 0.90. Fix manuale suggerito: aggiornare i wikilink in `fmp-api-quickstart.md` ai nomi corretti post-migrazione stable.

#### Orphan pages

Nessuna pagina orphan rilevata. Tutte le 74 pagine wiki (escluse log/index/gaps/lint) sono linkate dall'index o da cross-link.

**Risultato**: 4 ERROR, 0 WARNING.

---

### Check 2 — Claim senza fonte

8 WARNING (campione verificato):

1. `wiki/concepts/dcf-discount-rate-policy.md:69` — paragrafo ≥20 parole senza `[^src:]`/`[[…]]` entro 3 righe
2. `wiki/concepts/clone-investing-13f-overlay.md:70`
3. `wiki/concepts/correlation-id-tracing.md:27`
4. `wiki/concepts/analysis-api-pipeline.md:58`
5. `wiki/concepts/fmp-key-metrics-ratios.md:133`
6. `wiki/concepts/fintech-security-compliance.md:53`
7. `wiki/concepts/arctic-embed-l-v2.md:81` — link markdown `[ADR-018](...)` non conta come citazione canonica
8. `wiki/syntheses/fmp-api-overview.md:109` — `[[wiki/gaps.md]]` viola convenzione slug (`[[gaps]]` preferito)

**Risultato**: 0 ERROR, 8 WARNING.

---

### Check 3 — Integrità kanban

#### EP status drift (2 ERROR)

1. **EP-014** (`status: defined` vs 14/14 TSK `done`) — **heal-eligible** (vedi sopra)
2. **EP-009** (`status: done` vs TSK-071 `todo`) — **non heal-eligible** (decisione umana)

#### EP confermati OK

- EP-010 `status: done` — **confermato pulito** (fix 2026-05-26 applicato)
- EP-016 `status: done` — 10/10 TSK `done`
- EP-015 `status: done` — 11/11 TSK `done`
- EP-013 `status: done` — 6/6 TSK `done`

#### TSK frontmatter incompleto (76 ERROR)

76 TSK in EP-010 (18), EP-011 (35), EP-012 (17), EP-013 (6) mancano dei campi `sprint:` e/o `priority:`. Tutti hanno `id`, `layer`, `consumer`, `estimate`, `status`.

#### Verifiche OK

- 0 EP frontmatter incompleto (escluso status drift)
- 0 US frontmatter incompleto
- 0 `wiki_page` rotti
- 0 TSK id duplicati

**Risultato**: 78 ERROR, 0 WARNING.

---

### Check 4 — Coerenza wiki ↔ kanban

Tutte le US verificate referenziano pagine wiki esistenti.

**Risultato**: 0 ERROR, 0 WARNING.

---

### Check 4b — Coerenza Q ↔ kanban (v2.6)

4 WARNING:

1. **Q_004 de facto risolta ma ancora in `[APERTE]`** — ADR-023 `accepted`, EP-016 `done` (10/10 TSK), US-069 completata. Risoluzione non propagata a `questions.md`.
2. **EP-016.md** — testo ancora cita Q_004 come bloccante US-069 (narrativa stale vs ADR-023 accepted).
3. **TSK-184.md** — body dice "ADR-023 proposed" (ora `accepted`).
4. **Q_005** — correttamente aperta; US-082/TSK-237 `pending_clarification: [Q_005]` coerente (ADR-025 `proposed`).

Q_004/Q_005 hanno `**Bloccante:** soft` — campo presente, OK.
Nessun `stale-blocked-by` su Q_001/Q_002/Q_003 risolte.

**Risultato**: 0 ERROR, 4 WARNING.

---

### Check 4c — Coerenza topology (v2.7)

`factory.config.yaml`:
```yaml
topology: full-stack-agents
routing: { be: agent, fe: agent, db: agent, qa: agent, infra: agent }
```

5 dev-agent presenti (be-dev, fe-dev, db-dev, qa-dev, infra-dev) — coerente.
Campione TSK `consumer: agent` verificato — routing OK.

**Risultato**: 0 ERROR, 0 WARNING.

---

### Check 4d — Coerenza VCS (v2.8)

```yaml
vcs: { mode: monorepo, branch_strategy: shared, commit_coupling: float }
code_path: ./src/
```

- `code_path` valorizzato + `vcs.mode` presente — OK.
- `monorepo` con path relativo `./src/` — coerente.
- `commit_coupling: float` — no `.factory-lock` richiesto — OK.

**Risultato**: 0 ERROR, 0 WARNING.

---

## Delta vs report 2026-05-26

| Stato | Item |
|---|---|
| ✅ Risolto | EP-010 status drift (`proposed` → `done`) |
| 🆕 Nuovo | EP-014 status drift (`defined` vs 14/14 TSK done) — heal-eligible |
| 🆕 Nuovo | EP-009 status drift (`done` vs TSK-071 `todo`) — decisione umana |
| 🆕 Nuovo | 76 TSK senza `sprint`/`priority` (scan completo vs campione precedente) |
| 🆕 Nuovo | 4 broken wikilink in `fmp-api-quickstart.md` (post-migrazione stable) |
| 🆕 Nuovo | Q_004 de facto risolta ma non propagata a `questions.md` |
| ↔️ Invariato | Topology 5 dev-agent · VCS monorepo · 0 orphan wiki · Q_005 soft aperta |

---

## Azioni prioritarie suggerite

1. **`/heal`** — EP-014 `status: defined` → `done` (heal-eligible meccanico)
2. **EP-009** — decidere: completare TSK-071 o riportare EP a `in_progress` (decisione umana)
3. **Q_004** — spostare in `[RISOLTE]` con riferimento ADR-023 accepted; aggiornare EP-016/TSK-184 (PM)
4. **fmp-api-quickstart.md** — correggere 4 wikilink rotti ai nomi post-migrazione stable (wiki-keeper)
5. **Bulk TSK** — backfill `sprint`/`priority` su 76 TSK di EP-010…013 (gate umano per valori)

---

**Generato**: wiki-lint agent @ 2026-05-27 00:27 UTC+2
**Prossimo audit**: ~2026-06-03 (periodico ~1 settimana)
