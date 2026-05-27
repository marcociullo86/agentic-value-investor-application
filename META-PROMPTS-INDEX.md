# Meta-prompts — Index & version history

> **Nota v2.13 (2026-05-27)**: dalla v2.13 in poi i meta-prompt seed vivono **nel repo**
> in [`meta-prompts/`](meta-prompts/) (versionati col PATTERN). Vedi
> [`meta-prompts/README.md`](meta-prompts/README.md) per il changelog completo e i
> dettagli di replicabilità multi-runtime.
>
> Questo `META-PROMPTS-INDEX.md` resta come **archivio dei meta-prompt legacy** ≤ v2.11
> (file `meta-prompt-llm-wiki-factory-v2.X.md` al root del repo). Per le versioni v2.12+
> consultare `meta-prompts/`.

## Versione canonica corrente (v2.13+)

| Campo | Valore |
|---|---|
| Seed v2.13 | [`meta-prompts/v2-13/factory-bootstrap.md`](meta-prompts/v2-13/factory-bootstrap.md) |
| Seed v2.12 | [`meta-prompts/v2-12/factory-bootstrap.md`](meta-prompts/v2-12/factory-bootstrap.md) |
| Seed v2.11 (snapshot) | [`meta-prompts/v2-11/factory-bootstrap.md`](meta-prompts/v2-11/factory-bootstrap.md) |
| Pattern version | **v2.13** (multi-adapter scaffolding) |
| Aggiornata | 2026-05-27 |
| Allineata a | [`PATTERN.md`](PATTERN.md) v2.13 |
| Bootstrap entry-point | `/factory-bootstrap` (dispatcher in `~/.claude/commands/factory-bootstrap.md`) → seed in `meta-prompts/v2-13/` |
| Replicabilità | Self-contained portable: fetchable da `https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-13/factory-bootstrap.md` per qualunque AI agent |

## Archivio legacy (≤ v2.11, file al root)

I file `meta-prompt-llm-wiki-factory-v2.X.md` al root del repo sono **snapshot statici
legacy** ≤ v2.11. Mantenuti per backward compat dei runbook di migrazione e archeologia.
Dalla v2.12 in poi il versioning vive in `meta-prompts/`.

## Archivio versioni

