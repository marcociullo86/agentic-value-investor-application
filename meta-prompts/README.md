# Meta-Prompts — Factory Bootstrap Versioning

Cartella di storia versionata del meta-prompt `factory-bootstrap`, vissuto **nel repo
meta-framework** (`soli-multi-agents-factory/meta-prompts/`) dalla v2.13 in poi.

Prima di v2.13, i meta-prompt vivevano user-level in `~/.claude/factory-bootstrap/`.
Dalla v2.13 sono **versionati col PATTERN** in questo repo, accessibili a qualunque
agente via GitHub raw URL o git clone (vedi §Replicabilità sotto).

## Versioni disponibili

| Versione | Stato | Cartella | Caratteristiche distintive |
|---|---|---|---|
| **v2.13** | **corrente** | [`v2-13/`](v2-13/factory-bootstrap.md) | **Multi-adapter scaffolding** parallelo. Registry `adapters/<name>/manifest.yaml`. 6 invarianti R.A1-R.A6. Scaffolda `.claude/` + `.cursor/` + `.aider/` + `.openai/` + `.gemini/` + `.chatgpt/`. |
| v2.12 | legacy / snapshot | [`v2-12/`](v2-12/factory-bootstrap.md) | Self-contained portable + Code Quality Review Layer (§19) + multi-repo `code_paths` (§13) + coupling modes R.B1-R.B6 (§16) + existing-repo wiki feeding. Thin orchestrator + 5 bootstrap-* skills. |
| v2.11 | legacy / snapshot | [`v2-11/`](v2-11/factory-bootstrap.md) | Parallel scheduler DAG-driven (§18) + dev-agent opzionali + topology + VCS integration + publisher adapters. Monolitico, ~280 righe. |

## Replicabilità da qualunque agente

Il meta-prompt v2.13 è **self-contained portable**: un singolo file Markdown che
funziona su qualunque macchina/cartella con qualunque AI agent (Claude Code, Cursor,
OpenAI Assistants, Aider, Gemini, ChatGPT).

### Come usare il seed v2.13

**Option 1 — Local file** (se hai clonato questo repo):
```
Apri meta-prompts/v2-13/factory-bootstrap.md nel tuo agent runtime.
Dichiara intent: "Esegui factory-bootstrap v2.13 in <path-destinazione>".
```

**Option 2 — GitHub raw URL** (sempre fresco):
```
URL: https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-13/factory-bootstrap.md
Fai fetchare il file all'agent, poi dichiara intent come Option 1.
```

**Option 3 — Dispatcher Claude Code** (locale):
```
~/.claude/commands/factory-bootstrap.md (thin dispatcher, ~60 righe)
  → fetch v2.13 da GitHub o usa cache locale
```

Per altri runtime: l'agente legge il seed v2.13 direttamente dal raw URL. La
procedura §3 del seed funziona indipendentemente dal runtime.

## Architettura del meta-prompt (evoluzione)

### v2.11 (monolitico)

Singolo file 280 righe con tutta la logica inlined: input → scaffolding → tests →
report.

### v2.12 (thin orchestrator + skill-driven)

Refactoring "thin agents, fat skills" applicato al meta-prompt:

```
v2.12 factory-bootstrap.md  (orchestrator, ~480 righe)
    │
    ├── bootstrap-input-protocol         (raccolta input + archetipi)
    ├── bootstrap-multirepo-protocol     (coupling multi-repo)
    ├── bootstrap-scaffolding-protocol   (file + dir L1-L5 + adapter)
    ├── bootstrap-vcs-protocol           (submodule stamps + .factory-lock)
    └── bootstrap-validation-protocol    (24 check + wiki feeding + report)
```

Self-contained portable: PATTERN essentials inline + adapter templates fetched da GitHub.

### v2.13 (multi-adapter + meta-prompt in repo)

Aggiunge la 6ª skill bootstrap:

```
v2.13 factory-bootstrap.md  (orchestrator, ~470 righe)
    │
    ├── bootstrap-input-protocol         (raccolta input + archetipi)
    ├── bootstrap-multirepo-protocol     (coupling multi-repo)
    ├── bootstrap-multiadapter-protocol  (NUOVO v2.13 — adapter selection + scaffolding)
    ├── bootstrap-scaffolding-protocol   (file + dir L1-L5 + .claude/ reference)
    ├── bootstrap-vcs-protocol           (submodule stamps + .factory-lock)
    └── bootstrap-validation-protocol    (28 check + wiki feeding + report)
```