| Versione | File | Pattern | Data archivio | Cambio principale | Runbook migrazione |
|---|---|---|---|---|---|
| **v2.11** | [`meta-prompt-llm-wiki-factory.md`](meta-prompt-llm-wiki-factory.md) (HEAD) | 2.11 | 2026-05-22 | Parallel scheduler agent-agnostic basato su DAG di dipendenze dichiarate nei frontmatter: nuovi campi opzionali `depends_on` (EP/US/TSK), `blocked_by` esteso a TSK, `code_path` (TSK, glob L5 per conflict detection); nuovo §18 «Parallel scheduling» con modello (`E_dep ∪ E_conf`), algoritmo a 3 step (toposort + level grouping + graph-coloring partition), domini di parallelismo (§18.3), 8 regole inviolabili dello scheduler (R.S1–R.S8: single-committer preservato, conflict-free su `code_path`, cap fan-out, gate umano sopra threshold, no rollback collaterale, VCS sempre serializzato), nuovo blocco `scheduler:` in `factory.config.yaml` (`enabled`/`max_parallel`/`parallel_gate_threshold`/`code_path_conflict`/`empty_code_path_policy`/`domains:`), output wave-plan in chat. Orchestrator esteso con dispatch parallelo (multi-`Agent` call nello stesso turno). Skill `parallel-scheduling` provider-agnostic con 5 fasi. Lint Check 4g (cycle detection + drift body↔frontmatter + validazione `code_path`/`blocked_by`/`scheduler:`). Retrocompat: artefatti senza `depends_on` → level 0; senza `code_path` → conservativo (serial) | [`wiki/runbooks/migration-v211.md`](wiki/runbooks/migration-v211.md) |
| v2.10 | [`meta-prompt-llm-wiki-factory-v2.10.md`](meta-prompt-llm-wiki-factory-v2.10.md) | 2.10 | 2026-05-22 | Publisher adapters multi-target (L3/L4): nuovo ruolo *Publisher* pluralizzabile per provider (GitHub/GitLab/Jira/Linear/custom), nuovo verbo `Publish`, nuovo campo frontmatter opzionale `external_id:` su EP/US/TSK, nuova regola §7 r.15 (gate cross-tool: conferma esplicita prima di create/update batch; mai delete/close automatici), nuovo blocco `kanban_publish:` in `factory.config.yaml`, nuovo §17 «Publisher adapters» con contratto multi-provider, `github-publisher` come implementazione di riferimento via `gh` CLI, skill agnostic `publisher-protocol` + skill provider-specific `github-mapping`, lint Check 4f, comando `/kanban-publish`. Push-only in v2.10; bidirectional candidato v2.11 (poi rimandato a v2.12, sostituito in v2.11 da parallel scheduler) | [`wiki/runbooks/migration-v210.md`](wiki/runbooks/migration-v210.md) |
| v2.9 | [`meta-prompt-llm-wiki-factory-v2.9.md`](meta-prompt-llm-wiki-factory-v2.9.md) | 2.9 | 2026-05-22 | Sync adapters multi-sorgente: nuovo sub-agent `figma-sync` per Figma (Anthropic API + Figma MCP), nuovo shape L1 `.kb.json`, nuova grammatica citazione JSON `[^src: <path>.kb.json §<dotted-path>]`, nuovo §16 «Sync adapters» con contratto per nuovi adapter, ingest-protocol esteso (ramo Figma schema-driven), lint Check 4e (coerenza manifest↔raw), `.extraction-manifest.json` esteso con `source`/`primary_artifact`/`secondary_artifacts`/`extractor_version` | [`wiki/runbooks/migration-v29.md`](wiki/runbooks/migration-v29.md) (futuro) |
| v2.8 | [`meta-prompt-llm-wiki-factory-v2.8.md`](meta-prompt-llm-wiki-factory-v2.8.md) | 2.8 | 2026-05-22 | VCS integration esplicita: blocco `vcs:` (`mode: monorepo\|submodule\|sibling\|external\|none`), skill `vcs-handoff` invocata da `dev-protocol` Fase 5, lint check 4d, terzo formato citazione `[^src5-sub:`, regola §7 r.14 (gate VCS), `.factory-lock` opzionale | [`wiki/runbooks/migration-v28.md`](wiki/runbooks/migration-v28.md) |
| v2.7 | [`meta-prompt-llm-wiki-factory-v2.7.md`](meta-prompt-llm-wiki-factory-v2.7.md) | 2.7 | 2026-05-20 | Execution layer L5, dev-agent per-layer, topologie esplicite, stack modes (manual/guided/auto), `factory.config.yaml`, TSK con `layer`+`consumer`, regola §7 r.13 | [`wiki/runbooks/migration-v27.md`](wiki/runbooks/migration-v27.md) |
| v2.6 | [`meta-prompt-llm-wiki-factory-v2.6.md`](meta-prompt-llm-wiki-factory-v2.6.md) | 2.6 | 2026-05-20 | 2007 | Gate L4 graduato (`blocking_level: hard\|soft`), operazione `Propagate` (skill `propagate-resolution`), auto-promotion suggerita in `/run` | [`wiki/runbooks/migration-v26.md`](wiki/runbooks/migration-v26.md) |
| v2.5 | [`meta-prompt-llm-wiki-factory-v2.5.md`](meta-prompt-llm-wiki-factory-v2.5.md) | 2.5 | 2026-05-20 | 1865 | Operazione `Heal` (evaluator-optimizer vincolato), skill `heal-protocol`, comando `/heal`, whitelist chiusa (3 categorie ERROR), gate umano bulk, max 3 iterazioni | — (incrementale rispetto v2.4) |
| v2.2 | [`meta-prompt-llm-wiki-factory-v2.2.md`](meta-prompt-llm-wiki-factory-v2.2.md) | 2.2 | 2026-05-18 | 1256 | Memory tree (`episodic`/`semantic`/`procedural`), rimozione hook bash/python, two-phase commit, `wiki-staging/`, JSON Schemas, agenti `verifier-*`, regimi A/B | [`wiki/runbooks/migration-v22.md`](wiki/runbooks/migration-v22.md) |
| v2 | [`meta-prompt-llm-wiki-factory-v2.md`](meta-prompt-llm-wiki-factory-v2.md) | 2.0/2.1 | 2026-05-18 | 916 | Separazione `PATTERN.md`/adapter (v2.1); rimozione `project_manifest.json`, `wiki/confidences/`, agente `reviewer` (v2.0) | — (legacy) |

**Versioni non archiviate come file separati:**
- v2.3, v2.4 — modifiche incrementali assorbite da v2.5 (refactor "thin agents, fat skills" + ingest parallelo). Cambi documentati nel changelog di v2.5 e in `wiki/runbooks/thin-agents-fat-skills-refactor.md`.

## Politica di versioning

1. **Pattern major** (es. v2.x → v3.x): scaffold incompatibile, runbook di migrazione obbligatorio, snapshot del meta-prompt precedente.
2. **Pattern minor** (es. v2.6 → v2.7): backward-compat per artefatti esistenti (frontmatter, file), nuove sezioni nel PATTERN. Runbook + synthesis raccomandati. Snapshot del meta-prompt opzionale ma raccomandato se i cambi sono visibili al bootstrap.
3. **Patch** (es. fix tipografici, riformulazioni): no nuova versione, solo commit con `chore(meta-prompt)`.

## Naming convention degli snapshot

```
meta-prompt-llm-wiki-factory.md          → HEAD (sempre l'ultima versione, v2.11)
meta-prompt-llm-wiki-factory-v2.10.md    → snapshot v2.10 (creato al passaggio a v2.11, 2026-05-22)
meta-prompt-llm-wiki-factory-v2.9.md     → snapshot v2.9 (creato al passaggio a v2.10, 2026-05-22)
meta-prompt-llm-wiki-factory-v2.8.md     → snapshot v2.8 (creato al passaggio a v2.9, 2026-05-22)
meta-prompt-llm-wiki-factory-v2.7.md     → snapshot v2.7 (creato al passaggio a v2.8)
meta-prompt-llm-wiki-factory-v2.6.md     → snapshot v2.6 (esistente)
...
```

Lo snapshot della versione corrente NON si crea finché esiste solo quella versione; si crea **al momento dell'upgrade** alla versione successiva (così HEAD e snapshot coincidono mai contemporaneamente, evitando confusione).

## Cross-reference

- Contratto universale: [`PATTERN.md`](PATTERN.md) — fonte di verità autoritativa, agent-agnostic.
- Adapter di default: [`CLAUDE.md`](CLAUDE.md) → `.claude/`.
- Bootstrap command (Claude Code): `~/.claude/commands/factory-bootstrap.md` — legge `PATTERN.md` + il meta-prompt HEAD come fonte di verità.
- Runbook di migrazione tra versioni: `wiki/runbooks/migration-v*.md`.
- Sintesi tematiche su cambi architetturali maggiori: `wiki/syntheses/`.

## Storia (one-liner per versione)

```
v1.0  → legacy (docs/, management/{epics,stories}/, backlog/sprints/)
v2.0  → rimuove project_manifest.json, wiki/confidences/, agente reviewer
v2.1  → separa PATTERN.md (contratto) da adapter (.claude/, .cursor/, ...)
v2.2  → memory tree, rimuove hook/two-phase commit/JSON Schemas/A-B regimes
v2.3  → refactor "thin agents, fat skills" (13 skill)
v2.4  → ingest parallelo (batch ≥ 3), single-committer
v2.5  → operazione Heal (evaluator-optimizer)
v2.6  → gate graduato hard/soft + Propagate + auto-promotion suggerita
v2.7  → execution layer L5 + topology + dev-agents + stack modes
v2.8  → VCS integration (monorepo/submodule/sibling/external) + vcs-handoff skill
v2.9  → Sync adapters multi-sorgente: figma-sync + .kb.json + §16 sync adapters contract
v2.10 → Publisher adapters multi-target: github-publisher + external_id + §17 publisher adapters contract
v2.11 → Parallel scheduler DAG-driven: depends_on/blocked_by/code_path frontmatter + §18 parallel scheduling + R.S1–R.S8 + scheduler: config block  ← HEAD
```