E i meta-prompt si spostano **nel repo** per:
- Versioning insieme al PATTERN (semantic coupling).
- Accessibilità via raw GitHub URL (no dipendenza da ~/.claude/).
- Diff inter-versione in `git log meta-prompts/`.

## Diff v2.12 → v2.13

### Nuovo

- §12.0 (PATTERN) Adapter registry — `adapters/<name>/` con manifest formale.
- §12.1 (PATTERN) Manifest format esplicito (schema YAML completo).
- §12.2 (PATTERN) 6 invarianti R.A1-R.A6 multi-adapter coexistence.
- §12.3 (PATTERN) `factory.config.yaml.adapters[]` block.
- §12.4 (PATTERN) Principio taglio adapter esteso multi-adapter.
- 6° skill: `bootstrap-multiadapter-protocol`.
- 5 sub-folder in `adapters/`:
  - `cursor/` (full v2.13)
  - `aider/` (full v2.13)
  - `openai/` (partial — setup.py stub)
  - `gemini/` (manifest-only)
  - `chatgpt/` (manifest-only)
- Fase 1.bis (seed) per adapter selection (multi-select).
- 4 nuovi check accettazione (25-28).
- Reorg fisica: meta-prompts/ nel repo (era ~/.claude/factory-bootstrap/).

### Modificato

- §12 (PATTERN) ristrutturato in §12.0-§12.4 con sotto-sezioni numerate.
- factory.config.yaml — aggiunto blocco `adapters:` (default `[{name: claude, folder: .claude, maturity: full}]`).
- Bootstrap procedure: 6 fasi → 7 fasi (insert Fase 1.bis adapter selection).
- Report finale: include tabella adapter installati.

### Rimosso / deprecato

Nessuna rimozione. Backward compat totale con v2.12:
- `factory.config.yaml` senza `adapters:` → default single-adapter Claude.
- Seed v2.12 (`meta-prompts/v2-12/`) ancora funzionante.
- Seed v2.11 (`meta-prompts/v2-11/`) ancora funzionante.

## Statistiche

| Metrica | v2.11 | v2.12 | v2.13 |
|---|---|---|---|
| Righe meta-prompt | 280 | 482 | ~470 |
| Skill bootstrap-* | 0 | 5 | 6 |
| Adapter supportati | 1 (.claude/) | 1 + 5 documentati | **6 (.claude full + cursor/aider full + openai partial + gemini/chatgpt manifest)** |
| Invarianti enforced | r.1-r.15 | r.1-r.17 + R.B1-R.B6 + R.Q1-R.Q7 | + R.A1-R.A6 |
| Check accettazione | 17 | 24 | 28 |
| Multi-repo support | ✗ | ✓ | ✓ |
| CQRL | ✗ | ✓ | ✓ |
| Multi-adapter | ✗ (1 only) | ✗ (1 only) | ✓ (1+ paralleli) |
| Localizzazione meta-prompt | `~/.claude/factory-bootstrap/` | `~/.claude/factory-bootstrap/` | **`<repo>/meta-prompts/`** |

## Roadmap

- **v2.14** (candidato):
  - `.gemini/` + `.chatgpt/` da manifest-only → full.
  - `.openai/run.py` completo (orchestrator Python multi-Assistant).
  - `/factory-add-adapter <name>` command per aggiungere adapter a runtime.
  - Retrofit skill `/retrofit-factory` per migrare v2.11/v2.12 → v2.13.
- **v2.15** (candidato):
  - MCP server integration trasversale agli adapter.
  - Cross-adapter operation coordination (es. wave plan dispatch parallelo cross-runtime).
  - Plan domain scheduler (multi-PM per epica indipendente).

## Manutenzione

Quando si rilascia una nuova versione del meta-prompt:

1. **Snapshot della versione corrente**: assicurati che `v2-XX/factory-bootstrap.md`
   sia stabile.
2. **Crea nuova sub-folder** `v2-YY/` con il nuovo seed.
3. **Aggiorna questo README**: aggiungi una riga in "Versioni disponibili" + sezione "Diff vX → vY".
4. **Aggiorna il dispatcher** `~/.claude/commands/factory-bootstrap.md`: punta v2.YY come default.
5. **Aggiorna eventuali skill `bootstrap-*`** in `.claude/skills/` se la nuova versione richiede.
6. **Bump PATTERN.md** se ci sono cambiamenti contrattuali (§20 changelog).
7. **Commit + push**.
