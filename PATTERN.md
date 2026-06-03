# PATTERN — Agentic Factory `llm-wiki++` v2.17

> Contratto universale agent-agnostic. Qualsiasi runtime (Claude Code, OpenAI Assistants,
> Cursor, Aider, Gemini, ChatGPT, …) che rispetti questo file può operare sul repo. Gli
> adapter di runtime vivono in cartelle dedicate (`.claude/`, `.cursor/`, `.aider/`, …) e
> implementano i ruoli §2. **Multi-adapter v2.13**: scaffolding parallelo di adapter
> multipli supportato; ogni adapter ha un manifest formale (§12.x).

## §0 — Identità & versione
Pattern version: **2.17**.
Origine: llm-wiki (Karpathy) + estensione PM/Arch + memory tree cross-conversazione + adapter `thin agents, fat skills` + execution layer L5 + topology + stack modes + VCS integration + sync adapters multi-sorgente (PDF, Figma, **repo esistenti**, **knowledge graph**) + publisher adapters multi-target (GitHub, GitLab, Jira, Linear, …) + parallel scheduler basato su DAG di dipendenze frontmatter + code quality review layer post-Develop con ruleset evolutivo stack-aware + multi-adapter scaffolding parallelo via registry manifest (v2.13) + **compression layer a due assi opt-in (output via Caveman, context via Graphify), consolidato in v2.15 con gate empirici Fase 1.5/3a riformulati come opt-in deferred (eseguibili a discrezione del derivatore su factory candidata, non bloccanti per il consolidamento)** + **FE Visual Oracle Integration opt-in (v2.17): variante Develop FE «Visual Verification» via skill `visual-oracle-protocol` + comando `/visual-oracle` + State Matrix nel DoD FE + ordering develop→visual-oracle→review; tutto opt-in via `factory.config.yaml.fe_correctness`, niente nuova invariante §7**.
Scope: knowledge-base eseguibile **e** (opzionale) produzione codice tramite dev-agent o consumo umano dei task; integrazione esplicita con git per layout monorepo/submodule/sibling/external; ingestione L1 da fonti eterogenee tramite sub-agent Sync dedicati (incluso reverse-engineering di repo esistenti via `repo-sync`); pubblicazione opzionale di L3/L4 su tool esterni di project tracking tramite sub-agent Publisher (provider-agnostic); orchestrazione parallela di operazioni indipendenti sui livelli L1→L5 con safety-by-default (single-committer e conflict detection su `code_path`); valutazione qualitativa del codice prodotto a valle di Develop (idiomaticità, design, robustezza) tramite Code Reviewer opzionale con loop evaluator-optimizer bounded.
Progetto host: **Soli Multi-Agents Factory** (`owner: soli92`, `language: it`).

## §1 — Modello a layer
- **L1 `raw/`** — input multi-sorgente. Artefatti tipici: PDF + estrazioni `.txt` + `images/` (da `sync-docs`); KB JSON strutturate (`.kb.json`) da `figma-sync`; futuri sub-agent (Notion, Confluence) seguono lo stesso contratto. **Immutabile** (solo il ruolo *Sync*, nei suoi N sub-agent per sorgente, scrive in `raw/` — §16).
- **L2 `wiki/`** — wiki llm-style con `log.md` append-only. Unico autore: ruolo *Analyst* (`wiki-keeper`). Layout karpathy-style: `sources/concepts/entities/syntheses/runbooks/incidents/`.
- **L3 `management/`** — `kanban/EP-*/`, `roadmap.md`, `questions.md`. Autore: ruolo *PM*.
- **L4 `design_&_architecture/` + `management/kanban/**/TSK-*.md`** — autore: ruolo *Arch* (architettura) + *TPM* (task atomici).
- **L5 `<code_path>/`** — codice sorgente. `code_path` è configurabile in `factory.config.yaml` (default `./src/`) e **può puntare fuori dal repo** (path assoluto verso altro working tree). Autore: ruoli *Dev* (`be-dev`, `fe-dev`, `db-dev`, `qa-dev`) o sviluppatori umani — in base al routing §14. La **relazione VCS** fra L5 e factory repo è dichiarata in `factory.config.yaml.vcs.mode` (§15): `monorepo` (L5 dentro al factory repo), `submodule` (L5 come git submodule), `sibling` (L5 come clone separato), `external` (factory non conosce la topologia VCS), `none` (no L5).
- **`memory/`** — persistenza cross-conversazione (non un layer, ma side-channel).

Cascata: ogni layer è derivato dal precedente. L'aggiornamento di Lk rende Lk+1..L5 *stale*. Se `code_path` è esterno, la cascata si interrompe al boundary del repo: i dev-agent committano nel repo esterno, mentre `wiki/log.md` annota solo il fatto che è avvenuto un `develop` (puntatore al commit hash quando disponibile).

## §2 — Ruoli (responsabilità, non file)
Ogni runtime mappa questi ruoli ai propri costrutti (agenti, assistant, modes, …).

**Principio**: `wiki/` è **read-universal** (ogni agente la legge), **write-restricted** (solo `wiki-keeper` scrive contenuto; eccezioni puntuali sotto). Tutti gli agenti L3+ possono e devono leggere `wiki/` per contesto, anche se la loro citazione formale resta cascade (Arch cita storie, non concept; ma può aprire il concept per capire cosa significa).

| Ruolo | Legge | Scrive | Trigger |
|---|---|---|---|
| **Orchestrator** | tutto (read-only) | `memory/episodic/**`, `wiki/log.md`, **eccezione**: edit `status:` frontmatter di `wiki/**/*.md` (operazione `/promote`) | richiesta dashboard di stato; comando `/promote` |
| **Sync** (`sync-docs`, `figma-sync`, …) — un sub-agent per sorgente (§16) | input di propria competenza (PDF locali, URL/`file_key` Figma, …) | `raw/**` nel proprio scope di naming (§4): `sync-docs` → `*.txt` + `images/*-fig-NN.md`; `figma-sync` → `*.kb.json` + `images/*-frame-NN.{png,md}`. Tutti scrivono `raw/.extraction-manifest.json` (append-only per chiave) | nuovo input nella sorgente del sub-agent |
| **Analyst** (`wiki-keeper`) | `raw/**` (`.txt`, `.kb.json`, `images/**/*.md`), `raw/tech_stack.md`, `memory/**`, `wiki/**` (rilegge per cross-link + `wiki/gaps.md` all'inizio di ogni ingest) | `wiki/**` (escluso `query/`, `lint/`) + append `wiki/log.md` | L1 aggiornato OR gap aperti OR `heal-eligible` nel lint report (operazione `Heal`, §3) |
| **PM** (`product-manager`) | `wiki/**`, `memory/**` | `management/kanban/EP-*/**`, `management/{roadmap,questions}.md`, **append-only**: `wiki/gaps.md` + sezione `## Storie collegate` di pagine wiki impattate | L2 aggiornato |
| **Arch** (`lead-architect`) | `management/kanban/**`, `management/questions.md`, `raw/tech_stack.md`, `factory.config.yaml`, `memory/**`, **`wiki/**`** (contesto) | `design_&_architecture/**`, **append-only**: `wiki/gaps.md` + (opzionale, se `stack_mode: auto`) `raw/tech_stack.md.proposal` via skill `tech-scout` | L3 OK + gate questions resolved |
| **TPM** (`tpm`) | `design_&_architecture/**`, `management/kanban/**`, `raw/tech_stack.md`, `factory.config.yaml`, `memory/**`, **`wiki/**`** (contesto) | `management/kanban/**/TSK-*.md` (con campi `layer:` e `consumer:` derivati dal routing §14), `management/kanban/sprint.md`, **append-only**: `wiki/gaps.md` | L4 architettura OK |
| **Query** (`wiki-query`) | `wiki/**` (esclusivo) | `wiki/query/` (opt-out con `--ephemeral`) + append `wiki/log.md` | domanda NL |
| **Lint** (`wiki-lint`) | `wiki/**`, `management/kanban/**`, `design_&_architecture/**`, `factory.config.yaml` | `wiki/lint/` + append `wiki/log.md` | richiesta health check |
| **Dev** (`be-dev`, `fe-dev`, `db-dev`, `qa-dev`) — opzionali (topologia §14) | `management/kanban/**/TSK-*.md` (filtrato per `layer:` proprio + `consumer: agent`), `design_&_architecture/**`, `raw/tech_stack.md`, `factory.config.yaml`, `<code_path>/**`, **`wiki/**`** (contesto) | `<code_path>/**` (path da `factory.config.yaml`, può essere esterno al repo), **append-only**: `wiki/log.md` (entry `develop`), `wiki/gaps.md` (se gap), `management/kanban/**/TSK-*.md` **solo per `status:` e `updated:`** (handoff: `todo → in-progress → done`, MAI editare il corpo) | TSK con `consumer: agent` + `layer:` corrispondente + `status: todo` + dipendenze risolte; OR comando manuale `/dev <TSK-id>` |
| **Publisher** (`github-publisher`, `gitlab-publisher`, …) — opzionali (§17) — un sub-agent per provider | `management/kanban/EP-*/**`, `management/kanban/sprint.md`, `management/{roadmap,questions}.md`, `factory.config.yaml`, `memory/**` | **append-only**: `wiki/log.md` (entry `publish`); **modifica del solo `external_id:` frontmatter** di `EP-*/US-*/TSK-*.md` (mai del corpo); chiamate read+write verso provider esterno (GitHub Issues/Projects, GitLab Issues, Jira, Linear, …) via CLI/API dedicate | comando esplicito `/kanban-publish run` OR (in modalità auto, gate umano) trigger su nuovo TSK con `status: todo` + provider != `none` |
| **Code Reviewer** (`code-reviewer`) — opzionale (v2.12, §19) | TSK con `status: done` + `review_status: pending` (filtrato per `consumer: agent`), `<code_path>/**` (read-only — diff/file toccati), `code_quality/rules/**`, `factory.config.yaml`, `wiki/**` (contesto), `memory/**` | `code_quality/reports/**` (artefatti report machine-readable + digest umano-leggibile), **append-only**: `wiki/log.md` (entry `review TSK-ZZZ iter-N → <verdict>`); **modifica del solo `review_status:`/`review_iter:`/`review_report:` frontmatter** di `TSK-*.md` (mai del corpo); opzionale (gate umano, modalità evolutiva): `code_quality/rules/emergent/**` come bozze candidate | TSK con `consumer: agent` + `status: done` + `review_status: pending` + `code_quality.enabled: true`; OR comando esplicito `/review <TSK-id>` |

## §3 — Operazioni canoniche (verbi)
- **Ingest** = transizione L1 → L2 eseguita da *Sync* (uno o più sub-agent, §16) + *Analyst*. L'*Analyst* legge artefatti `.txt` (PDF), `.kb.json` (Figma), o futuri shape registrati nel manifest. Per batch ≥ 3 nuovi raw, l'*Analyst* può delegare la fase di analisi ad agenti subordinati paralleli; la scrittura su `wiki/` resta serializzata sull'*Analyst* (single-committer). Append a `wiki/log.md`.
- **Query** = domanda NL → risposta sintetizzata leggendo solo `wiki/`. Append a `wiki/log.md`.
- **Lint** = health check strutturale di L2+L3+L4. Append a `wiki/log.md`.
- **Plan** = transizione L2 → L3 eseguita dal *PM*.
- **Design** + **Execute** = transizione L3 → L4 eseguita da *Arch* poi *TPM*. *Arch* può invocare la skill `tech-scout` se `stack_mode: auto` (produce `raw/tech_stack.md.proposal` — mai overwrite di `raw/`, gate umano).
- **Develop** = transizione L4 → L5 eseguita da un ruolo *Dev*. Consuma un singolo TSK con `consumer: agent` + `layer:` corrispondente. Scrittura su `<code_path>/**`. Append a `wiki/log.md` (marker `develop TSK-ZZZ → <commit-hash o path>`). Mai edit del corpo del TSK; solo `status:` (`todo → in-progress → done`). A Fase 5 il dev-agent invoca la skill `vcs-handoff` (§15) che gestisce il bump del submodule ref, il branch per-TSK, o solo il logging — in base a `vcs.mode`.
- **Promote** = transizione di `status:` di una pagina wiki (`draft → review → approved`).
- **Heal** = ciclo evaluator-optimizer vincolato su ERROR meccanici flaggati dal *Lint* come `heal-eligible`. Opt-in, gated, bounded (max 3 iterazioni). Eseguito dall'*Analyst* in modalità heal (single-committer §7 r.12 preservato). Whitelist chiusa: mai correzione di WARNING né di ERROR fuori categoria. Append a `wiki/log.md` con marker `heal-iter-N`.
- **Propagate** = riconciliazione downstream quando *Analyst* chiude un gap che cita una `Q_NNN`. Skill `propagate-resolution`: trova le US con `blocked_by: [Q_NNN]` ancora in lista, appende a `wiki/log.md` un marker `reconcile-needed: US-XXX → Q_NNN closed`. L'umano (o il PM in pass successivo) sblocca le storie.
- **Tech-scout** = proposta automatica di stack tecnologico (skill omonima, v2.7). Invocabile da *Arch* o on-demand. Output: `raw/tech_stack.md.proposal` con citazioni a fonti web datate. Mai auto-applicato: gate umano per promuovere `.proposal` → `raw/tech_stack.md`.
- **Publish** = transizione L3/L4 → tool esterno di project tracking (v2.10). Eseguita da un sub-agent *Publisher* (§17). Mirror **push-only** di EP/US/TSK/sprint verso GitHub Issues, GitLab Issues, Jira, Linear, … in base a `factory.config.yaml.kanban_publish.provider`. Idempotente: l'`external_id:` frontmatter dell'artefatto pubblicato evita duplicazioni. Mai bidirectional in v2.10 (single source of truth resta `management/kanban/**`, §8). Append a `wiki/log.md` (marker `publish <provider> <count>`). Mai modifica del corpo dei TSK; solo aggiornamento di `external_id:` e `updated:` nel frontmatter.
- **Visual Verification** = variante di **Develop FE** (v2.17, opt-in). Quando `factory.config.yaml.fe_correctness.enabled: true` AND `TSK.layer: fe`, il `dev-protocol` esegue un sub-step **Fase 4-bis — Visual Verification** (skill `visual-oracle-protocol`) che chiude il loop visivo (render headless + screenshot multi-viewport/tema + critica) **prima** di marcare il TSK `status: done`. Esito: `pass` → `visual_status: pass` + TSK done; `conditional` → loop fe-dev bounded (`fe_correctness.max_iterations`, default 3); `reject` → `visual_status: reject` + gate umano. A flag spento la Fase 4-bis è no-op (comportamento identico a v2.16). Vedi ADR-012 §G + ADR-013 Punto 1.
- **Review** = transizione L5 → L5 post-Develop (v2.12, §19). Eseguita dal ruolo *Code Reviewer*. Legge diff/file toccati dal TSK appena chiuso (`status: done` + `review_status: pending`), invoca skill `code-review-protocol` (Stack Detector → 3 passate specializzate → Aggregator → Feedback Router). Produce `code_quality/reports/<TSK-id>-iter-<N>.{json,md}` e un verdict `pass | conditional | reject`. `pass` → TSK `review_status: passed`, ciclo chiuso. `conditional` → task_package machine-readable consegnato al dev-agent corrispondente, re-Develop dello stesso TSK con `review_iter+=1` (bounded da `max_iterations`, default 3; vedi §19.6 R.Q4). `reject` → gate umano (§7 r.16). Append a `wiki/log.md` entry `review TSK-ZZZ iter-N → <verdict>`. Mai modifica del corpo del TSK; solo `review_status:`/`review_iter:`/`review_report:` nel frontmatter.

## §4 — Naming conventions
| Artefatto | Pattern |
|---|---|
| PDF | `YYYY-MM-DD-<nome>.pdf` (e `.txt` corrispondente) |
| Figura PDF | `YYYY-MM-DD-<nome>-fig-NN.md` |
| KB Figma | `raw/YYYY-MM-DD-figma-<file-key>.kb.json` (prodotto da `figma-sync`, §16) |
| Frame Figma | `raw/images/YYYY-MM-DD-figma-<file-key>-frame-NN.md` (companion stub; binario `.png` opzionale stesso slug) |
| Source page | `wiki/sources/<kebab-slug>.md` |
| Concept page | `wiki/concepts/<kebab-slug>.md` |
| Entity page | `wiki/entities/<kebab-slug>.md` |
| Synthesis page | `wiki/syntheses/<kebab-question>.md` |
| Runbook | `wiki/runbooks/<kebab-slug>.md` |
| Incident | `wiki/incidents/YYYY-MM-DD-<kebab-slug>.md` |
| Epica | `management/kanban/EP-XXX-<slug>/EP-XXX.md` |
| Storia | `management/kanban/EP-XXX-<slug>/US-YYY-<slug>/US-YYY.md` |
| Task | `management/kanban/EP-XXX-<slug>/US-YYY-<slug>/TSK-ZZZ.md` |
| Memoria episodica | `memory/episodic/YYYY-MM-DD-HH-MM-<slug>.md` |
| Memoria semantica | `memory/semantic/<slug>.md` |
| Memoria procedurale | `memory/procedural/<slug>.md` |
| Tech-stack proposal | `raw/tech_stack.md.proposal` (effimero, gate umano per promote → `raw/tech_stack.md`) |
| Repo spec (v2.12) | `raw/YYYY-MM-DD-repo-<slug>.md` (prodotto da `repo-sync`, §16); opzionale companion `raw/images/YYYY-MM-DD-repo-<slug>-tree.md` |
| Graph summary (v2.14 Fase 2) | `raw/YYYY-MM-DD-graph-<slug>.md` (prodotto da `graphify-sync`, §16; riepilogo umano-leggibile: god nodes, surprising connections, confidence breakdown) |
| Graph side-channel (v2.14 Fase 2) | `.graphify-state/code_paths/<slug>/{graph.json,GRAPH_REPORT.md,last_full_rebuild.txt}` (non versionato in git, rebuildable da `<code_path>`) |
| Code quality rule (v2.12) | `code_quality/rules/<tier>/<rule_id>.md` con `<tier> ∈ {canonical, emergent, team-specific}` e `<rule_id>` in formato dotted `{language}.{framework}.{category}.{specific}` |
| Code quality report (v2.12) | `code_quality/reports/<TSK-id>-iter-<N>.json` (machine-readable) + `code_quality/reports/<TSK-id>-iter-<N>.md` (digest umano-leggibile) |
| Factory config | `factory.config.yaml` (singolo file, root del repo) |

Slug: lowercase, spazi→`-`, rimuovi `()/'`, max 40 char. XXX/YYY/ZZZ = 3 cifre zero-padded.

## §5 — Frontmatter (minimo necessario, deduci dal path quando possibile)
- **Wiki page:** `type`, `sources`, `status` (`draft|review|approved`)
- **Epica:** `id`, `title`, `status`, `priority`, `confidence`, `confidence_rationale`, `wiki_pages`, `created`, **opzionale (v2.10)**: `external_id` (`<provider>:<id>` se pubblicata su tool esterno via Publisher, §17), **opzionale (v2.11)**: `depends_on` (lista EP prerequisite, input per scheduler §18), **opzionale (v2.16)**: `risk_classification` (blocco strutturato: `tier`, `premortem_ref`, `reviewed_by` — vedi paragrafo dedicato sotto)
- **User Story:** `id`, `title`, `role`, `priority`, `status`, `wiki_page`, `blocked_by` (`epic` deducibile dal path), **opzionale (v2.10)**: `external_id`, **opzionale (v2.11)**: `depends_on` (lista US prerequisite), **opzionale (v2.16)**: `risk_classification` (idem EP)
- **Task:** `id`, `sprint`, `layer` (`be|fe|db|qa|infra`), `consumer` (`agent|human`), `priority`, `estimate`, `status` (`story`/`epic` deducibili dal path; `team` deprecato in v2.7 — usa `layer`), **opzionale (v2.10)**: `external_id`, **opzionale (v2.11)**: `depends_on` (lista TSK prerequisiti), `blocked_by` (lista `Q_NNN` hard aperte, simmetrico US), `code_path` (lista glob L5 toccati in scrittura — input per conflict detection §18; in multi-repo v2.12 i glob sono *relativi al target*), **opzionale (v2.12, §19)**: `review_status` (`pending|passed|conditional|rejected`, default `pending` se `code_quality.enabled: true`; assente se disabilitato), `review_iter` (integer, default `0`), `review_report` (path al report più recente in `code_quality/reports/`), **opzionale (v2.12, multi-repo §13)**: `target` (nome di un'entry in `factory.config.yaml.code_paths`; required se la combinazione `(routing.<layer>, code_paths)` produce ambiguità — vedi §13), **opzionale (v2.16)**: `risk_classification` (idem EP), **opzionale (v2.17, §G ADR-012)**: `visual_status` (`pending|pass|conditional|reject`, single-writer skill `visual-oracle-protocol`; default implicito assente = `pending`), `interaction_test_spec` (path test Playwright, scritto da TPM), `visual_reference` (path frame Figma/screenshot, scritto da TPM) — vedi paragrafo dedicato sotto
- **Figura:** `source_pdf`, `page`, `figure_number`, `type`
- **Memoria:** `type` (`episodic`/`semantic`/`procedural`), `created`, `tags`

Regola: `id` e `status` (dove applicabile) sono **sempre obbligatori**; tutto il resto deducibile dal path va rimosso.

**`external_id` (v2.10)**: campo frontmatter scritto **solo** dal sub-agent Publisher (§17) corrispondente al provider configurato in `factory.config.yaml.kanban_publish.provider`. Forma canonica: `<provider>:<id>` dove `<provider>` ∈ {`github`, `gitlab`, `jira`, `linear`, …} e `<id>` è l'identificatore esterno (numero issue, key Jira, UUID Linear, …). Esempi: `github:1234`, `gitlab:567`, `jira:PROJ-89`, `linear:abc-uuid-…`. Funzione: idempotenza al re-publish (Publisher fa UPDATE se presente, CREATE se assente). PM/TPM/Dev **non scrivono mai** questo campo (è scope esclusivo del Publisher, vedi §17 invariante di isolamento).

**`depends_on` / `blocked_by` / `code_path` (v2.11)**: campi che codificano le dipendenze causali e gli scope di scrittura per il parallel scheduler (§18). Semantica:
- `depends_on: [<id>, ...]` — lista di artefatti dello stesso tipo (EP→EP, US→US, TSK→TSK) che DEVONO essere almeno in stato avanzato (`done` per TSK; `ready`/`done` per US; `in-progress`/`done` per EP) prima di poter procedere su questo artefatto. **Hard dependency**: gate non bypassabile. La semantica per tipo è formalizzata in §18.
- `blocked_by: [Q_NNN, ...]` — equivalente al campo omonimo su US (§5 v2.6): lista di `Q_NNN` con `Bloccante: hard` aperte che bloccano l'artefatto. Già definito per US in v2.6; **esteso a TSK in v2.11**.
- `code_path: ["<glob>", ...]` — solo TSK. Lista di glob in `<code_path>/**` che il TSK prevede di toccare in scrittura. Lo scheduler la usa per il **conflict-detection**: due TSK con intersezione non vuota di glob non sono parallelizzabili (race su file). Lista vuota = "scope sconosciuto" → trattamento conservativo (serializzante). Glob, non path assoluti; esempio: `["src/auth/**", "tests/integration/auth/**"]`.

PM, Arch, TPM scrivono `depends_on` quando producono l'artefatto (v2.11). I dev-agent **non lo modificano** (read-only su questo campo, come sui `## Dependencies` del body). Drift fra `depends_on` frontmatter e sezione `## Dependencies` body → warning di `wiki-lint` (frontmatter prevale).

**`review_status` / `review_iter` / `review_report` (v2.12)**: campi del solo TSK, scritti **esclusivamente** dal Code Reviewer (§2). Semantica:
- `review_status` — `pending` (TSK chiuso da Develop, in attesa di Review), `passed` (verdict `pass` dell'aggregator §19.3), `conditional` (verdict `conditional` — feedback loop attivo, dev-agent re-invocato), `rejected` (verdict `reject` o `max_iterations` raggiunto — gate umano §7 r.16).
- `review_iter` — contatore del round di review/fix corrente. Incrementato dal Code Reviewer prima di consegnare un nuovo `task_package` al dev-agent. Bounded da `code_quality.max_iterations` (default 3).
- `review_report` — path relativo al report più recente in `code_quality/reports/`. Esempio: `code_quality/reports/TSK-042-iter-2.md`.

PM/TPM/Dev **non scrivono mai** questi campi (scope esclusivo del Code Reviewer §19.6 R.Q2). Drift fra `review_status` frontmatter e ultima entry `review` in `wiki/log.md` → warning di `wiki-lint` (frontmatter prevale).

**`visual_status` / `interaction_test_spec` / `visual_reference` (v2.17)**: tre campi del solo TSK, **opzionali e additivi**, introdotti dal Visual Oracle (EP-005, ADR-012 §A/§G). Backward compat totale: assenza dei campi = comportamento v2.16 identico.
- `visual_status` — enum `pending | pass | conditional | reject`. **Single-writer: solo la skill `visual-oracle-protocol`** (analogo a `review_status:` di CQRL, R.Q2). Dev-agent, PM, TPM **non lo scrivono mai** a runtime. **Default implicito**: campo assente = `pending`. Letto da: `code-review-protocol` Fase 0 (gating precondition, §19 + ADR-009/ADR-013) e Oracle Pre-Check dell'orchestrator.
- `interaction_test_spec` — path (relativo al code_path) a un file di test Playwright. Scritto dal **TPM** in fase di taskizzazione (input di specifica, non output di runtime). Letto dalla Fase 3-bis di `visual-oracle-protocol`.
- `visual_reference` — path (relativo al repo) a un frame Figma o screenshot reference. Scritto dal **TPM** in fase di taskizzazione. Letto dal critic (Fase 4) di `visual-oracle-protocol` come specifica visiva contro cui criticare.

**`target` (v2.12, multi-repo)**: in setup multi-repo (`code_paths` con più di una entry per uno stesso `layer`), il TSK DEVE dichiarare il `target` per disambiguare a quale repo punta la scrittura. Scritto dal *TPM* quando produce il TSK; mai modificato a runtime dal Dev. Semantica di risoluzione:
- `target: <name>` valorizzato → cerca `code_paths[name == target]`. Se non trovato → ERROR del dev-agent.
- `target:` assente → filtra `code_paths` per `<layer> ∈ entry.layers`. Se 1 match → usa quello (auto-derive). Se ≥ 2 match → ERROR (TPM doveva valorizzare `target`). Se 0 match → ERROR di config (nessun repo dichiara questo layer).
- Backward compat: se `code_paths` ha una sola entry o `code_path` (singolare, legacy v2.11-) è in uso, `target:` è opzionale e ignorato.

**`risk_classification` (v2.16)**: blocco opzionale su EP/US/TSK con 3 sotto-campi. Introdotto dal pattern Premortem (operazione opzionale §3, vedi `.claude/skills/premortem-protocol.md`). Schema:

```yaml
risk_classification:
  tier: <enum>                 # required se il blocco è presente
  premortem_ref: <path#anchor> # opzionale
  reviewed_by: [<handle>, ...] # opzionale, lista
```

| Campo | Tipo | Required | Esempio |
|---|---|---|---|
| `tier` | enum (6 valori sotto) | sì se blocco presente | `tiger-launch-blocking` |
| `premortem_ref` | string `<path>#<anchor>` verso `management/risk-registry.md` | no | `management/risk-registry.md#pm-2026-05-29-EP-042` |
| `reviewed_by` | list[string] di handle `@user` o nomi team | no | `["@soli92", "@arch-team"]` |

Enum legale per `tier` (6 valori): `tiger-launch-blocking | tiger-fast-follow | tiger-track | paper-tiger | elephant | high-impact` (quest'ultimo è il catch-all per "alto impatto ma premortem non ancora eseguita").

Esempi positivi:

```yaml
# EP cross-cutting, alto impatto, premortem eseguita
risk_classification:
  tier: tiger-launch-blocking
  premortem_ref: management/risk-registry.md#pm-2026-05-29-EP-042
  reviewed_by: ["@soli92"]

# US con incertezza ma senza premortem formale
risk_classification:
  tier: high-impact
```

Esempi negativi (malformati, riconoscibili a colpo d'occhio):

```yaml
risk_classification:
  tier: maybe-risky        # ← tier non in enum legale
risk_classification: {}    # ← blocco vuoto: tier è required
tier: tiger-track          # ← tier fuori dal blocco risk_classification
```

**Default semantico**: assenza del blocco = `tier: untagged` (concetto, **non** un valore scritto nel file). Garantisce backward compat totale (R.P3 opt-in v2.16): una factory che non aggiunge mai il blocco si comporta identica a v2.15. Validato da `wiki-lint` Check 4m (**WARNING-only**, mai ERROR). PM/Arch/TPM possono scriverlo; il pattern premortem lo *suggerisce* ma non lo applica mai in autonomia (R.P1). Vedi `wiki/concepts/factory-premortem-integration.md` per il design doc.

I glob in `code_path` (TSK) sono **relativi al target risolto**: lo scheduler conflict-detection (§18.4 R.S2) opera su `(target, glob)` — due TSK con target diversi non confliggono mai (filesystem disgiunti).

## §6 — Grammatica delle citazioni
- Citazione fonte testuale: `[^src: <path-relativo>.{md,txt} §<sezione>]` su ogni claim ≥ 20 parole (la sezione è un header markdown del file citato).
- Citazione fonte strutturata (JSON, v2.9): `[^src: <path-relativo>.kb.json §<dotted-path>]` dove `<dotted-path>` segue la convenzione (a) chiavi punto-separate (`§project.name`, `§tokens.colors`), (b) indice positivo per array (`§screens[0]`), (c) selettore per chiave (`§components[name=Button]`). Solo notazioni leggibili a mano; vietato JSONPath complesso o JMESPath.
- Link interno wiki: `[[nome-pagina-senza-estensione]]`, **mai** path relativi `../../`.
- Citazione codice (factory): `[^code: <path>:<line>]`.
- Citazione codice prodotto (L5):
  - `vcs.mode: monorepo` → `[^src5: <code_path>/<path>:<line>]` (path relativo, commit factory).
  - `vcs.mode: submodule` → `[^src5-sub: <submodule_path>/<path>:<line> @ <commit-hash>]` (path interno al factory repo, commit del submodule).
  - `vcs.mode: sibling` o `external` → `[^src5-ext: <abs-path>:<line> @ <commit-hash>]` (path assoluto, commit del repo esterno).
- Claim senza citazione = claim invalido (segnalato dal *Lint*, mai bloccato deterministicamente).

## §7 — Regole inviolabili (18)
1. **L1 read-only** (eccetto *Sync*).
2. **Zero invenzione.** Info assente → `wiki/gaps.md` o `management/questions.md`.
3. **Citazione obbligatoria** su ogni claim non triviale.
4. **Wikilink** per link interni, mai path relativi.
5. **`wiki/log.md` append-only.** Stesso vincolo per `wiki/gaps.md` e `wiki/incidents/`.
6. **Report preliminare e STOP** prima di scrivere file in batch.
7. **Update non distruttivo** su pagine `review|approved`: aggiungi `## Aggiornamenti (vYYYY-MM-DD)`.
8. **Scope di scrittura chiuso** per ruolo (§2). I dev-agent scrivono solo `<code_path>/**` + `status:`/`updated:` del proprio TSK; mai design o wiki.
9. **Gate L4 graduato (`blocking_level`).** `Q_NNN` con `blocking_level: hard` aperta blocca le US dipendenti; `soft` consente di procedere annotando `pending_clarification`. Idem per L5: TSK con `consumer: agent` e dipendenze `blocked_by` aperte (hard) → STOP del dev-agent corrispondente.
10. **`raw/tech_stack.md` priorità assoluta.** SAML/OIDC/SOAP citati non si sostituiscono con alternative. La skill `tech-scout` propone, mai applica: scrive `.proposal`, l'umano promuove.
11. **`memory/` non è wiki/.** Persistenza cross-conversazione vive in `memory/`, mai mescolata con `wiki/log.md`.
12. **`wiki/` è read-universal**, **single-committer**. Solo l'*Analyst* committa su `wiki/`. Eccezioni: `## Storie collegate` (PM), `wiki/gaps.md` (L3+ append), `status:` frontmatter via `/promote` (Orchestrator), entry `develop` su `wiki/log.md` (dev-agent, append-only).
13. **Topology & consumer routing dichiarati**. Se esistono dev-agent in `.claude/agents/` (o equivalente per altri adapter), DEVE esistere `factory.config.yaml` con `topology:`, `code_path:`, e `routing:` valorizzati (§13). Un dev-agent può rifiutarsi di operare se il TSK non ha `layer:` + `consumer:` espliciti.
14. **VCS dichiarato** (v2.8). Se `code_path:` è valorizzato, DEVE esistere `vcs.mode:` in `factory.config.yaml` (`monorepo | submodule | sibling | external | none`). Nessuna operazione `git submodule add|update`, `git clone`, `git push`, `git commit --amend`, o force-push viene MAI eseguita automaticamente: la skill `vcs-handoff` propone, l'umano conferma (gate non bypassabile per scritture VCS distruttive o cross-repo).
15. **Cross-tool publish gate umano** (v2.10). Se `kanban_publish.provider ≠ none` in `factory.config.yaml`, il sub-agent Publisher (§17) deve mostrare in chat il piano di pubblicazione (lista di CREATE + UPDATE proposti con conteggi per tipo) e **attendere conferma esplicita** prima di qualsiasi chiamata write sul provider esterno. Mai operazioni `delete`/`close` automatiche su issue/milestone esterne: solo `create` e `update`. Mai pubblicare più di `kanban_publish.batch_limit` artefatti in un singolo run senza ulteriore gate (default `batch_limit: 10`). Token di autenticazione **solo da variabile d'ambiente** (mai committati nel repo; nome var dichiarato in `kanban_publish.auth_env`).
16. **Code review verdict `reject` = gate umano** (v2.12, §19). Mai auto-revert del codice, mai auto-close/auto-merge del TSK, mai riapertura automatica del Develop. Quando il Code Reviewer emette verdict `reject` (o `max_iterations` viene raggiunto senza convergenza), il TSK resta `status: done` ma con `review_status: rejected`: l'umano decide il next step (re-Develop manuale con istruzioni, accept-as-is con override documentato in `wiki/incidents/`, o rollback del codice). `code_quality.max_iterations` (default 3) è invariante non bypassabile a runtime. No-progress detection (due iterazioni con stesso set di `rule_id` violate) e regression detection (finding nuovi in file non toccati dalla fix) accelerano l'escalation **prima** di raggiungere il cap.
17. **Sync read-only verso la sorgente** (v2.9 generalizzata in v2.12). Nessun sub-agent Sync (§2 + §16) modifica MAI la propria fonte di estrazione: `sync-docs` non riscrive i PDF, `figma-sync` non muta il file Figma (solo lettura via MCP/REST), `repo-sync` **non aggiunge né modifica file nel repo scansionato** — in particolare mai aggiungere `factory.config.yaml`, adapter `.claude/`, o file infrastrutturali al repo esterno. L'output del Sync vive esclusivamente nel proprio scope di `raw/**` + `raw/.extraction-manifest.json` (§16). Una factory esistente che ingerisce sé stessa via `repo-sync` (modalità reflective) resta legittima: la regola distingue la *sorgente di scansione* dall'*output di scansione*.
18. **Compression layer mai sugli artefatti** (v2.14, §20). Se `compression.output.enabled: true` in `factory.config.yaml`, la compressione (Caveman) si applica **solo** ai canali di messaging agent-to-agent / agent-to-tool / tool-to-agent. **Mai** sugli artefatti scritti su filesystem (`wiki/**`, `management/kanban/**`, `<code_path>/**`, `design_&_architecture/**`, `code_quality/**`, `memory/**`), **mai** sull'output verso l'utente finale, **mai** sul flow di `propagate-resolution` (§3 — coerenza referenze cross-page). Questi invarianti (`to_user`, `to_artifact`, `propagate_resolution` → sempre `off`) non sono mai overridabili neppure in `policy_profile: custom`. Vedi §20.4 R.C1–R.C6 per il dettaglio.

## §8 — State derivation (single source of truth)
Lo stato del progetto si deduce SOLO da:
- Filesystem (presenza/assenza di file e cartelle, **inclusa la presenza di agenti dev in `.claude/agents/`** che codifica la topologia).
- `wiki/log.md` (ultima entry per tipo di operazione).
- `memory/episodic/` (ultimo run rilevante).
- Data modifica file (`git log` o `stat`).
- `factory.config.yaml` (configurazione, non stato — vedi distinzione sotto).

**Vietato:** `project_manifest.json` o qualsiasi file di **stato** scritto a mano (si desincronizza).
**Vietato:** doppia source-of-truth (es. `sprint.md` *e* cartelle TSK — `sprint.md` è view generata).

**Distinzione config vs stato**: `factory.config.yaml` è **configurazione utente** (topology, code_path, routing, stack_mode) — cambia raramente, sotto controllo umano. Non descrive *cosa è stato fatto* (stato), descrive *come la factory è configurata* (config). Lo stato resta derivato dal filesystem + log.

## §9 — Memoria cross-conversazione
- **`memory/episodic/`** — record narrativo del run. Scritto dall'*Orchestrator*. Letto dai run successivi per continuità.
- **`memory/semantic/`** — fatti consolidati cross-progetto. Promossi da episodic dopo validazione umana.
- **`memory/procedural/`** — playbook riutilizzabili. Curati a mano.

Distinto da `wiki/log.md` (narrazione operativa) e da `wiki/incidents/` (post-mortem operativi).

## §10 — Wiki maintenance & feedback loop

`wiki/` è la **source of truth** del progetto. Per restare tale deve essere:

1. **Accessibile a tutti** (read-universal). Anche i dev-agent leggono concept/entity/synthesis per contesto, ma citano cascade (TSK/ADR, non concept direttamente).
2. **Manutenuta con disciplina stringente** (single-committer). Solo `wiki-keeper` committa contenuto. Eccezioni (`## Storie collegate` PM, `status:` `/promote`, `develop` entry su `wiki/log.md`) sono operazioni meccaniche.
3. **Aggiornabile via feedback loop**. Agenti L3+ (PM, Arch, TPM, **dev**) che scoprono un gap lo formalizzano in `wiki/gaps.md`.

### Wiki feedback loop (canale formale per gap discovery)

`wiki/gaps.md` è **append-only condiviso in scrittura** fra PM, Arch, TPM, dev-agent, wiki-query. Formato gap:

```markdown
## YYYY-MM-DD HH:MM — <slug-gap>
**Origine:** <agente> @ <artefatto in lavorazione>
**Gap:** <cosa manca in wiki/>
**Sospetta fonte:** <raw da ingerire | "nessuna fonte chiara, serve nuovo raw">
**Impatto:** <quale produzione è frenata>
```

Ciclo: **Apertura** (L3+ → append `wiki/gaps.md`), **Pickup** (`wiki-keeper` legge in Fase 0), **Chiusura** (`**Risolto:** YYYY-MM-DD — [[<pagina>]]` + append `wiki/log.md` + `propagate-resolution`).

### Eventi che innescano un aggiornamento wiki/

| Evento | Trigger | Chi | Cosa |
|---|---|---|---|
| Nuovo PDF in `raw/` | `/sync-docs` completato | `wiki-keeper` | Ingest L1→L2 |
| Nuovo Figma in `raw/` (v2.9) | `/figma-sync <url>` completato; nuovo `*.kb.json` nel manifest | `wiki-keeper` | Ingest L1→L2 (touch many small files: una pagina per screen/component/flow/feature significativi) |
| Re-ingest stesso raw | `wiki/log.md` segnala precedente ingest | `wiki-keeper` | Append `## Aggiornamenti (vYYYY-MM-DD)` |
| Gap segnalato | append a `wiki/gaps.md` da L3+ (incluso dev) | `wiki-keeper` | Ingest mirato o nuova synthesis |
| Storia creata che impatta concept | PM completa US | `product-manager` | Append `## Storie collegate` |
| Risposta candidata a synthesis | `wiki-query` produce risposta ri-askable | `wiki-keeper` | Promote `wiki/query/<file>.md` → `wiki/syntheses/` |
| Promozione status | `promote` | `orchestrator` | Modifica `status:` + `updated:` |
| Auto-promotion suggerita | concept page citata da ≥ 2 US committed/in-progress | `orchestrator` (suggerimento `/run`) | Mai auto-promote |
| Gap chiuso che cita `Q_NNN` | `wiki-keeper` chiude → `propagate-resolution` | `wiki-keeper` (append-only log) | Marker `reconcile-needed: US-XXX` |
| Develop completato | dev-agent chiude TSK → `dev-handoff` | `<layer>-dev` | Append `wiki/log.md`: `develop TSK-ZZZ → <commit-hash o path>` |
| Kanban pubblicato (v2.10) | `/kanban-publish run` completato | `<provider>-publisher` | Append `wiki/log.md`: `publish <provider> created=<N> updated=<M>`. Lista degli artefatti con nuovo `external_id` |
| Lint findings | `wiki-lint` segnala errori | umano (mai auto-fix) | Eventualmente invoca `wiki-keeper` |

### Invarianti di manutenzione

- **Append-only** su `wiki/log.md`, `wiki/gaps.md`, `wiki/incidents/`.
- **Non distruttivo** su pagine `review`/`approved`.
- **Touch many small files**: un ingest sano = 5–15 piccole pagine.
- **Flag, don't resolve**: contraddizioni → `## Contradictions`.
- **Citation chain integrity**: ogni claim L3+L4+L5 traccia fino a `raw/` via `wiki/`.

## §11 — Standards as constraints (tenant-driven)
Quando un raw cita uno standard normativo (SPID, OIDC, OAuth2, SAML, eIDAS, FHIR, GDPR, HL7, ISO/IEC, RFC numerati), il `lead-architect` deve trattarlo come **vincolo verbatim** e produrre un ADR che lo adotta esplicitamente. La skill `tech-scout`, in modalità `auto`, deve rispettare gli standard già fissati in `raw/tech_stack.md` e in `raw/**` (non li sostituisce, anche se il mercato 2026 offre alternative "migliori").

## §12 — Adapter (runtime-specific)

Ogni adapter implementa i ruoli §2 con i costrutti del proprio runtime. **In v2.13** il
contratto è formalizzato con un **manifest** per ciascun adapter (vedi §12.1) che
abilita lo scaffolding multi-adapter parallelo al bootstrap.

### §12.0 — Adapter registry

Il **registry degli adapter** vive in `adapters/<name>/` al root del repo
meta-framework. Ogni sub-folder contiene il manifest + i template + un README. La
factory generata può ospitare uno o più adapter (es. `.claude/` e `.cursor/`
coesistenti) — la scelta è fatta al bootstrap o aggiunta a runtime.

Adapter attualmente disponibili (v2.13):

| Adapter | Folder runtime | Stato | Registry path |
|---|---|---|---|
| Claude Code | `.claude/` | reference completo (default) | `adapters/claude/` (manifest only; il `.claude/` reale del meta-framework è la fonte) |
| Cursor | `.cursor/` | full v2.13 | `adapters/cursor/` (template + manifest) |
| Aider | `.aider/` | full v2.13 | `adapters/aider/` (template + manifest) |
| OpenAI Assistants | `.openai/` | partial v2.13 (manifest + setup.py stub) | `adapters/openai/` |
| Gemini Code Assist | `.gemini/` | manifest-only v2.13 | `adapters/gemini/` |
| ChatGPT (Custom GPT / file tools) | `.chatgpt/` | manifest-only v2.13 | `adapters/chatgpt/` |

**Più adapter possono coesistere** nella stessa factory: condividono `raw/`, `wiki/`,
`management/`, `design_&_architecture/`, `memory/`, `code_quality/`,
`factory.config.yaml`, e `<code_path(s)>/`. Ogni adapter agisce sullo stesso state
filesystem; non c'è duplicazione di dati o operazioni.

### §12.1 — Manifest format

Ogni `adapters/<name>/manifest.yaml` dichiara:

```yaml
adapter_name: cursor                  # slug
adapter_folder: .cursor               # cartella in cui scaffoldare nella factory generata
runtime: cursor                       # nome runtime descrittivo
runtime_version_min: "0.45"           # versione minima del runtime supportata
maturity: full | partial | manifest-only  # stato del manifest
contract_version: 2.13                # versione PATTERN supportata

# Mapping dei costrutti runtime
mappings:
  agent:
    pattern_concept: "Agente specializzato (PATTERN §2 ruoli)"
    runtime_construct: "Cursor rules file con frontmatter `description` + `globs`"
    file_path_template: ".cursor/rules/{name}.mdc"
  skill:
    pattern_concept: "Procedura riusabile (PATTERN v2.3 'fat skills')"
    runtime_construct: "Cursor rules file in .cursor/rules/skills/"
    file_path_template: ".cursor/rules/skills/{name}.mdc"
  command:
    pattern_concept: "Slash command per invocazione esplicita"
    runtime_construct: "Cursor custom command"
    file_path_template: ".cursor/commands/{name}.md"
  tool_read:
    pattern_concept: "Lettura file"
    runtime_construct: "@<file>"
  tool_write:
    pattern_concept: "Scrittura file"
    runtime_construct: "Edit/Apply (Cursor built-in)"
  # ... altri mappings

# Lista template scaffoldabili
templates:
  agents:
    - { name: orchestrator, required: true,  path: .cursor/rules/orchestrator.mdc }
    - { name: wiki-keeper,  required: true,  path: .cursor/rules/wiki-keeper.mdc }
    - { name: be-dev,       required: false, path: .cursor/rules/be-dev.mdc, condition: "routing.be == agent" }
    # ...
  skills:
    - { name: ingest-protocol, required: true, path: .cursor/rules/skills/ingest-protocol.mdc }
    # ...
  commands:
    - { name: run,  required: true,  path: .cursor/commands/run.md }
    - { name: lint, required: true,  path: .cursor/commands/lint.md }
    - { name: dev,  required: false, path: .cursor/commands/dev.md, condition: "has_dev_agents" }
    # ...

# Skill che cambiano comportamento per questo runtime
runtime_overrides:
  parallel_dispatch:
    supported: false           # Cursor non ha multi-tool-call concept
    fallback: "sequential dispatch with manual confirmation"
  subagent_fanout:
    supported: false
    fallback: "manual user invocation of separate agents"

# Note operative
notes: |
  Cursor (≥0.45) supporta `.cursor/rules/*.mdc` con frontmatter `description` +
  `globs`. Adatta gli agenti del PATTERN come rules con `description` che spiega
  quando attivare la rule. I sub-agent (Agent tool di Claude Code) non hanno
  equivalente diretto in Cursor; emulati come "rules condizionali" che l'utente
  invoca manualmente con @.
```

### §12.2 — Multi-adapter coexistence (R.A1-R.A6)

Invarianti per la coesistenza di adapter multipli nella stessa factory:

- **R.A1 — Isolamento di cartella**: ogni adapter scrive **solo** nel proprio
  `adapter_folder` (es. `.claude/` per Claude Code, `.cursor/` per Cursor). Mai
  scritture cross-adapter (es. `.cursor/` rules che modificano `.claude/agents/`).
- **R.A2 — State filesystem condiviso**: tutti gli adapter leggono e scrivono lo
  stesso `wiki/`, `management/`, `raw/`, `memory/`, ecc. (i layer L1-L4 + memoria +
  code_quality side-channel). Le operazioni canoniche §3 hanno la stessa semantica
  per ogni adapter — cambia solo il *costrutto* di invocazione.
- **R.A3 — Single-committer preservato**: la regola §7 r.12 (wiki/ single-committer)
  vale globalmente, non per-adapter. Solo wiki-keeper (in qualunque adapter) scrive
  `wiki/`. Se l'utente invoca `wiki-keeper` da `.claude/` E da `.cursor/`
  contemporaneamente, è responsabilità sua serializzare.
- **R.A4 — Manifest immutabile a runtime**: il manifest di un adapter cambia solo
  via release PATTERN nuove. La factory generata non modifica il manifest.
- **R.A5 — Adapter aggiungibile a runtime**: l'utente può aggiungere un adapter
  dopo il bootstrap eseguendo `bootstrap-multiadapter-protocol` standalone (con il
  manifest target) — senza ri-scaffoldare l'intera factory.
- **R.A6 — Agent-agnostic preservato**: PATTERN.md, `factory.config.yaml`, e i
  layer L1-L5 NON contengono mai riferimenti a tool/costrutti specifici di un
  runtime. Tutto ciò che è runtime-specific vive negli adapter. Questo è
  l'invariante che rende il pattern truly portable.

### §12.3 — Configurazione adapter in `factory.config.yaml`

Nuovo blocco `adapters:` (v2.13) per dichiarare gli adapter installati:

```yaml
adapters:
  - name: claude       # adapter attivo (deve corrispondere a una cartella `<adapter_folder>`)
    folder: .claude
    maturity: full
  - name: cursor       # opzionale, secondo adapter
    folder: .cursor
    maturity: full
  # ...
```

In single-adapter (caso comune), una sola entry. In multi-adapter, N entry.
Backward compat: se `adapters:` è assente, la factory assume `[{name: claude, folder: .claude, maturity: full}]` (compatibile con v2.12 e precedenti).

### §12.4 — Principio di taglio adapter (v2.3 + multi-adapter v2.13)

- Gli **agenti** sono identità contrattuali (mappabili a sub-agent / rules / Assistants / role-prompts del runtime).
- Le **skill** sono procedure ricorrenti (mappabili a file markdown / rules / function tools / prompts).
- Una stessa procedura non è duplicata fra adapter — è scritta una volta come **contratto** in `<adapter>/skills/<name>.md` e implementata nel costrutto del runtime.
- Le **regole inviolabili §7** sono invariabili per tutti gli adapter (cambia il costrutto, non il contratto).

**Dev-agent opzionali (v2.7)**: i quattro dev-agent (`be-dev`, `fe-dev`, `db-dev`, `qa-dev`) esistono solo se la topologia li include. Bootstrap installa solo quelli necessari, **in ogni adapter installato**. A runtime, aggiungere/rimuovere il file agente cambia la topologia.

## §13 — Topology & consumer routing (nuovo in v2.7)

### Topologie supportate

La topologia è codificata dalla **presenza dei file agente** in `.claude/agents/` (o equivalente per altri adapter) + dal blocco `topology:` in `factory.config.yaml`.

| Topologia | Dev-agent presenti | Caso d'uso |
|---|---|---|
| `knowledge-only` | nessuno | Knowledge factory pura (Sync→Analyst), no planning, no execution |
| `plan-only` | nessuno | Default storico v2.6: arriva fino a TSK, consumer umano |
| `full-stack-agents` | `be-dev`, `fe-dev`, `db-dev`, `qa-dev` | Tutto agentico end-to-end |
| `hybrid-be-agents` | `be-dev`, `db-dev` | BE/DB agentici, FE/QA umani |
| `hybrid-fe-agents` | `fe-dev` | FE agentico, BE/DB/QA umani |
| `custom` | sottoinsieme arbitrario | Mix esplicito (es. solo `qa-dev` per autotest) |

### `factory.config.yaml` (schema, v2.8 + v2.12 multi-repo)

```yaml
# Configurazione factory — versione PATTERN e topologia
pattern_version: "2.12"
topology: full-stack-agents  # vedi tabella sopra

# === Code paths (L5) — v2.12 multi-repo ===========================================
# Lista di repository / path target. Ogni entry rappresenta uno scope di scrittura
# distinto (FE+BE disaccoppiati, microservizi, micro-frontend, monorepo con
# pacchetti multipli, …). Il TSK punta a un'entry via `target:` (§5).
#
# Per setup single-repo (default storico), una sola entry è sufficiente.
code_paths:
  - name: default                   # univoco; usato come `target:` nel TSK
    path: ./src/                    # relativo al repo factory o assoluto
    layers: [be, fe, db, qa, infra] # quali layer di TSK puntano qui (1+ valori)
    tags: []                        # opzionale: descrittivi (monolith, microservice, mfe, ...)
    vcs:
      mode: monorepo                # monorepo | submodule | sibling | external | none
      submodule_path: ""            # solo se mode=submodule
      remote_url: ""                # opzionale; documentazione e bootstrap
      branch_strategy: shared       # shared | per-tsk | per-sprint
      commit_coupling: float        # pin | float

# === Backward compatibility (v2.7-v2.11) ==========================================
# Se `code_path:` (singolare) è valorizzato e `code_paths:` è vuoto/assente, viene
# auto-promosso a `code_paths: [{name: default, path: <code_path>, layers: <tutti>,
# vcs: <blocco vcs top-level>}]`. Il blocco `vcs:` top-level (v2.8) resta valido
# solo in questo caso legacy; in multi-repo v2.12 il `vcs:` vive dentro a ciascuna
# entry di `code_paths`.
code_path: ""
vcs:
  mode: none
# ===================================================================================

# Modalità di scelta dello stack tecnologico
stack_mode: guided  # manual | guided | auto

# Routing TSK → consumer (default per layer; override per-TSK via frontmatter)
routing:
  be: agent      # agent | human
  fe: agent
  db: agent
  qa: agent
  infra: human

# Stack (in multi-repo, può essere per-entry — vedi §13.x)
stack:
  backend: <es. FastAPI 0.115 + Python 3.13>
  frontend: <es. React 19 + Vite>
  database: <es. PostgreSQL 17>
  qa: <es. Pytest + Playwright>
```

### Esempi multi-repo (v2.12)

**a) FE + BE disaccoppiati (2 repo)**:

```yaml
code_paths:
  - name: backend-api
    path: /Users/me/repos/portal-api/
    layers: [be, db, qa]
    tags: [monolith]
    vcs: { mode: sibling, remote_url: "git@github.com:org/portal-api.git" }
  - name: frontend-web
    path: /Users/me/repos/portal-web/
    layers: [fe]
    tags: [react, spa]
    vcs: { mode: sibling, remote_url: "git@github.com:org/portal-web.git" }
```

I TSK con `layer: be` finiscono in `backend-api` (auto-derivato, `target:` opzionale —
una sola entry copre `be`). Idem per `fe`. Layer `qa` ambiguo? Solo `backend-api` lo
copre → ancora auto-derivabile. Aggiungi `qa` a `frontend-web.layers` se vuoi e2e su
entrambi → allora `target:` diventa **richiesto** per i TSK QA.

**b) Microservizi (N BE + 1 FE)**:

```yaml
code_paths:
  - name: service-auth
    path: /Users/me/repos/auth-service/
    layers: [be]
    tags: [microservice, oidc]
    vcs: { mode: sibling, remote_url: "git@github.com:org/auth-service.git" }
  - name: service-payments
    path: /Users/me/repos/payments-service/
    layers: [be]
    tags: [microservice, stripe]
    vcs: { mode: sibling, remote_url: "git@github.com:org/payments-service.git" }
  - name: service-orders
    path: /Users/me/repos/orders-service/
    layers: [be]
    tags: [microservice]
    vcs: { mode: sibling }
  - name: web-app
    path: /Users/me/repos/web-app/
    layers: [fe, qa]
    tags: [next-js]
    vcs: { mode: sibling }
```

Tutti i microservizi hanno `layers: [be]`: il TSK BE **deve** dichiarare `target:` per
disambiguare (es. `target: service-auth`). Il TPM, quando produce TSK, decide il target
in base alla US/EP (es. una US "OAuth flow" → TSK BE su `service-auth`).

**c) Micro-frontend (N FE + 1 BE shared)**:

```yaml
code_paths:
  - name: api-gateway
    path: /Users/me/repos/api-gateway/
    layers: [be, db]
    vcs: { mode: sibling }
  - name: mfe-shell
    path: /Users/me/repos/mfe-shell/
    layers: [fe]
    tags: [module-federation, host]
    vcs: { mode: sibling }
  - name: mfe-checkout
    path: /Users/me/repos/mfe-checkout/
    layers: [fe]
    tags: [module-federation, remote]
    vcs: { mode: sibling }
  - name: mfe-catalog
    path: /Users/me/repos/mfe-catalog/
    layers: [fe]
    tags: [module-federation, remote]
    vcs: { mode: sibling }
```

Analogo (b) per i `fe`. Si possono mescolare coupling diversi: alcuni `sibling`, alcuni
`submodule`, alcuni `monorepo` (se uno dei repo ospita anche la factory). Ogni entry è
indipendente.

**d) Monorepo logico con pacchetti separati** (1 repo fisico, target distinti per
clarity di routing):

```yaml
code_paths:
  - name: api
    path: ./apps/api/
    layers: [be, db, qa]
    vcs: { mode: monorepo }
  - name: web
    path: ./apps/web/
    layers: [fe]
    vcs: { mode: monorepo }
  - name: shared
    path: ./packages/shared/
    layers: [be, fe]
    tags: [shared-lib]
    vcs: { mode: monorepo }
```

Tutto nello stesso commit chain. Il valore aggiunto è chiarezza di routing + scheduler
conflict detection più granulare (TSK su `api` non conflitta con TSK su `web`).

### Regole di routing

1. **TPM legge `factory.config.yaml`** e per ogni TSK applica `consumer: <routing[layer]>` come default. Override esplicito a livello TSK è ammesso (utile per task una-tantum).
2. **Override runtime**: il comando `/dev <TSK-id>` forza un dev-agent su un TSK con `consumer: human` (non modifica il file; è un'invocazione one-shot).
3. **Cambio topologia a runtime**: aggiungi/rimuovi il file agente in `.claude/agents/` (o equivalente). Aggiorna `topology:` e `routing:` in `factory.config.yaml` per coerenza. TPM userà la nuova config al prossimo run.
4. **Coerenza obbligatoria**: se `routing.be: agent` ma `be-dev.md` non esiste, è errore di config (segnalato da *Lint*). In multi-repo (v2.12): se `routing.<layer>: agent` ma nessuna entry in `code_paths` dichiara `<layer>` in `layers`, è errore di config (segnalato da Check 4c del lint).
5. **Target resolution (v2.12 multi-repo)**: il TPM, quando produce un TSK con `consumer: agent`, valorizza `target:` se l'ambiguità lo richiede (≥ 2 entry in `code_paths` con `<layer>` in `layers`). Algoritmo (vedi §5 `target` paragraph):
   - 0 match per layer → ERROR di config (Check 4c lint).
   - 1 match → `target:` opzionale, dev-agent risolve via auto-derive.
   - ≥ 2 match → `target:` **obbligatorio** sul TSK; assenza segnalata dal lint Check 4j (drift target/code_paths).
6. **Mai mescolare `code_path:` (legacy) e `code_paths:` (v2.12)**: scegliere una forma. In presenza di entrambi, `code_paths` prevale e `code_path` legacy viene ignorato con warning lint.

### Esempio: progetto BE agentico + FE umano (single-repo, legacy v2.11-)

```yaml
topology: hybrid-be-agents
code_path: /Users/me/Repos/customer-portal/   # legacy singolare
stack_mode: guided
routing:
  be: agent
  db: agent
  fe: human   # gli sviluppatori React lavorano in IDE
  qa: human
stack:
  backend: FastAPI 0.115
  frontend: React 19 (umano)
  database: PostgreSQL 17
```

In questo setup, `.claude/agents/` contiene `be-dev.md` e `db-dev.md`; `fe-dev.md` e `qa-dev.md` non esistono. I TSK con `layer: fe` o `layer: qa` finiscono in TODO board per umani; quelli con `layer: be|db` sono raccolti dai dev-agent (via `/run` → suggerimento).

### Esempio: BE agentico + FE umano (multi-repo, v2.12 forma esplicita)

Stessa topologia logica, ma il BE e il FE vivono in repo distinti:

```yaml
topology: hybrid-be-agents
code_paths:
  - name: portal-api
    path: /Users/me/repos/portal-api/
    layers: [be, db]
    vcs: { mode: sibling, remote_url: "git@github.com:org/portal-api.git" }
  - name: portal-web
    path: /Users/me/repos/portal-web/
    layers: [fe, qa]    # umani lavorano qui in IDE, factory non genera codice qui
    vcs: { mode: sibling, remote_url: "git@github.com:org/portal-web.git" }
stack_mode: guided
routing:
  be: agent      # dev-agent genera codice in portal-api
  db: agent      # idem
  fe: human      # umani in portal-web
  qa: human      # idem
```

Notare: portal-web ha `layers: [fe, qa]` ma `routing.fe: human` → i dev-agent non
vengono mai invocati su portal-web; serve a documentare nella config che quel repo
contiene FE+QA umani (utile per `repo-sync`, `code-quality-review-layer`, e per il
lint Check 4c che verifica la coerenza routing↔layers).

## §14 — Tech stack modes (nuovo in v2.7)

Tre modalità (campo `stack_mode` in `factory.config.yaml`):

### `manual` (v2.6 default)
L'utente scrive `raw/tech_stack.md` a mano prima di invocare *Arch*. Nessuna automazione.

### `guided`
Bootstrap mostra opzioni curate per ciascun layer (es. backend: FastAPI / Express / Spring Boot; database: PostgreSQL / MongoDB / SQLite) con pro/contro brevi citati. L'utente sceglie; bootstrap scrive `raw/tech_stack.md` riempito.

### `auto`
La skill `tech-scout` (invocabile da *Arch* o on-demand) legge `wiki/` (requisiti business + vincoli normativi) e usa fonti web datate (2026) per proporre uno stack ottimo (scalabilità, stabilità, supporto LTS). Output: `raw/tech_stack.md.proposal` con citazioni `[^web: <url> §<sezione>] (accessed YYYY-MM-DD)`. Gate umano obbligatorio per promuovere `.proposal` → `raw/tech_stack.md`.

**Invariante**: tech-scout NON sostituisce mai uno standard normativo già fissato in `raw/**` (§11). Se il wiki cita SPID/OIDC/FHIR, la proposta li adotta verbatim.

## §15 — VCS integration (v2.8, esteso multi-repo in v2.12)

La relazione fra il factory repo e il codice prodotto (L5) è dichiarata
esplicitamente in `factory.config.yaml.vcs.mode` per single-repo legacy, oppure in
`factory.config.yaml.code_paths[i].vcs.mode` per multi-repo v2.12 (una relazione VCS
**per entry**). La skill `vcs-handoff` applica una procedura diversa per ciascun mode;
gate umano sempre obbligatorio per operazioni distruttive o cross-repo (§7 r.14).

**Multi-repo (v2.12)**: ciascuna entry di `code_paths` ha il proprio blocco `vcs:`. Le
entry possono avere mode diversi (es. `backend-api: sibling`, `db: submodule`,
`shared-lib: monorepo`). `vcs-handoff` opera **per-target**: legge il `target:` del
TSK, risolve l'entry, applica la procedura del relativo `vcs.mode`. Mai operazioni
cross-entry coordinate (ogni commit chain è indipendente).

In single-repo legacy (`code_path:` singolare valorizzato, `vcs:` top-level), la skill
opera come v2.8-v2.11. Backward compat preservata.

### Modi VCS

Validi per **ciascuna entry** di `code_paths` (v2.12) o per il `vcs:` top-level (legacy single-repo):

| Mode | Significato | Quando |
|---|---|---|
| `none` | Nessun L5 per questa entry — la factory non gestisce codice qui | Solo legacy single-repo con `code_path: ""` (in multi-repo, se l'entry esiste deve avere un mode reale; rimuovere l'entry se non serve) |
| `monorepo` | L5 dentro al factory repo, un solo commit chain | `path` relativo al repo factory (es. `./src/`, `./apps/api/`) |
| `submodule` | L5 come git submodule dentro al factory repo | `path` relativo + `vcs.submodule_path` valorizzato + entry in `.gitmodules` |
| `sibling` | L5 in un working tree separato (altro clone) | `path` assoluto (o relativo fuori dal repo) + `vcs.remote_url` opzionale per documentazione |
| `external` | Path opaco, factory non ne conosce la topologia VCS | `path` qualsiasi, factory si limita a leggere/scrivere senza coordinare git |

**Mix di mode in multi-repo (v2.12)**: legittimo e comune. Esempio: una factory in
nuovo repo che ospita `db-migrations` come submodulo, legge `frontend-web` e
`backend-api` come sibling (clone separati di repo esistenti), e ha un `shared-lib`
embedded in monorepo:

```yaml
code_paths:
  - { name: db-migrations, path: ./db/, layers: [db], vcs: { mode: submodule, submodule_path: ./db/, remote_url: "..." } }
  - { name: frontend-web, path: /Users/me/repos/web/, layers: [fe], vcs: { mode: sibling } }
  - { name: backend-api, path: /Users/me/repos/api/, layers: [be], vcs: { mode: sibling } }
  - { name: shared-lib, path: ./packages/shared/, layers: [be, fe], vcs: { mode: monorepo } }
```

### Procedura `vcs-handoff` (per mode)

Invocata dal `dev-protocol` Fase 5, dopo `dev-handoff` (entry log) e prima
del return finale al chiamante.

**`monorepo`**: `git status` nel factory repo; se ci sono modifiche in `code_path`, propone un messaggio di commit con riferimento al TSK; gate umano → commit. Nessuna operazione cross-repo.

**`submodule`**:
1. `cd <submodule_path> && git status` → mostra i cambiamenti.
2. Propone messaggio di commit nel submodule + branch (vedi `branch_strategy` sotto).
3. **Gate umano** → conferma commit nel submodule (e push opzionale).
4. `cd <factory> && git add <submodule_path>` → stagea il bump del ref.
5. Propone commit nel factory repo: `chore(<layer>): bump <submodule_path> for TSK-ZZZ`.
6. **Gate umano** → conferma commit factory.
7. Se `commit_coupling: pin` → aggiorna `.factory-lock` (vedi sotto).

**`sibling`**:
1. `cd <code_path> && git status` → mostra i cambiamenti.
2. Propone messaggio di commit + branch nel repo sibling.
3. **Gate umano** → conferma commit (e push opzionale, mai automatico).
4. Append a `wiki/log.md`: `develop TSK-ZZZ → <commit-hash> @ <code_path>` con avviso "ricorda di mergeare il PR su <remote_url>".
5. Se `commit_coupling: pin` → aggiorna `.factory-lock`.

**`external`**:
1. Nessuna operazione VCS (la factory non sa cosa c'è in `code_path`).
2. Solo append a `wiki/log.md` con il path e — se git-tracciato — il commit hash. Best-effort.

### `branch_strategy`

Solo per `submodule` e `sibling`:
- `shared` (default) — tutti i `develop` commit sul branch corrente (di solito `main`/`dev`).
- `per-tsk` — un branch per TSK (`tsk-<id>-<slug>`). La skill propone il `git checkout -b` al primo commit del TSK.
- `per-sprint` — un branch per sprint (`sprint-<NN>`). Letto da `factory.config.yaml` o dal frontmatter `sprint:` del TSK.

### `commit_coupling`

- `float` (default) — solo log entry su `wiki/log.md`, nessun file di lock. Più semplice, accetta drift.
- `pin` — la skill mantiene `.factory-lock` al root: un file YAML che mappa ogni `develop` chiuso al commit hash del codice. Garantisce reproducibilità (`git checkout <factory-commit>` → so esattamente quale commit di L5 corrispondeva).

`.factory-lock` (formato):
```yaml
# .factory-lock — generato da vcs-handoff, append-only
- tsk: TSK-042
  layer: be
  vcs_mode: submodule
  submodule_path: ./code/
  commit: a1b2c3d4
  date: 2026-05-20T14:32:00Z
```

### Vincoli inviolabili (estensione §7 r.14)

- **Mai `git push`** senza gate umano esplicito. La skill propone, l'umano esegue.
- **Mai `git submodule add|update --remote`** senza gate umano. Al bootstrap, la skill stampa il comando da eseguire, ma non lo lancia.
- **Mai `git clone`** automatico al bootstrap per `sibling`: stampa istruzioni, l'umano cloni.
- **Mai `--force` / `--no-verify`** in nessun caso.
- **Mai modificare `.gitmodules`** o `.factory-lock` fuori da `vcs-handoff`.

## §16 — Sync adapters (multi-source L1, v2.9, esteso v2.14 Fase 2)

Il ruolo *Sync* (§2) è l'unico ruolo della factory **pluralizzabile per sorgente**: per
ogni famiglia di input L1 (PDF, Figma, repo locale, **knowledge graph**, futuri
Notion/Confluence/…) esiste un sub-agent dedicato che scrive il proprio sotto-scope di
`raw/`. Tutti i sub-agent condividono `raw/.extraction-manifest.json` (append-only per
chiave). L'*Analyst* (`wiki-keeper`) resta agnostico alla sorgente: legge il manifest,
capisce quale shape gestire (`.txt` | `.kb.json` | …), applica la grammatica di
citazione corrispondente (§6).

### Sub-agent supportati (v2.14)

| Sub-agent | Input | Output L1 | Trigger |
|---|---|---|---|
| `sync-docs` | `raw/*.pdf` | `raw/*.txt`, `raw/images/*-fig-NN.md` | nuovi PDF in `raw/` |
| `figma-sync` (v2.9) | URL Figma o `file_key` (passato al comando, non vive in `raw/`) | `raw/YYYY-MM-DD-figma-<file-key>.kb.json`, opzionali `raw/images/*-frame-NN.{md,png}` | comando `/figma-sync <url>` |
| `repo-sync` (v2.12) | path locale a un repo esistente (passato al comando; mai vive in `raw/`) | `raw/YYYY-MM-DD-repo-<slug>.md` (documento di specifiche umano-leggibile + sezioni strutturate: stack rilevato, struttura, moduli chiave, API surface, dipendenze, vincoli normativi); opzionale companion `raw/images/YYYY-MM-DD-repo-<slug>-tree.md` (albero del filesystem fino a depth configurabile) | comando `/repo-sync <path>`; usato dal bootstrap quando `wiki_feed_source: existing-repo` |
| **`graphify-sync` (v2.14 Fase 2)** | **`code_path` (entry di `factory.config.yaml.code_paths`) — passato al comando, non vive in `raw/`** | **`raw/YYYY-MM-DD-graph-<slug>.md` (summary umano-leggibile del graph: god nodes, surprising connections, confidence breakdown EXTRACTED/INFERRED/AMBIGUOUS) + side-channel `.graphify-state/code_paths/<slug>/{graph.json, GRAPH_REPORT.md, last_full_rebuild.txt}` (consumato dai dev-agent come context replacement)** | **comando `/graphify-sync <target>`; usato dai dev-agent in modalità context-compression quando `compression.context.enabled: true` (§20.10)** |

### Contratto per un nuovo sync adapter

Aggiungere un sub-agent (es. ipotetico `notion-sync`) richiede:

1. File agente in `.claude/agents/<name>.md` (o equivalente per altro runtime) — **thin**, identità + scope (regola v2.3).
2. Skill di procedura in `.claude/skills/<name>-protocol.md` — **fat**, contiene Discovery → Estrazione → Serializzazione (regola v2.3).
3. Comando di invocazione in `.claude/commands/<name>.md`.
4. Naming dichiarato in §4 (path + pattern per gli artefatti prodotti). Convenzione: prefisso del source nel filename (`<source>-` o `<source>-<key>-`) per evitare collisioni cross-adapter.
5. Una voce in `raw/.extraction-manifest.json` per ciascuna estrazione, con chiave univoca derivata dal source.
6. Eventuale grammatica di citazione in §6, se lo shape non è `.txt`/`.md` (es. `.kb.json` ha la regola dotted-path).
7. Aggiornamento di `ingest-protocol` (Fase 0/1) per leggere il nuovo shape.
8. Aggiornamento di `lint-checks` Check 4e (coerenza manifest ↔ filesystem) per validare il nuovo tipo.

### Invariante di isolamento

Ogni sub-agent Sync scrive **solo** nel proprio scope di naming. Mai sovrapposizioni:
`sync-docs` non tocca `*.kb.json` né `*-repo-*.md` né `*-graph-*.md`; `figma-sync` non
tocca `*.txt` né `*-repo-*.md` né `*-graph-*.md`; `repo-sync` non tocca `*.txt` né
`*.kb.json` né `*-graph-*.md` (e mai i file della sorgente scansionata, §7 r.17);
`graphify-sync` (v2.14) non tocca `*.txt` né `*.kb.json` né `*-repo-*.md` (e mai il
`<code_path>` analizzato — §7 r.17 esteso a code_path scanning). Se due adapter
producono lo stesso slug per fonti diverse → ERROR di config (un sync adapter deve
scegliere un namespace univoco). Solo `.extraction-manifest.json` è condiviso, e
ciascun sub-agent vi appende **solo la propria entry** (mai overwrite di entries
altrui).

### Side-channel storage per `graphify-sync` (v2.14 Fase 2)

A differenza degli altri sync adapter, `graphify-sync` produce **due output paralleli**:

1. **`raw/YYYY-MM-DD-graph-<slug>.md`** — riepilogo umano-leggibile del graph,
   consumabile dal `wiki-keeper` come ingest L1→L2 standard (analogo a `repo-sync`).
2. **`.graphify-state/code_paths/<slug>/`** — side-channel storage (analogo a
   `code_quality/`, §19): graph completo machine-readable (`graph.json`,
   `GRAPH_REPORT.md`, `last_full_rebuild.txt`). **Non versionato in git**
   (`.gitignore`-d). Consumato a runtime dai dev-agent e dal code-reviewer come
   **context replacement** dei file sorgente raw (vedi §20.10 Context Compression).

Caratteristiche del side-channel:
- **Rebuildable**: full rebuild ricostruisce tutto da zero da `<code_path>` (zero
  perdita di stato, solo costo di rebuild — 2–20 $ token su primo build).
- **Scritto solo da `graphify-sync`** (analogo a R.Q2 di CQRL — scope di scrittura chiuso).
- **Letto da molti**: dev-agent, code-reviewer, wiki-query (in v2.15 sperimentale).
- **Filesystem è single source of truth**: il graph è una *view derivata*, mai
  authoritative. Se conflitto graph ↔ filesystem → vince filesystem.

### Riuso dello Stack Detector (v2.12)

La skill `stack-detector` (definita in §19.2 come componente del Code Reviewer) è
**riusabile** dal `repo-sync`: durante la fase di estrazione del repo esistente, il
sub-agent invoca `stack-detector` per popolare la sezione `## Stack rilevato` del
documento `raw/YYYY-MM-DD-repo-<slug>.md` con `language`, `framework`, `framework_version`,
`secondary_libs`, `patterns_expected`. Questa riusabilità rende la pipeline coerente:
ciò che il Code Reviewer userà a runtime sul codice prodotto da L5 è la stessa logica
che `repo-sync` usa al kick-off di un progetto pre-esistente.

### Bootstrap da repo esistente — coupling modes (v2.12)

Quando la factory viene scaffoldata con `wiki_feed_source: existing-repo` (cioè il
materiale iniziale per L1 è uno o più repo locali preesistenti), l'utente sceglie
**come accoppiare** la factory ad ogni repo. La scelta determina deterministicamente
le entry di `code_paths` (§13) — `path`, `layers`, `vcs.mode` — non chiesti
indipendentemente.

**Single-repo vs multi-repo** (v2.12):
- **Un solo repo sorgente** → una sola entry in `code_paths`, una sola scelta di coupling.
- **Più repo sorgente** (FE + BE disaccoppiati, micro-frontend, microservizi, ...) → N
  entry in `code_paths`, ciascuna con il proprio coupling. Coupling diversi per repo
  diversi sono legittimi (es. monorepo per il primo repo + sibling per gli altri,
  oppure tutti sibling — vedi sotto).

**Tre coupling canonici** (applicabili a ciascun repo indipendentemente):

| Coupling | Path destinazione factory | `code_path` (derivato) | `vcs.mode` (derivato) | Modifica al repo sorgente? |
|---|---|---|---|---|
| **`monorepo`** | = path del repo esistente | `./` (radice) o sub-path interno | `monorepo` | **Sì** (factory installata dentro il repo: `PATTERN.md`, `.claude/`, `wiki/`, `management/`, `factory.config.yaml`, `code_quality/` se on, …). Mai sovrascrive file esistenti del repo (i path del cascade L1-L4 sono nuovi). Gate umano obbligatorio per conferma (potenziale conflitto se il repo ha già file omonimi). |
| **`sibling-new-repo`** | Nuovo path separato (default: `<repo-path>-factory/` accanto) | path **assoluto** verso il repo esistente | `sibling` | **No**. Il repo sorgente resta intatto (§7 r.17). La factory legge `<code_path>` come working tree esterno. `vcs.remote_url` opzionale (URL git del repo esistente, per documentazione). |
| **`submodule-new-repo`** | Nuovo path separato | relativo (default: `./code/`) | `submodule` + `submodule_path: ./code/` | **No** al momento del bootstrap. L'utente esegue manualmente `git submodule add <remote-url-repo-esistente> ./code/` dopo il bootstrap (gate §7 r.14 — mai automatico). |

**Vincoli inviolabili del coupling**:

- **R.B1 — Il bootstrap non scrive mai nel repo esistente in modalità `sibling-new-repo`
  o `submodule-new-repo`.** Anche se `repo-sync` (durante il bootstrap stesso) lo legge,
  l'unico path di scrittura è la nuova factory directory.
- **R.B2 — In `monorepo`, conferma esplicita prima di toccare il repo esistente.** Il
  bootstrap mostra in chat la lista dei file/cartelle che verrebbero aggiunti
  (`PATTERN.md`, `CLAUDE.md` o equivalente adapter, `factory.config.yaml`, `wiki/`,
  `management/`, `design_&_architecture/`, `memory/`, `raw/`, `.claude/` o adapter
  scelto, `code_quality/` se on) e attende `y/N`. Se uno qualsiasi di questi path
  esiste già nel repo → ABORT con messaggio chiaro (no overwrite distruttivo).
- **R.B3 — `repo-sync` scansiona la sorgente in modalità read-only sempre** (§7 r.17),
  indipendentemente dal coupling. In `monorepo`, scansiona dopo che il bootstrap ha
  scaffoldato i file della factory: il documento `raw/<data>-repo-<slug>.md` osserverà
  l'esistenza della factory appena creata come parte dello stato del filesystem (caso
  *reflective*, legittimo). Per evitare rumore, la skill `repo-extraction-protocol`
  esclude dallo scope di scansione i path tipici della factory (`PATTERN.md`,
  `.claude/`, `wiki/`, `management/`, `design_&_architecture/`, `memory/`, `raw/`,
  `code_quality/`).
- **R.B4 — Il coupling è scelto al bootstrap e non muta a runtime.** Cambiare coupling
  dopo lo scaffolding richiede rifare il bootstrap o operazioni VCS manuali (cross-repo).
  Il `wiki-lint` check 4d (coerenza VCS) segnala drift fra coupling implicito e
  `vcs.mode` corrente.
- **R.B5 — Agent-agnostic preservato**: il coupling è una proprietà del *layout
  filesystem* e *VCS*, non del runtime di agenti. Qualsiasi adapter (`.claude/`,
  `.cursor/`, `.openai/`, …) può coesistere su una factory bootstrappata con qualsiasi
  coupling. La scelta dei tool (Read/Write/Bash di Claude Code, o equivalenti) è
  ortogonale.
- **R.B6 — Multi-repo coupling mix** (v2.12): in setup multi-repo, ogni entry di
  `code_paths` può avere un coupling distinto. Vincoli:
  - **Al massimo una** entry può avere `vcs.mode: monorepo` (un solo repo può ospitare
    fisicamente la factory). Più entry con `monorepo` simultanee → ERROR di config
    (segnalato dal lint Check 4d esteso).
  - Le altre entry coesistono come `sibling` (path assoluto, no operazioni cross-repo
    automatiche), `submodule` (referenziate dal factory repo via `.gitmodules`), o
    `external` (path opaco).
  - **`repo-sync` invocato N volte al bootstrap**, una per entry in `code_paths` con
    `wiki_feed_source: existing-repo`. Produce N file in `raw/` (uno per repo); il
    `wiki-keeper` può ingerirli in batch (§3 "Ingest" parallelo via
    `wiki-keeper-worker` se N ≥ 3).
  - **`vcs-handoff` opera per-target**: dato il `target:` del TSK, risolve l'entry,
    applica la procedura del relativo `vcs.mode`. Mai operazioni cross-target
    coordinate automaticamente (gate umano §7 r.14 invariato).
  - **Scheduler conflict-detection** (§18.4 R.S2): due TSK con `target` diversi sono
    sempre conflict-free (filesystem disgiunti). Conflict solo intra-target su
    overlap di `code_path` glob.

### Esempi multi-repo + coupling al bootstrap

**Caso a — microservizi all-sibling** (factory in nuovo repo, tutti i microservizi
restano intatti):

```
Bootstrap input:
- wiki_feed_source: existing-repo
- N = 3 repo (auth-service, payments-service, web-app)
- coupling per ciascuno: sibling-new-repo
- factory destination: /Users/me/repos/customer-portal-factory/ (NUOVO)
- Per ciascun repo: path assoluto + name + layers + remote_url

Risultato:
- /Users/me/repos/customer-portal-factory/ contiene la factory
- Auth/Payments/Web restano INTATTI (R.B1)
- code_paths in factory.config.yaml ha 3 entry, ciascuna sibling
- raw/ contiene 3 file *-repo-<slug>.md (uno per servizio)
```

**Caso b — monolite monorepo + servizio sidecar sibling**:

```
Bootstrap input:
- wiki_feed_source: existing-repo
- N = 2 repo (monolith-app, notification-service)
- coupling: monolith-app=monorepo (factory dentro), notification-service=sibling
- factory destination: /Users/me/repos/monolith-app/ (= path del monolith)

Risultato:
- Monolith-app contiene ora anche la factory (PATTERN.md, .claude/, ecc.)
- Notification-service resta intatto (R.B1)
- code_paths: 2 entry — uno monorepo (./ del monolith) + uno sibling
  (/Users/me/repos/notification-service/)
```

**Caso c — tutti submodule**:

```
Bootstrap input:
- N = 4 repo (frontend-web, backend-api, db-migrations, shared-lib)
- coupling per tutti: submodule-new-repo
- factory destination: /Users/me/repos/portal-factory/

Risultato:
- Factory in /Users/me/repos/portal-factory/
- code_paths: 4 entry, ciascuna submodule, submodule_path: ./code/<name>/
- Bootstrap stampa 4 comandi `git submodule add ...` per l'utente (mai automatici, §7 r.14)
```

### `raw/.extraction-manifest.json` (esteso v2.9)

Formato per entry (retrocompat: entries pre-v2.9 senza `source` sono interpretate come
`source: pdf` dal *Analyst* e dal *Lint*):

```json
{
  "<key>": {
    "source": "pdf | figma | notion | ...",
    "extracted_at": "ISO-8601",
    "primary_artifact": "raw/<path>",
    "secondary_artifacts": ["raw/images/<...>", "..."],
    "extractor_version": "<sub-agent>@<semver>",
    "extraction_metadata": { ... }
  }
}
```

`<key>` è il nome base senza estensione e senza directory (es. `2026-05-21-spid-tech` o
`2026-05-21-figma-ABC123`). I sub-agent **non scrivono mai** entry con `source` diverso
dal proprio.

### Vincoli inviolabili (estensione §7)

- **L1 read-only resta intatto** (§7 r.1): solo *Sync* scrive in `raw/`, e ciascun
  sub-agent solo nel proprio scope.
- **Mai chiamare API esterne automaticamente in altri ruoli**. Le chiamate ad
  Anthropic API, Figma MCP, Figma REST API, Notion API, ecc. vivono solo nelle skill
  dei sub-agent Sync. Gli altri ruoli (PM/Arch/TPM/Dev) leggono il manifest e i
  file di `raw/`, mai la fonte originale.
- **Mai scrittura cieca**: il sub-agent Sync mostra in chat la proposta (file da
  creare, dimensioni, fonti) e attende conferma esplicita prima di scrivere
  (analogo a `ingest-protocol` Fase 2).

## §17 — Publisher adapters (multi-target L3/L4, v2.10)

Simmetrico ai sync adapters (§16): se §16 definisce il contratto per ingerire L1 da
fonti eterogenee, §17 definisce il contratto per **pubblicare L3/L4 verso tool esterni
di project tracking** (GitHub Issues/Projects, GitLab Issues, Jira, Linear, …) come
mirror push-only. Il sub-agent Publisher è un nuovo ruolo (§2) pluralizzabile per
provider.

### Invariante di direzione (PATTERN §8, single source of truth)

- `management/kanban/**` resta **canonico**. Il provider esterno è un mirror.
- Pubblicazione è **push-only** in v2.10. Modifiche fatte direttamente nel tool
  esterno (body, label, milestone) verranno **sovrascritte** al prossimo publish.
  Solo i `comment` e gli assignee su issue esterne sono lasciati intatti
  (l'Publisher non li tocca).
- Bidirectional di `status:` (issue chiusa sul provider → `status: done` nel TSK
  locale) è candidato per v2.11, **non implementato in v2.10**.

### Provider supportati (v2.10)

| Provider | Sub-agent | Implementazione di riferimento | Auth env (default) |
|---|---|---|---|
| `github` | `github-publisher` | `gh` CLI (richiede `gh auth login` preventivo) | `GH_TOKEN` |
| `gitlab` | `gitlab-publisher` | `glab` CLI o REST API | `GL_TOKEN` (placeholder, non implementato in v2.10) |
| `jira` | `jira-publisher` | REST API Cloud | `JIRA_TOKEN` (placeholder, non implementato in v2.10) |
| `linear` | `linear-publisher` | GraphQL API | `LINEAR_TOKEN` (placeholder, non implementato in v2.10) |
| `none` | — | (publishing disabilitato) | — |

`v2.10` rilascia il solo `github-publisher` come riferimento. Gli altri provider
hanno il contratto pronto ma agent/skill **non scaffoldati**: aggiungerli è
seguire il contratto del nuovo adapter (vedi sotto), niente modifiche a PATTERN.

### `factory.config.yaml.kanban_publish` (schema, v2.10)

```yaml
kanban_publish:
  provider: github          # none | github | gitlab | jira | linear | custom
  target: "<org>/<repo>"    # provider-specific: GH "org/repo", GL "group/proj", JIRA "PROJ", LINEAR "team-id"
  auth_env: GH_TOKEN        # nome variabile d'ambiente che contiene il token
  mode: push-only           # v2.10: solo push-only; bidirectional candidato v2.11
  batch_limit: 10           # gate §7 r.15: max artefatti pubblicati per run senza secondo gate
  mapping:
    epic_to: milestone      # milestone | issue-label | project-column
    story_to: issue-label   # issue-label | issue-type-story
    task_to: issue-label    # issue-label
    sprint_to: milestone    # milestone | project-iteration | cycle
  labels:
    epic: "kanban:epic"
    story: "kanban:story"
    task: "kanban:task"
    layer_prefix: "layer:"  # produce label "layer:be", "layer:fe", ...
  filter:
    only_consumer: any      # any | agent | human (pubblica solo TSK con questo consumer)
    only_status: any        # any | todo | in-progress | done
```

### Contratto per un nuovo Publisher adapter

Aggiungere un sub-agent (es. `linear-publisher`) richiede:

1. Agente thin in `.claude/agents/<provider>-publisher.md` (o equivalente per altro runtime).
2. Skill provider-specific in `.claude/skills/<provider>-mapping.md` — mapping concreto EP/US/TSK ↔ artefatti del provider + comandi CLI/API.
3. La skill provider-agnostic `publisher-protocol` (5 fasi) **non va duplicata**: il provider-specific mapping è una skill *complementare* invocata da `publisher-protocol`.
4. Nessun nuovo comando: `/kanban-publish` è agnostico al provider — legge `kanban_publish.provider` da config e invoca il sub-agent corrispondente.
5. Aggiornamento di `lint-checks` Check 4f (lista provider noti).
6. **Mai modifiche a PATTERN.md** per un nuovo provider: il contratto §17 li copre tutti.

### Invariante di isolamento

Ogni Publisher scrive **solo** nel proprio scope:

- Frontmatter `external_id:` di `management/kanban/EP-*/US-*/TSK-*.md` — solo se il prefisso `<provider>:` corrisponde al sub-agent attivo.
- `wiki/log.md` (append-only, marker `publish`).
- Provider esterno via CLI/API (gate umano per ogni batch, §7 r.15).

**Mai sovrapposizioni**:
- Mai modificare il corpo dei file kanban (PM/TPM ownership).
- Mai modificare `external_id:` di altri provider (es. `github-publisher` non tocca `external_id: jira:...`).
- Mai cancellare/chiudere issue esterne (anti-pattern destruttivo, vedi §7 r.15).

### Procedura `publisher-protocol` (5 fasi, provider-agnostic)

1. **Bootstrap**: verifica auth (`<auth_env>` settato + provider raggiungibile via CLI/API ping). Read `factory.config.yaml.kanban_publish`. ABORT se config invalida.
2. **Discovery**: `Glob management/kanban/EP-*/EP-*.md`, `US-*/US-*.md`, `**/TSK-*.md`. Filtra in base a `kanban_publish.filter`. Estrai metadata (frontmatter + body markdown).
3. **Plan**: per ciascun artefatto, decidi CREATE vs UPDATE in base a `external_id:` frontmatter. Mostra in chat: `Plan: CREATE EP×N1, US×N2, TSK×N3; UPDATE EP×M1, US×M2, TSK×M3. Procedo? [y/N]`. **Attendi conferma esplicita** (§7 r.15). Se totale CREATE+UPDATE > `batch_limit`, secondo gate obbligatorio.
4. **Publish**: per ogni artefatto, invoca la skill provider-specific (`<provider>-mapping`) che esegue CREATE/UPDATE sul provider e ritorna l'`external_id`. Aggiorna il frontmatter locale **solo nel campo `external_id:`** (mai del corpo).
5. **Log**: append a `wiki/log.md` template `publish` (count CREATE/UPDATE/skipped, lista artefatti con nuovo `external_id`). Output a chat: link al milestone/board esterno.

## §18 — Parallel scheduling (DAG-driven, v2.11)

La factory esegue **per default in serie** ogni operazione di un agente — invariante di sicurezza
(`wiki/` single-committer §7 r.12, append-only su `log.md` §7 r.5, gate cross-tool §7 r.15).
Il **parallel scheduler** è il meccanismo agent-agnostic con cui l'Orchestrator (§2) riconosce
**a runtime** le finestre in cui più operazioni o sub-agent possono essere lanciati in parallelo
senza violare gli invarianti. Vive nell'Orchestrator (Claude Code adapter: `.claude/agents/orchestrator.md`)
ed è alimentato dai campi `depends_on` / `blocked_by` / `code_path` introdotti in §5.

### §18.1 — Modello

Costruisci un DAG `G = (V, E)` dove:

- `V` = artefatti azionabili nello sprint corrente (EP/US/TSK) + operazioni one-shot
  in coda (es. N ingest paralleli su `raw/`).
- `E = E_dep ∪ E_conf`:
  - `E_dep` (causal): `v → u` se `u.depends_on ∋ v` (cascade L3→L4→L5) o se `u.blocked_by` contiene una `Q_NNN` non chiusa che cita `v` (resolution L2→L3).
  - `E_conf` (file-conflict, solo per TSK): `u — v` (arco non orientato) se `intersect(u.code_path, v.code_path) ≠ ∅`. Modellato come pseudo-arco che impedisce la co-esecuzione allo stesso level senza implicare ordine.

### §18.2 — Algoritmo (3 step)

```
schedule(sprint_context, factory_config):
  # 1. Build DAG
  V := candidates(sprint_context)         # status=todo|ready, consumer routing rispettato
  E_dep := { (v→u) | u.depends_on ∋ v OR Q-block(u, v) }
  E_conf := { (u—v) | TSK overlap(u.code_path, v.code_path) }

  # 2. Toposort + level grouping
  if cycle_detected(V, E_dep): ABORT "ciclo in depends_on" — report agli umani
  levels := topo_levels(V, E_dep)         # antichain per level

  # 3. Parallel-safe partition di ogni level
  for L in levels:
    groups := partition(L, E_conf)        # graph-coloring greedy
    # tutti i nodi di groups[i] sono parallelizzabili allo stesso wall-clock
    yield groups
```

**`topo_levels`** è una toposort che assegna a ogni nodo `v` il livello `1 + max(level(parents))`,
con `level(root) = 0`. Nodi senza dipendenze finiscono al level 0 e partono insieme.

**`partition(L, E_conf)`** è graph coloring greedy: itera nodi per `priority DESC, estimate ASC`
(quick wins prima), assegna ciascuno al primo gruppo che non ha conflitti di `code_path`. Output:
gruppi di TSK senza overlap fra loro → eseguibili in parallelo via multi-tool-call dell'Orchestrator
(adapter Claude Code: N `Agent` call nello stesso turno).

### §18.3 — Domini di parallelismo (cosa parallelizzare, cosa no)

| Dominio | Parallelizzabile? | Vincolo / strategia |
|---|---|---|
| **Ingest L1→L2** (`wiki-keeper-worker`) | Sì (già v2.4) | N ≥ 3 raw nel manifest → fan-out N sub-agent; merge serializzato (§16, `ingest-protocol` Fase 1.bis) |
| **Develop TSK→L5** (dev-agent) | Sì (nuovo v2.11) | Antichain calcolato come §18.2. Sub-agent dev distinti possono lavorare su layer/file disgiunti. `vcs-handoff` resta serializzato per ogni commit |
| **Lint check** (`wiki-lint`) | Sì (read-only) | I check sono read-only e indipendenti per file; fan-out trasparente |
| **Query** (`wiki-query`) | Sì (read-only) | Multiple query NL indipendenti — nessun side effect |
| **Plan L2→L3** (`product-manager`) | No (per ora) | Single-committer su `management/`. Candidate v2.12 con sub-agent PM per epica indipendente |
| **Design+Execute L3→L4** (`lead-architect`, `tpm`) | No | Coerenza globale del design — un solo arch per repo per run |
| **Publish L3/L4 → tool esterno** | No | Gate batch_limit §7 r.15, idempotenza via `external_id` richiede serializzazione |
| **Sync (`sync-docs`, `figma-sync`, `repo-sync`)** | Sì per sorgenti diverse | Due `figma-sync` su `file_key` diversi possono girare insieme; due `sync-docs` su PDF diversi anche; un `repo-sync` su path X e un `figma-sync` su `file_key` Y possono girare in parallelo (sorgenti disgiunte). Stessa sorgente → serial (`raw/.extraction-manifest.json` append-only single-writer) |
| **Review** (`code-reviewer`, v2.12) | Sì per TSK indipendenti | Antichain calcolato come `develop` ma su TSK con `status: done` + `review_status: pending`. Overlap `code_path` → serializza (R.S2). Le 3 passate del Reviewer (idiomaticità/design/robustezza, §19.3) sono sub-skill interne, **non sub-agent** — girano in parallelo all'interno della singola invocazione `code-reviewer`. `code_quality/reports/<TSK-id>-iter-N.*` write single-committer per file (R.S1) |
| **Promote `status:`** | No | Operazione meccanica, sub-second; parallelismo non paga |
| **Heal** | No | Loop evaluator-optimizer ha stato condiviso (max 3 iter, single-committer §7 r.12) |
| **Premortem** (`/premortem`, skill `premortem-protocol`, v2.16) | Sì per target distinti (default `parallel` — ADR-004) | Premortem su artefatti diversi sono indipendenti per costruzione: la skill scrive solo in chat + append serializzato sul caller (`wiki/log.md` + `management/risk-registry.md` opt-in + `memory/episodic/premortem-runs.md`). Vedi nota composizione N×M sotto. Opt-out via `domains.premortem: serial` |

**Composizione N × M (premortem, v2.16)**: il dominio `premortem` introduce un **secondo livello** di parallelismo annidato. Lo scheduler dispatcha N invocazioni `/premortem` parallele (dominio sopra); ognuna **internamente** esegue la Fase 4 (Parallel Deep-Dives) con fan-out fino a `max_parallel: 8` sub-agent investigatori (cap **hardcoded** nella skill — ADR-001, distinto dal `scheduler.max_parallel`). Esempio peggiore con `scheduler.max_parallel: 4` e 3 `/premortem` attive: 3 × 8 = **24 sub-agent contemporanei**. R.S1 (single-committer, §7 r.12) è preservato automaticamente: i sub-agent della Fase 4 **non scrivono su filesystem**, ritornano solo al caller, che serializza ogni append. I due `max_parallel` (scheduler vs cap interno Fase 4) vivono a livelli diversi e non vanno confusi.

### §18.4 — Regole inviolabili dello scheduler (estensione §7)

Lo scheduler **deve** rispettare:

- **R.S1 — Single-committer preservato** (§7 r.12). Anche con N dev-agent in parallelo, `wiki/log.md`
  e `wiki/gaps.md` ricevono entry **una alla volta** (il dispatcher accoda le scritture; ciascun
  dev-agent restituisce una entry-line, l'Orchestrator le appende seriali). Mai due agent scrivono
  sullo stesso file nello stesso turno.
- **R.S2 — Conflict-free su `(target, code_path)`** (v2.12 multi-repo aware). Due TSK
  con `intersect(code_path) ≠ ∅` **e** stesso `target` (o entrambi su single-repo legacy)
  **non** possono essere allo stesso group (§18.2 step 3). Due TSK con `target` diversi
  (multi-repo) sono **sempre** conflict-free su file (filesystem disgiunti) — possono
  girare in parallelo anche con glob identici. Glob vuoto = scope sconosciuto =
  serializzante (mai parallelizzato con altri TSK dello stesso target; resta
  parallelizzabile cross-target).
- **R.S3 — Cap di fan-out**. `factory.config.yaml.scheduler.max_parallel` (default `4`) è il
  numero massimo di sub-agent lanciati nello stesso turno. Lo scheduler taglia il group più
  grosso in chunk se eccede il cap.
- **R.S4 — Gate umano sopra `parallel_gate_threshold`** (default `3`). Se un group ha `≥ threshold`
  sub-agent, l'Orchestrator stampa il piano (lista TSK + sub-agent + estimate aggregato) e
  attende `y/N` prima di dispatchare. Analogo a §7 r.15 (publish) e `ingest-protocol` Fase 2.
- **R.S5 — Ciclo nelle dipendenze = ABORT**. Mai "rompere" un ciclo automaticamente: rapporto
  agli umani con la lista delle entry coinvolte.
- **R.S6 — Re-scheduling è idempotente**. Lo scheduler ricostruisce il DAG da capo a ogni `/run`;
  non mantiene stato fra invocazioni (lo stato è derivato §8: filesystem + `wiki/log.md` + `memory/episodic/`).
- **R.S7 — Fallimento di un sub-agent non rollba gli altri**. Se uno dei TSK paralleli fallisce
  (eccezione, output malformato), gli altri proseguono. Il fallimento è annotato in `wiki/log.md`
  come entry `develop-failed TSK-ZZZ` e il TSK resta `status: todo`. Convergenza opportunistica.
- **R.S8 — VCS sempre serializzato**. La skill `vcs-handoff` (§15) esegue commit/branch operations
  **uno per volta**, anche se i dev-agent producono il codice in parallelo. Il dispatcher accoda
  le richieste vcs-handoff alla fine del wave parallelo. (Ragione: `git index lock`, branch state,
  gate umano §7 r.14.)

### §18.5 — `factory.config.yaml.scheduler` (v2.11)

```yaml
scheduler:
  enabled: true                    # false → sempre seriale (modalità conservativa)
  max_parallel: 4                  # cap fan-out per turno (R.S3)
  parallel_gate_threshold: 3       # ≥ N parallel → gate umano (R.S4)
  code_path_conflict: strict       # strict | warn | off
                                   # strict (default): glob overlap → serializza
                                   # warn: glob overlap → warning e procede
                                   # off: disabilita conflict detection (sconsigliato)
  empty_code_path_policy: serial   # serial (default) | parallel
                                   # serial: lista vuota = serializzante
                                   # parallel: lista vuota = parallelizzabile (rischioso)
  domains:                         # opt-in/out per dominio (§18.3)
    ingest:        true
    develop:       true
    lint:          true
    query:         true
    plan:          false           # candidate v2.13
    design:        false
    publish:       false
    sync:          true            # per sorgenti distinte (PDF, Figma, repo)
    review:        true            # v2.12 — Code Reviewer su TSK indipendenti
    premortem:     true            # v2.16 — /premortem su target distinti (composizione N×M, ADR-004)
```

Se `scheduler.enabled: false` o assente, comportamento pre-v2.11 (tutto seriale).

### §18.6 — Output dello scheduler (forma osservabile)

L'Orchestrator, alla `/run`, stampa il **wave plan** in chat prima di dispatchare:

```
WAVE PLAN (sprint NN, sched v2.11)
====================================
Level 0 — parallel (3 of max 4):
  ▸ Group A:
    • TSK-007 [be, S, P0] code_path=src/auth/**
    • TSK-012 [db, M, P1] code_path=db/migrations/0042_*.sql
    • TSK-019 [fe, S, P0] code_path=web/src/login/**
Level 1 — serial (2 nodes, depends_on Level 0):
  ▸ TSK-008 [be, S, P0] depends_on=[TSK-007] code_path=src/auth/handlers/**
  ▸ TSK-013 [qa, M, P1] depends_on=[TSK-007,TSK-012,TSK-019] code_path=tests/e2e/**

VCS hand-off accodato seriale dopo ogni wave.
Procedo? [y/N]
```

Se `len(Group) ≥ parallel_gate_threshold`, l'Orchestrator attende conferma esplicita
(R.S4); altrimenti dispatcha. Il piano è loggato in `memory/episodic/` per audit.

### §18.7 — Anti-pattern (cosa lo scheduler NON fa)

- **Mai** sostituirsi al ragionamento dell'Arch: lo scheduler ordina TSK già taskizzati,
  non genera architettura parallela.
- **Mai** parallelizzare un TSK con `consumer: human` (umano è single-threaded; lo scheduler
  filtra solo `consumer: agent`).
- **Mai** dedurre dipendenze "implicite" da `wiki_page:` o `related:` (sono soft references).
  Solo `depends_on` esplicito conta.
- **Mai** parallelizzare la fase di scrittura su `wiki/` (single-committer §7 r.12 invariato:
  i worker producono *proposte*; il `wiki-keeper` principale scrive).
- **Mai** auto-merge in caso di conflitto rilevato: surface al chiamante, no silent resolution.

## §19 — Code Quality Review Layer (v2.12)

Il **Code Quality Review Layer** (CQRL) è il meccanismo agent-agnostic con cui la factory
valuta **qualità, idiomaticità e robustezza** del codice prodotto a valle di `Develop` (§3),
distinto e complementare al QA funzionale di `qa-dev` (che copre *correttezza*). Vive in
un nuovo ruolo *Code Reviewer* (§2) opzionale e si materializza in un loop
evaluator-optimizer vincolato con i dev-agent (`max_iterations` default `3`), governato
dalle invarianti §19.6 e dalla regola §7 r.16 (gate umano per verdict `reject`).

CQRL **non sostituisce mai** `qa-dev`: i test funzionali restano un gate ortogonale e
prerequisito. CQRL **non copre la sicurezza** (SAST, dependency scanning, secret
detection): quella resta su un layer dedicato fuori dallo scope v2.12.

### §19.1 — Modello a tre componenti

CQRL è un'istanza specializzata del pattern *orchestrator-workers*: il Quality Reviewer
funge da orchestratore di tre passate cognitive specializzate; il Feedback Router chiude
il loop verso il layer di esecuzione L5.

```
   commit / diff TSK ─────────────┐
                                  ▼
                       ┌───────────────────────┐
                       │  Stack Detector       │ → stack_descriptor
                       └───────────┬───────────┘
                                   ▼
                       ┌───────────────────────┐
                       │  Quality Reviewer     │
                       │   pass 1: idiomaticità│
                       │   pass 2: design      │ → finding[] (JSON schema)
                       │   pass 3: robustezza  │
                       │   aggregator          │ → verdict (pass|conditional|reject)
                       └───────────┬───────────┘
                                   ▼
                       ┌───────────────────────┐
                       │  Feedback Router      │ → task_package OR escalation
                       └───────────────────────┘
```

### §19.2 — Stack Detector

Riconosce **lingua + framework + versione** del codice toccato dal TSK leggendo file di
manifest (`package.json`, `pyproject.toml`, `pom.xml`, `go.mod`, `Cargo.toml`, …), config
del framework (`next.config.js`, `manage.py`, `nest-cli.json`, …), import statement e
signature usate. Produce uno **`stack_descriptor`** strutturato che alimenta tutte le fasi
successive.

Schema:

```json
{
  "language": "python",
  "framework": "fastapi",
  "framework_version": "0.110",
  "secondary_libs": ["pydantic@2.6", "sqlalchemy@2.0"],
  "patterns_expected": ["async", "pydantic_v2", "dependency_injection"],
  "ruleset_id": "python.fastapi.v2",
  "confidence": 0.94
}
```

`confidence` è una stima `[0..1]` di quanto il detector sia sicuro del riconoscimento
(manifest chiari + import coerenti → alto; codice senza manifest → basso). Quando
`confidence < code_quality.thresholds.confidence_min` (default `0.6`) il Reviewer opera
in **modalità degradata** (solo regole language-level, no framework-specific), segnala
l'incertezza nel report e raccomanda intervento umano per chiarire lo stack.

Lo `stack_descriptor` viene **allegato a ogni report** in `code_quality/reports/` per
garantire interpretabilità storica (i report restano leggibili anche dopo evoluzioni
del ruleset).

**Riuso**: lo Stack Detector è invocato anche da `repo-sync` (§16) durante l'estrazione
di un repo esistente per popolare la sezione `## Stack rilevato` del documento di
specifiche.

### §19.3 — Quality Reviewer (3 passate + aggregator)

Tre passate specializzate, ciascuna con schema di prompt a 5 sezioni (`role`, `context`,
`input`, `task`, `output_contract`). Principio: un LLM focalizzato su un singolo obiettivo
cognitivo produce review qualitativamente superiori rispetto a una passata
multi-obiettivo (vedi [[code-quality-review-layer]] §Quality Reviewer).

| Passata | `role` (persona) | Focus | Input deterministici extra |
|---|---|---|---|
| **1 — Idiomaticità** | "Core contributor di `{framework}`" | Astrazioni native, naming convention, style guide della community, no pattern deprecati per la versione | Output linter (`ruff`, `eslint`, `golangci-lint`, `clippy`, …) iniettato come contesto per ridurre allucinazioni su regole base |
| **2 — Design** | "Tech lead che dovrà mantenere il codice nei prossimi 2 anni" | Responsabilità, coesione, accoppiamento, naming, abstraction leak, complessità | Metriche pre-calcolate (complessità ciclomatica, fan-in/fan-out, LOC per funzione) |
| **3 — Robustezza** | "SRE che ha visto questo codice fallire in produzione" | Error handling idiomatico, edge case, resource leak, concorrenza, validazione input, timeout/retry | — |

**Opzionale (v2.16)**: pass aggiuntivo **`premortem-on-merge`** (4° pass, default **off**, opt-in via `code_quality.passes`). Quando attivo, dopo le 3 passate il code-reviewer invoca la skill `premortem-protocol` con scope `diff of TSK-<id>`, `timeframe: 3mo`, `max_findings: 5` → output mini-Risk-Registry come sotto-sezione `### Premortem on Merge` del report standard (non un verdict separato; logica aggregator invariata). **Touchpoint #3**: se verdict aggregator = `conditional` e TSK ha `risk_classification.tier: tiger-*`, il `task_package` al dev-agent include il suggerimento «considera `/premortem` prima del re-Develop» (mai esecuzione automatica — R.P1/R.P3, ADR-005). Vedi `.claude/skills/code-review-protocol.md` Passata 4 e `wiki/concepts/factory-premortem-integration.md §4.4`.

**Aggregator**: NON è una 4a passata costosa. Combina due step:
- **Deterministico**: dedup finding per `(file, lines, rule_id)`; severity aggregata per
  `(file, area)`; applicazione di soglie configurabili per stack.
- **Mini-prompt di consolidamento**: produce *executive summary* (≤ 200 parole) e
  *verdict* finale `pass | conditional | reject`.

Output schema del Reviewer (single file JSON per iterazione):

```json
{
  "tsk_id": "TSK-042",
  "stack_descriptor": { "language": "...", "framework": "...", "confidence": 0.94 },
  "iter": 1,
  "findings": [
    {
      "rule_id": "python.fastapi.dependency_injection.misuse",
      "rule_version": "v3",
      "severity": "high | medium | low",
      "file": "src/auth/handlers.py",
      "lines": [42, 58],
      "rationale": "perché è un problema, citando la regola",
      "fix_complexity": "low | medium | high",
      "auto_fixable": false
    }
  ],
  "verdict": "pass | conditional | reject",
  "summary": "executive summary ≤ 200 parole",
  "generated_at": "ISO-8601",
  "reviewer_version": "code-reviewer@2.12.0"
}
```

Companion digest `.md` umano-leggibile (stesso slug, generato dall'aggregator) è
materializzato in `code_quality/reports/<TSK-id>-iter-<N>.md` con sezioni: Stack
rilevato, Verdict, Finding ordinati, Loop status, Prossimo step.

### §19.4 — Feedback Router

Il dev-agent **non legge il report**. Riceve un **`task_package` machine-readable** che
indica esattamente cosa fare, dove, con quali vincoli e criteri di accettazione. Il
report umano-leggibile resta in `code_quality/reports/` per audit e analytics.

**Ordinamento finding** (default): `(severity DESC, fix_complexity ASC)` — prima le cose
gravi e facili. Massimizza l'impatto del primo round e riduce il rischio di iterazioni
multiple.

**Strategie di batching** (`code_quality.router.strategy`):

| Strategia | Trigger | Effetto |
|---|---|---|
| **all-in-one** | `len(findings) ≤ thresholds.batching_split` (default `7`) | Singolo `task_package`, fix completo in un round |
| **severity-tiered** | `len(findings) > batching_split` | Round multipli: critical/high → medium → low, con mini-review intermedia per round |
| **split-by-area** | Finding distribuiti su moduli con `code_path` disgiunto | N `task_package` paralleli — invocazioni multiple del dev-agent abilitate dallo scheduler §18 (antichain conflict-free su `code_path`) |

**Loop control** (analogo strutturale a [[circuit-breaker]] e [[evaluator-optimizer]]):

| Meccanismo | Trigger | Azione |
|---|---|---|
| **Iteration counter** | `review_iter ≥ code_quality.max_iterations` (default `3`) | Verdict forzato a `reject`, escalation umana (§7 r.16) |
| **No-progress detection** | Due iterazioni consecutive con **stesso set di `rule_id` violate** | Escalation immediata: verdict `reject`, marker speciale `no-progress` nel report |
| **Regression detection** | In iter `N+1` emergono finding nuovi in file **non toccati** dalla fix dell'iter `N` | Flag rosso nel report, raccomandazione di rollback al chiamante umano |

Schema `task_package` (consegnato al dev-agent come input ortogonale al TSK):

```json
{
  "tsk_id": "TSK-042",
  "iter": 2,
  "constraint": {
    "scope": "fix only the findings below; no opportunistic refactor",
    "max_diff_lines": 80
  },
  "actions": [
    {
      "rule_id": "python.fastapi.dependency_injection.misuse",
      "file": "src/auth/handlers.py",
      "lines": [42, 58],
      "expected_fix": "descrizione concisa del fix atteso",
      "acceptance_criteria": "criterio verificabile alla prossima passata"
    }
  ],
  "report_ref": "code_quality/reports/TSK-042-iter-1.md"
}
```

**Feedback all'autore originale**: al termine di ogni iterazione, il Router produce un
**digest aggregato** in `code_quality/reports/_digests/<agent>-<YYYY-WW>.md` (settimanale
per dev-agent) — "sui tuoi ultimi N artefatti, errori frequenti su rule Y". Con memoria
persistente per agente (`memory/semantic/dev-<layer>-recurring-issues.md`), questo è il
canale per migliorare a monte e ridurre il carico futuro sul Reviewer (loop di
auto-miglioramento, non auto-modifica del codice).

### §19.5 — Stack-Aware Ruleset

Knowledge base evolutiva sotto `code_quality/rules/` — **side-channel** (simile a
`memory/`), non un layer del cascade L1→L5. Configurata in
`factory.config.yaml.code_quality.ruleset.path`.

**Tre tier** (priorità crescente):

| Tier | Origine | Stabilità | Priorità |
|---|---|---|---|
| **canonical** | Documentazione ufficiale, best practice consolidate | Alta — cambia raramente | Base |
| **emergent** | Pattern emersi da clustering dei finding storici (loop evolutivo) | Media — sotto osservazione | Sovrascrive canonical se più recente sullo stesso `rule_id` |
| **team-specific** | Convenzioni interne del progetto/team | Variabile | Massima — vince sempre |

**Tassonomia ID**: `{language}.{framework}.{category}.{specific_rule}`. Esempi:
- `python.fastapi.dependency_injection.misuse`
- `typescript.react.hooks.missing_dependency`
- `go.stdlib.error_handling.swallowed_error`
- `java.spring.transaction.scope_leak`

La gerarchia permette query naturali su qualsiasi dimensione (tutti i finding di React,
tutti gli error-handling indipendentemente dallo stack, ecc.).

**Anatomia di una regola** (file `code_quality/rules/<tier>/<rule_id>.md`, frontmatter
+ body):

```yaml
---
rule_id: python.fastapi.dependency_injection.misuse
version: v3
tier: canonical
title: "Misuse of FastAPI Depends() in non-route functions"
applies_to:
  language: python
  framework: fastapi
  framework_version_min: "0.95"
  context: ["routes", "handlers"]
severity_default: high
auto_fixable: false
status: active                    # active | disabled
metadata:
  created_at: "2026-04-12"
  author: "human:soli92"
  trigger_count: 47
  false_positive_count: 3
  last_review: "2026-05-20"
references:
  - "https://fastapi.tiangolo.com/tutorial/dependencies/"
---

# Regola

## Rationale
...

## Detection hints
...

## Examples

### Bad
```python
...
```

### Good
```python
...
```
```

Il campo `metadata.false_positive_count` è la metrica chiave per il **degrado attivo**:
regole con tasso `false_positive / trigger` elevato vengono riformulate o passate a
`status: disabled` (mai eliminate — lo stato resta per audit).

**Loop evolutivo del ruleset** (job periodico, cadenza suggerita settimanale; **NON
automatizzato in v2.12** — gate umano obbligatorio):

1. **Clustering** dei finding recenti per similarità semantica (embedding su `rationale`
   + `rule_id` raggruppati).
2. **Identificazione cluster orfani** — gruppi di finding che non mappano a una
   `rule_id` esistente: candidate per nuove regole `emergent`.
3. **Sintesi candidate rule** — un mini-prompt LLM genera una bozza di regola con
   descrizione, esempi reali tratti dai finding del cluster, detection hints. Scritta in
   `code_quality/rules/emergent/<rule_id>.md` con `status: candidate` (variante esplicita
   di `active`/`disabled` per il workflow di review).
4. **Coda di review umana** — la candidate **non viene attivata automaticamente**.
   L'umano (o un agente arbitro con gate) la promuove `status: candidate → active` o la
   scarta.
5. **Degrado regole rumorose** — regole con `false_positive_count / trigger_count` sopra
   soglia → riformulate o `status: disabled` (vedi sopra).

Il loop evolutivo è un'istanza del pattern [[evaluator-optimizer]]: clustering propone
candidati, coda di review è la condizione di terminazione con gate umano.

**Storage v2.12**: filesystem-based (markdown con frontmatter, retrievable via grep/glob).
Per volumi tipici sotto qualche centinaio di regole è sufficiente. Una migrazione verso
DB relazionale + estensione vettoriale (es. `pgvector`) è valutabile quando i volumi
superano la soglia — vedi [[code-quality-review-runbook]] §Storage e indicizzazione. Mai
sovra-ingegnerizzare prima di avere i volumi.

### §19.6 — Invarianti del Code Reviewer (estensione §7)

Il Code Reviewer **deve** rispettare le seguenti invarianti:

- **R.Q1 — Single source di verdict.** Una sola entry `review TSK-ZZZ iter-N → <verdict>`
  in `wiki/log.md` per iterazione. Mai sovrascrivere; sempre append (§7 r.5). Il
  frontmatter del TSK riflette il verdict più recente (`review_status`, `review_iter`,
  `review_report`); la storia completa vive in `wiki/log.md` + `code_quality/reports/`.
- **R.Q2 — Scope di scrittura chiuso.** Il Code Reviewer non scrive in `<code_path>/**`.
  Output esclusivo: `code_quality/reports/**`, append a `wiki/log.md` (entry `review`),
  e modifica del **solo** `review_status:` / `review_iter:` / `review_report:` nel
  frontmatter di TSK con `consumer: agent` (mai del corpo, mai di TSK altrui). In
  modalità *evolutiva* (gate umano, §19.5 loop step 3), scrive anche
  `code_quality/rules/emergent/**` come bozze candidate (mai `canonical/` né
  `team-specific/`).
- **R.Q3 — Verdict `reject` = gate umano.** Mai auto-revert del codice, mai
  auto-close/auto-merge del TSK, mai riapertura automatica del Develop. Vedi §7 r.16.
- **R.Q4 — Bounded loop.** `code_quality.max_iterations` (default `3`) è invariante non
  bypassabile a runtime. Mai bypass automatico; raggiunto il cap → verdict forzato
  `reject` + escalation. No-progress detection (R.Q4-bis) e regression detection
  (R.Q4-ter) accelerano l'escalation **prima** del cap.
- **R.Q5 — Stack-aware obbligatorio quando `confidence ≥ confidence_min`.** Sopra
  soglia, il Reviewer DEVE applicare il ruleset `{language}.{framework}.*`. Sotto
  soglia, modalità degradata (solo `{language}.*`) con flag esplicito nel report.
  Mai applicare regole framework-specific su stack non riconosciuto.
- **R.Q6 — Ruleset write protetto.** `code_quality/rules/canonical/` e
  `code_quality/rules/team-specific/` sono **write-restricted** (curati a mano o da
  agenti arbitri esplicitamente autorizzati, mai dal Code Reviewer). Il Code Reviewer
  può solo proporre bozze in `emergent/` con gate umano per la promozione (§19.5).
- **R.Q7 — Niente sicurezza.** CQRL **non** copre SAST, dependency scanning, secret
  detection. Un finding che identifica un secret in chiaro o una vulnerabilità nota va
  segnalato in `wiki/incidents/` (post-mortem) e mai mascherato come quality finding.

### §19.7 — `factory.config.yaml.code_quality` (schema v2.12)

```yaml
code_quality:
  enabled: false                  # default false; attivare richiede topology con dev-agent
  max_iterations: 3               # R.Q4 — bounded loop (invariante)
  thresholds:
    confidence_min: 0.6           # R.Q5 — sotto soglia → modalità degradata
    batching_split: 7             # |findings| > N → severity-tiered batching (§19.4)
    pass_rate_warn: 0.05          # pass_rate per stack < N → segnala review theater (§19.8)
    false_positive_warn: 0.30     # fp/trigger > N per regola → flag review umana (§19.5 step 5)
  passes:
    idiomaticity: true            # passata 1 (§19.3)
    design: true                  # passata 2
    robustness: true              # passata 3
  router:
    strategy: severity-tiered     # all-in-one | severity-tiered | split-by-area
    max_diff_lines: 80            # constraint passato al dev-agent nel task_package
    ordering: severity_then_complexity   # default; override possibile via `complexity_then_severity`
  ruleset:
    path: ./code_quality/rules/   # KB locale (v2.12 filesystem-based)
    tiers: [canonical, emergent, team-specific]
    evolve:
      enabled: false              # loop evolutivo §19.5 — manuale in v2.12 (gate umano)
      cadence_days: 7             # cadenza suggerita; nessun cron auto in v2.12
  reports:
    path: ./code_quality/reports/
    retain_iterations: 5          # mantieni gli ultimi N report per TSK (rotation)
    digest_cadence: weekly        # feedback all'autore (§19.4)
```

Coerenza inviolabile (segnalata da `wiki-lint`):
- `code_quality.enabled: true` ⇒ topology include almeno un dev-agent E
  `.claude/agents/code-reviewer.md` presente.
- `code_quality.enabled: false` ⇒ TSK senza `review_status` (campo opzionale); nessun
  contenuto in `code_quality/reports/` atteso.

### §19.8 — Anti-pattern (cosa CQRL NON fa)

- **Review theater**: il Reviewer trova sempre qualcosa anche su codice ottimo, perché
  incentivato a "trovare problemi". *Mitigazione*: budget di severity — verdict `pass`
  pulito quando nessun finding `high`/`medium` realmente fondato; tracciamento
  `pass_rate` per stack (`thresholds.pass_rate_warn`); se cronicamente sotto soglia, il
  layer va ricalibrato.
- **Loop infiniti**: dev-agent e Reviewer si rimbalzano lo stesso artefatto senza
  convergenza. *Mitigazione*: iteration counter + no-progress detection + regression
  detection (R.Q4).
- **Drift fra agenti e standard reali**: lo stack evolve (nuove release del framework),
  il Reviewer applica regole vecchie. *Mitigazione*: campo
  `applies_to.framework_version_min/max` su ogni regola; refresh periodico delle
  `canonical` rule basato su release notes (out-of-scope automatico in v2.12).
- **Fix cosmetico**: il dev-agent sposta righe senza capire il rationale per soddisfare
  formalmente la regola. *Mitigazione*: il `task_package` include `expected_fix` +
  `acceptance_criteria` verificabili; la passata successiva valida il rationale, non il
  pattern testuale.
- **Refactor opportunistico**: il dev-agent modifica codice non richiesto durante il
  fix. *Mitigazione*: `task_package.constraint.scope: "fix only the findings below; no
  opportunistic refactor"` + `max_diff_lines` (default `80`).
- **Hallucinated context**: il Reviewer inventa API o nomi inesistenti nei finding.
  *Mitigazione*: prompt delle 3 passate inietta snippet del codice circostante reale +
  linter output deterministici (vedi §19.3).
- **Auto-modifica del codice**: il Code Reviewer **non** committa fix. Le modifiche al
  codice restano scope esclusivo dei dev-agent (§2). Anche regole con
  `auto_fixable: true` producono `task_package` per il dev-agent, mai patch dirette.

### §19.9 — Integrazione con scheduler (§18)

Il dominio `review` (§18.3) è parallelizzabile per **TSK indipendenti**: due review su
TSK diversi possono girare nello stesso wave se i loro `code_path` non si sovrappongono
(R.S2 conflict-detection §18.4 invariato). Le 3 passate di una singola review sono
sub-skill **interne** (non sub-agent), parallele all'interno dell'unica invocazione del
`code-reviewer`.

`wiki/log.md` (append entry `review`) e `code_quality/reports/<TSK-id>-*.{json,md}`
restano single-committer per file (R.S1, §18.4). Il dispatcher accoda le scritture
report come fa per `develop`.

### §19.10 — Pipeline completa con Review (riepilogo)

```
TSK status:todo  ──Develop──▶  status:done, review_status:pending
                                      │
                                      ▼
                              ┌───────────────┐
                              │ code-reviewer │   (R.Q1-R.Q7)
                              └───────┬───────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
         verdict: pass         verdict: conditional      verdict: reject
              │                       │                       │
              ▼                       ▼                       ▼
   review_status:passed    review_iter+=1, task_package  review_status:rejected
        (chiusura)              ──▶ dev-agent re-invoke      gate umano §7 r.16
                                (loop bounded R.Q4)
```

### §19.11 — Ordering visual oracle → review (v2.17, opt-in)

Per i TSK FE con Visual Oracle attivo, l'ordering è **develop → visual-oracle → review**
(ADR-013): il rendering è epistemicamente più fondamentale del codice, quindi va validato
prima. Concretamente, se `factory.config.yaml.fe_correctness.enabled: true` AND
`TSK.layer: fe`, il visual oracle deve aver prodotto `visual_status: pass` **prima** di
invocare `/review`. La **Fase 0 (Bootstrap) di `code-review-protocol`** guadagna una
**precondition additiva**: se `visual_status != pass` la review è abortita con indicazione
di eseguire `/visual-oracle <TSK-id>` (o attendere la Fase 4-bis di `dev-protocol`). A flag
spento (`fe_correctness.enabled: false`, default) o per TSK non-FE la precondition è skip →
comportamento Fase 0 identico a v2.16. Vedi ADR-009 + ADR-013 Punto 2.

## §20 — Output Compression Layer (v2.14)

L'**Output Compression Layer** (OCL) è il meccanismo agent-agnostic con cui la factory
**riduce i token generati** dagli agenti applicando una grammatica di compressione
linguistica ([[caveman]]) ai soli canali di messaging effimero, lasciando intatti gli
artefatti persistenti (wiki/, management/kanban/, code/, …). È **opt-in**, configurato
in `factory.config.yaml.compression.output`, default `enabled: false`.

OCL **non sostituisce mai** la leggibilità degli artefatti karpathy-style (§7 r.18). È un
layer *trasversale* (intercetta i flussi di comunicazione tra agent e tool) e non un
nuovo verbo di operazione canonica (§3). La compressione è effettuata dal sub-skill
`caveman-protocol` invocato dall'agente prima di emettere un payload verso un altro
agente/tool.

Il design di alto livello vive in [[factory-compression-layer]] (concept wiki); questa
sezione fissa il contratto invariante. Il sotto-asse "context compression" (Graphify) è
**out-of-scope v2.14**: introduzione pianificata per v2.15 (vedi §21 Versioning).

### §20.1 — Modello a un asse (v2.14: solo output)

Caveman intercetta il **payload generato** da un agente prima che venga emesso al
destinatario (sub-agent, tool, orchestrator). Quattro operazioni grammaticali (vedi
[[caveman]] §Logica di funzionamento):

1. Rimozione funzioni grammaticali (articoli, verbi essere, preposizioni deducibili)
2. Eliminazione padding sociale (hedging, cortesia, preamboli, postamboli)
3. Abbreviazioni convenzionali (`fn`, `ret`, `→`, simboli quantificatori)
4. Strutturazione tabellare/lista (prosa non informativa → tabella o lista minimale)

Tre livelli di intensità: `lite` / `full` / `ultra`. L'asse context (Graphify) sarà
introdotto in v2.15.

### §20.2 — Allow-list per canale e policy profile

Il comportamento è **policy-driven** (configurato esternamente, non hardcoded nel codice
degli agent). Tre profili selezionabili in `factory.config.yaml.compression.output.policy_profile`:

| Profilo | Risparmio atteso | Caratteristica | Quando usarlo |
|---|---|---|---|
| `conservative` (**default**) | 50–70% | Drift minimo, audit trail ricco, chain-depth downgrade attivo | Factory new, primo deployment, topologie con chain lunghe |
| `aggressive` | 70–85% | Risparmio massimo, accetta drift cumulativo | Factory mature, topologia `knowledge-only`, dopo ≥ 2 settimane di validazione |
| `custom` | Variabile | Matrice esplicita (override completo dei preset, invarianti restano enforced) | Debugging di un drift specifico, esigenze esotiche |

**Matrice canale × profilo** (R.C2):

| Canale | `conservative` | `aggressive` | Override `custom` |
|---|---|---|---|
| Orchestrator → Sub-agent (dispatch) | `full` | `ultra` | ✓ |
| Sub-agent → Tool (Bash, Read, Grep, …) | `ultra` | `ultra` | ✓ |
| Tool → Sub-agent (result) | `lite` | `full` | ✓ |
| Sub-agent → Orchestrator (return) | `full` | `ultra` | ✓ |
| Sub-agent → Sub-agent (sibling, es. `wiki-keeper-worker` → `wiki-keeper`) | `full` | `ultra` | ✓ |
| `feedback-router` → dev-agent (task package) | `full` | `ultra` | ✓ |
| **Qualsiasi → utente finale (`to_user`)** | `off` | `off` | **NO (R.C1)** |
| **Qualsiasi → file artefatto (`to_artifact`)** | `off` | `off` | **NO (R.C1)** |
| **`propagate-resolution` → wiki page update** | `off` | `off` | **NO (R.C1)** |

`to_artifact` include: `wiki/**`, `management/kanban/**`, `<code_path>/**`,
`design_&_architecture/**`, `code_quality/**`, `memory/**`, `raw/**` (output dei Sync).

### §20.3 — Topology-aware default

Il `policy_profile` ha un default per topologia (modificabile dall'utente):

| Topologia (§13) | Default `policy_profile` | Rationale |
|---|---|---|
| `knowledge-only` | `aggressive` | Chain corte (ingest paralleli), rischio drift basso, no `code_path` |
| `plan-only` | `conservative` | Chain medie (PM → TPM), no `code_path` |
| `full-stack-agents` | `conservative` | Chain lunghe (orch → PM → TPM → dev), `code_path` attivo |
| `hybrid-be-agents` / `hybrid-fe-agents` | `conservative` | Solo il sotto-grafo agentificato beneficia di Caveman |
| `custom` | `conservative` | Default sicuro; utente esplicita override |

In topologie federate ([[federated-topology]]), la compressione **non attraversa il
boundary cross-factory** (R.C4): si applica solo intra-factory.

### §20.4 — Invarianti del Compression Layer (R.C1–R.C6, estensione §7)

Il Compression Layer **deve** rispettare le seguenti invarianti:

- **R.C1 — Invarianti non overridabili.** I canali `to_user`, `to_artifact` (qualsiasi
  scrittura su filesystem persistente) e `propagate-resolution → wiki page update` sono
  **sempre `off`** indipendentemente dal `policy_profile`. Anche in `custom`, l'override
  non può attivare la compressione su questi canali. Vincolato a §7 r.18.
- **R.C2 — Allow-list channel-aware.** Ogni payload emesso da un agente verso un altro
  agente/tool deve essere dichiarativamente associato a un *canale* (vedi tabella
  §20.2). Il `caveman-protocol` applica il livello di compressione configurato per quel
  canale + profilo. Un payload senza canale identificabile → fallback automatico a
  `normal mode` (no Caveman) + warning in `wiki/log.md`.
- **R.C3 — Chain-depth severity ceiling.** Solo nel profilo `conservative` (e in `custom`
  che lo abilita esplicitamente). Quando la chain di handoff (orchestrator → sub-agent →
  sub-agent → tool …) supera profondità `3`, il livello di compressione viene
  auto-degradato di un step (`ultra → full → lite`). Mitigation per drift cumulativo
  (vedi [[caveman]] §Rischi principali). `aggressive` disabilita questo meccanismo.
- **R.C4 — Cross-factory boundary OFF.** In topologie federate ([[federated-topology]]),
  la compressione è **sempre `off`** sull'handoff cross-factory. Modelli o versioni diverse
  fra factory hanno vocabolari Caveman incoerenti → handoff ellittico ambiguo. Questa
  regola non è bypassabile via `policy_profile`.
- **R.C5 — Drift fallback automatico.** Se un sub-agent risponde con marker di ambiguità
  riconosciuto (`AMBIGUOUS_HANDOFF`, `REQUEST_CLARIFY`, exception interpretativa
  documentata), l'orchestrator **rinvia la stessa request in normal mode** (no Caveman) e
  appende a `wiki/log.md` un marker `compression-drift TSK-ZZZ canale=<C> profilo=<P>`.
  Il fallback non termina il loop ma lo riporta a stato non compresso per quel TSK.
- **R.C6 — Opt-in totale, backward compatibility.** `compression.output.enabled: false`
  di default. Factory v2.13- senza il blocco `compression:` in `factory.config.yaml` si
  comportano identiche a v2.13. Nessuna migrazione obbligatoria del frontmatter agent
  o skill. Il campo `caveman_policy:` nei frontmatter agent (§20.6) è **opzionale**:
  agenti senza il campo ricevono il default conservative per il loro canale principale.

### §20.5 — `factory.config.yaml.compression` (schema v2.14)

```yaml
compression:
  output:
    provider: caveman           # caveman | none
    enabled: false              # default OFF, opt-in (R.C6)
    install_command: "curl -fsSL https://raw.githubusercontent.com/JuliusBrussee/caveman/main/install.sh | bash"
    policy_profile: conservative   # conservative | aggressive | custom
    # Invarianti R.C1 — mai overridabili, presenti per esplicitazione documentale:
    invariants:
      to_user: off
      to_artifact: off
      propagate_resolution: off
    # channels: usato SOLO se policy_profile == custom (override completo dei preset)
    channels:
      orchestrator_to_subagent: full
      subagent_to_tool: ultra
      tool_to_subagent: lite
      subagent_to_orchestrator: full
      sibling_to_sibling: full
      feedback_router_to_devagent: full
    chain_depth_downgrade: true       # R.C3 — auto-downgrade su chain depth > 3 (solo conservative)
    chain_depth_threshold: 3
    cross_factory: off                # R.C4 — sempre off in federated; off significa OFF cross-factory, on intra-factory
    drift_fallback:
      enabled: true                   # R.C5 — fallback automatico su marker ambiguità
      markers: [AMBIGUOUS_HANDOFF, REQUEST_CLARIFY]
    audit_trail_for:                  # canali sempre in normal mode anche se policy diversa
      - propagate-resolution
      - feedback-router               # task package interno: tracciabilità completa

  # Asse context (Graphify) — placeholder v2.14, attivazione v2.15
  context:
    provider: none                    # graphify-cloud | graphify-ollama | none (v2.14: solo none)
    enabled: false
```

Coerenza inviolabile (segnalata da `wiki-lint`):
- `compression.output.enabled: true` ⇒ `provider` valorizzato + Caveman installato (test
  empirico: `caveman --version` ritorna OK) + topology compatibile (qualsiasi tranne
  `knowledge-only` con `aggressive` di default).
- `compression.output.policy_profile == custom` ⇒ `channels` block valorizzato
  completamente (no fallback ai preset).
- `compression.output.enabled: false` ⇒ il blocco `channels` viene ignorato; nessun
  agent invoca `caveman-protocol`.

### §20.6 — Frontmatter agent: `caveman_policy:` (opzionale)

Un agent può dichiarare un override locale nel proprio frontmatter:

```yaml
---
name: be-dev
description: Backend developer agent
caveman_policy:                       # opzionale; se assente, default da factory.config.yaml
  to_subagent: full
  to_tool: ultra
  drift_fallback_enabled: true
---
```

Convenzione: il frontmatter agent **non può** mai abilitare un canale che la config
globale ha `off` (R.C1 enforced — il `caveman-protocol` esegue check pre-invocazione).
Può solo *abbassare* il livello (es. `ultra → full`) per debugging di drift locale.

### §20.7 — Integrazione con scheduler (§18)

Il dominio scheduler **`compression`** è introdotto come *concetto* ma non è un dominio
parallelizzabile separato: la compressione è un **intercept inline** nel dispatch della
wave. Per ogni payload emesso dall'orchestrator verso un sub-agent nella wave:

1. Lo scheduler determina il *canale* (es. `orchestrator_to_subagent`)
2. Invoca `caveman-protocol §Fase 2` con `payload`, `channel`, `profile`, `chain_depth`
3. Il payload compresso viene poi passato all'agent come argomento del tool call

Per i `return value` dei sub-agent (sub-agent → orchestrator), la compressione è
applicata dall'agent emittente prima del return (skill `caveman-protocol` chiamata
internamente). Il `wave_report.md` (output di `state-scan` esteso) include una colonna
`tokens_compressed / tokens_raw` per canale per misurare l'efficacia.

`compression.output.enabled: false` → l'intercept è no-op (zero overhead).

### §20.8 — Anti-pattern (cosa OCL NON fa)

- **Compressione su artefatto persistente**: rompe il pattern karpathy-style del wiki/
  e la struttura dei TSK/EP/US. *Mitigazione*: R.C1 invariante non overridabile + Check
  4k del lint (verifica enforcement R.C1 in caveman-protocol invocations).
- **Compressione `ultra` su chain depth > 3 in conservative**: drift cumulativo
  ambiguità di handoff. *Mitigazione*: R.C3 chain-depth severity ceiling automatico.
- **Compressione su modelli non testati**: Caveman è progettato per fraseggio
  Claude/GPT; modelli diversamente fine-tunati possono produrre output ambigui.
  *Mitigazione*: in `caveman-protocol` Fase 1 (Bootstrap) test empirico su `caveman
  --version` + matrice di compatibilità documentata; per modelli non testati, fallback
  conservativo a `lite`.
- **Allow-list inconsistente fra profilo e custom**: utente attiva `custom` ma dimentica
  campi → fallback ai preset oscuro. *Mitigazione*: `wiki-lint` Check 4k segnala
  `channels` block incompleto in `custom` come ERROR (no auto-fallback).
- **Auto-modifica delle policy a runtime**: nessun agente può scrivere
  `factory.config.yaml.compression.*` (config è user-controlled, §8). Solo via
  `/compression set <param> <value>` con gate umano.
- **Ignorare drift fallback marker**: orchestrator ignora `AMBIGUOUS_HANDOFF` e
  continua in compressed mode. *Mitigazione*: R.C5 fallback automatico obbligatorio
  con marker `compression-drift` in `wiki/log.md`.

### §20.9 — Pipeline completa con OCL attivo (riepilogo)

```
       config: compression.output.enabled: true, policy_profile: conservative
                                          │
              Orchestrator emette payload  │  → caveman-protocol intercept
                                          ▼
                         ┌────────────────────────────────────┐
                         │ caveman-protocol (Fase 2)          │
                         │   - identifica canale              │
                         │   - applica livello dal profilo    │
                         │   - check R.C1 invariants          │
                         │   - check R.C3 chain depth         │
                         │   - check R.C4 cross-factory       │
                         └────────────────┬───────────────────┘
                                          ▼
                              payload_compressed → sub-agent
                                          │
                              sub-agent risposta
                                          │
                         ┌────────────────┴───────────────────┐
                         │ Risposta contiene marker ambiguità?│
                         └─────┬──────────────────┬───────────┘
                               ▼ NO              ▼ SÌ (R.C5)
                       continua wave    ──────► fallback normal mode
                                                + log compression-drift
```

### §20.10 — Context Compression Layer (v2.14 Fase 2, Graphify code_path)

Il **secondo asse** del Compression Layer riduce i token **passati come contesto** ai
dev-agent (al posto dei file sorgente raw del `code_path`). L'implementazione canonica
è [[graphify]]: trasforma `<code_path>` in un knowledge graph queryabile
(`.graphify-state/code_paths/<slug>/GRAPH_REPORT.md`) che il dev-agent legge come
input contestuale. Opt-in, configurato in `factory.config.yaml.compression.context`.

L'asse context è **complementare e ortogonale** all'asse output (§20.1-§20.9):
Caveman riduce ciò che gli agent *generano*, Graphify riduce ciò che gli agent
*consumano*. Possono coesistere: una factory può attivare uno, l'altro, o entrambi.

#### §20.10.1 — Confidence-gated dispatch

Graphify produce nodi e archi taggati con tre livelli di confidenza:

| Tag | Origine | Affidabilità | Uso |
|---|---|---|---|
| `EXTRACTED` | AST tree-sitter (deterministico) | Alta — riproducibile | Eseguibili: dev-agent in modifica file |
| `INFERRED` | LLM-driven (semantica) | Media — variabile | Esplorativi: design proposals, query NL |
| `AMBIGUOUS` | Conflitto fra sorgenti AST/LLM | Bassa — da rivedere | Audit: code-reviewer in modalità review |

Lo scheduler (§18) applica un **gating per ruolo agent** quando dispatcha un TSK in
modalità context-compression:

| Ruolo agent | Nodi/archi consumati | Esempio operativo |
|---|---|---|
| **Executor** (modifica file: `be-dev`, `fe-dev`, `db-dev`, `qa-dev`) | Solo `EXTRACTED` | Task di refactor/fix con base AST garantita |
| **Explorer** (genera proposte: `lead-architect`, `wiki-query`) | `EXTRACTED` + `INFERRED` | Design ADR, esplorazione codebase per concept proposal |
| **Reviewer** (audit: `code-reviewer`) | Tutto con flag visibile | Review con blast radius analysis |

Specializzazione del pattern [[verifier-as-gate]] applicata al consumo di contesto.

#### §20.10.2 — `factory.config.yaml.compression.context` (schema v2.14 Fase 2)

```yaml
compression:
  context:
    provider: graphify-cloud        # graphify-cloud (default) | graphify-ollama | none
    enabled: false                  # default OFF, opt-in (R.G6)
    package: graphifyy              # safishamsi/graphify (PyPI: doppia y)
    # Privacy: graphify-cloud manda docs/immagini all'API LLM
    # graphify-ollama: locale, 16+ GB VRAM, qualità inferiore (enterprise data residency)
    ollama:
      model: llama3.1:8b
      vram_gb_min: 16
    targets:
      - kind: code_path
        name: backend              # match factory.config.yaml.code_paths[].name
        gitignore_patterns:
          - "*.env"
          - "secrets/**"
      # - kind: wiki                # v2.15 only, gated da PoC karpathy preservation
      #   path: wiki/
    update_strategy: incremental    # incremental | manual | always
    full_rebuild_cron: "0 0 * * 0"  # weekly (R.G4 drift mitigation)
    drift_alert_days: 7             # alert se delta last_ast vs last_full_rebuild > N gg
    ci_strategy:
      mode: cache-with-fallback     # cache-with-fallback (default) | disabled | always-rebuild
      cache_provider: actions       # actions (GitHub) | gitlab | s3 | local
      cache_key_prefix: graphify-state
      stale_threshold_hours: 168    # 7 giorni → fallback scansione filesystem
      full_rebuild_on_demand: true
    confidence_gating:
      executor: [EXTRACTED]
      explorer: [EXTRACTED, INFERRED]
      reviewer: [EXTRACTED, INFERRED, AMBIGUOUS]
    mcp_server:
      enabled: false                # opt-in con MCP runtime
      topology: per-agent           # per-agent (isolato) | shared (factory mature)
      crg_tools_max: 8              # CRG_TOOLS env var (riduce ~25 tool → ~8)
```

Coerenza inviolabile (segnalata da `wiki-lint`, Check 4l v2.15):
- `compression.context.enabled: true` ⇒ `provider` valorizzato + Graphify installato
  (`graphify --version` — binario singola-y, da `pip install graphifyy` doppia-y; o `graphify-ts --version`) + topology include
  almeno un dev-agent + `code_paths` non vuoto.
- `targets` non vuoto se `enabled: true`.
- `targets[i].kind == wiki` ⇒ riservato a v2.15 con PoC karpathy gate (vedi
  [[factory-compression-layer]] §Fase 3a).

#### §20.10.3 — Integrazione con code-reviewer (CQRL, §19)

Il [[code-quality-review-layer]] beneficia di Graphify come pre-check:

- Prima dell'invocazione del Reviewer su un TSK con `status: done`, lo scheduler
  invoca `graphify get_impact_radius(<file>)` per ciascun file toccato dalla fix.
- Output: lista di symbol/file dipendenti (downstream blast radius).
- Il `task_package` (§19.4) include il blast radius come constraint esplicito:
  `"non toccare i symbol [...] senza valutarne l'impatto su [downstream files]"`.
- Riduce il rischio di **regression detection** in iter `N+1` (R.Q4-ter §19.4).

#### §20.10.4 — Drift mitigation

`EXTRACTED` (AST) si aggiorna ad ogni commit via post-commit hook (zero token,
~0.4s/1k file). `INFERRED` (LLM-semantica) richiede full rebuild (2–20 $ token).
Questo causa drift asincrono se non gestito.

| Meccanismo | Frequenza | Costo | Mitigation di |
|---|---|---|---|
| **Incremental update** (AST only) | Post-commit, on-session-start | Zero token | Disallineamento file ↔ AST nodes |
| **Full rebuild semantico** | Cron weekly | 2–20 $ | Drift asincrono concept↔code |
| **Drift monitoring** | Daily check | Zero token | Alert se `delta(last_ast, last_full_rebuild) > drift_alert_days` |
| **Manual trigger** | Post-refactor maggiori | Su richiesta | `/graphify-sync <target> --force` |
| **CI cache-with-fallback** | Ogni pipeline | Zero (cache hit) o nessun build (fallback) | Costo CI |
| **Ghost duplicates dedup** | Post full rebuild | Zero token | Bug noto Graphify (AST↔semantica disagree su ID) |

#### §20.10.5 — Pipeline completa con context compression attiva

```
       config: compression.context.enabled: true, targets: [code_path=backend]
                                          │
                   Dev-agent invocato su TSK (status:todo, target:backend)
                                          ▼
                         ┌────────────────────────────────────┐
                         │ Scheduler context-resolve          │
                         │  read .graphify-state/code_paths/  │
                         │  filtra per confidence_gating role │
                         │  (executor → EXTRACTED only)       │
                         └────────────────┬───────────────────┘
                                          ▼
                              GRAPH_REPORT.md filtered → dev-agent
                                          │   (al posto dei file sorgente raw)
                                          ▼
                              Dev-agent legge contesto compresso
                                          │   (es. 200k token raw → 3k token graph)
                                          ▼
                              Dev-agent scrive file in <code_path>
                                          │
                              Post-commit hook: incremental update
                                          │   (.graphify-state/ aggiornato AST)
                                          ▼
                              [opzionale] Code reviewer:
                                  get_impact_radius(<files>) → blast radius
                                  task_package include constraint
```

### §20.11 — Invarianti del Context Compression (R.G1–R.G6, v2.14 Fase 2)

L'asse context **deve** rispettare le seguenti invarianti (parallele a R.C1–R.C6
dell'asse output):

- **R.G1 — Filesystem è single source of truth.** `.graphify-state/` è una *view
  derivata*, mai authoritative. In caso di conflitto graph ↔ filesystem (es. node
  nel graph che non corrisponde a un file reale, o file presente ma assente nel
  graph) → **vince filesystem**, graph viene rebuiltato. Mai sincronizzazione
  bidirezionale.
- **R.G2 — Confidence-gated dispatch obbligatorio.** Lo scheduler DEVE filtrare i
  nodi del graph per `confidence_gating` in base al ruolo dell'agent destinatario
  (executor/explorer/reviewer, §20.10.1). Mai consegnare `INFERRED` o `AMBIGUOUS`
  a un executor agent (R.G2 enforced dal `caveman-protocol §Fase 2` analogo per
  context). Violazione = chain di modifiche basate su relazioni inferite incerte.
- **R.G3 — Blast radius pre-check su modifiche.** Se `compression.context.enabled:
  true`, prima di ogni modifica del codice da parte di un dev-agent, lo scheduler
  invoca `graphify get_impact_radius(<files_to_modify>)` e include il risultato nel
  task_package del dev-agent come constraint esplicito (vedi §20.10.3). Il
  dev-agent può rifiutare la modifica se il blast radius eccede la soglia
  (`max_diff_lines × downstream_depth`). Mai fix silente con blast radius elevato.
- **R.G4 — Drift mitigation obbligatoria.** L'asse context DEVE prevedere:
  - Incremental update post-commit (via git hook o session-start, zero token)
  - Full rebuild semantico periodico (cron weekly default)
  - Drift monitoring (daily check di `delta(last_ast, last_full_rebuild)`)
  - Alert quando delta > `drift_alert_days` (default 7)
  In assenza di queste 4 protezioni → ERROR di config (segnalato da `wiki-lint`
  Check 4l).
- **R.G5 — Side-channel write-restricted.** Solo `graphify-sync` scrive in
  `.graphify-state/**`. Nessun altro agent (dev, code-reviewer, wiki-keeper, …) ci
  scrive. Il path è `.gitignore`-d in fase di scaffolding (R.G6 backward compat).
  Lettura aperta a tutti gli agent che hanno `<code_path>/**` o `wiki/**` nel proprio
  read scope.
- **R.G6 — Opt-in totale, backward compatibility.** `compression.context.enabled:
  false` di default. Factory v2.14 Fase 1-only (solo output axis) senza il blocco
  `context:` valorizzato si comportano identiche. Migration v2.14 Fase 1 → Fase 2
  è additiva: nessun cambio breaking del frontmatter agent / skill esistenti.
  `.graphify-state/` viene aggiunto al `.gitignore` come parte del bootstrap Fase 2
  (mai versionato).

## §21 — Versioning

- **v2.17** (questa): **Visual Oracle Integration** (opt-in, EP-005/EP-006). Nuovi 3 campi frontmatter TSK opzionali additivi `visual_status` / `interaction_test_spec` / `visual_reference` (§5; single-writer `visual_status` = skill `visual-oracle-protocol`, analogo `review_status`; default implicito assente = `pending`). Nuova variante di Develop FE **«Visual Verification»** (§3, sub-step Fase 4-bis di `dev-protocol`) che chiude il loop visivo prima del `done`. Nuova nota ordering **develop → visual-oracle → review** (§19.11) con precondition additiva in `code-review-protocol` Fase 0 quando `fe_correctness.enabled: true` AND `TSK.layer: fe`. Side-channel report riusa `code_quality/reports/` con slug `visual` (cartelle PNG non versionate, file `.json`/`.md` versionati). **Niente nuova invariante §7** (sezione §7 invariata rispetto a v2.16; tutto opt-in, backward compat totale — `fe_correctness.enabled: false` default → comportamento v2.16 identico). Vedi ADR-012 (schema dati) + ADR-013 (ordering 3 punti) + ADR-009.
- **v2.16**: **Premortem Integration** (opt-in, Opzione B skill standalone). Nuova skill `premortem-protocol` (operazione opzionale §3, 5 fasi: Context Gathering → Frame Setting → Raw Premortem → Parallel Deep-Dives → Sintesi) + comando `/premortem` invocabile su 3 input shape (descrizione libera / artefatto kanban EP-US-TSK / pagina wiki). Output: Risk Registry strutturato con tassonomia Tigers/Paper Tigers/Elephants ([[risk-classification-tigers-paper-tigers-elephants]]). Nuovo blocco frontmatter opzionale `risk_classification:` (§5) su EP/US/TSK (6 enum tier + `premortem_ref` + `reviewed_by`). Nuovo lint **Check 4m** WARNING-only (coerenza `risk_classification` ↔ Risk Registry; 3 sotto-check, mai ERROR). Nuovo dominio scheduler **`premortem`** (§18.3, default `parallel`, composizione N×M sub-agent — cap interno Fase 4 `max_parallel: 8` hardcoded ADR-001). Pass CQRL opzionale **`premortem-on-merge`** (§19.3, 4° pass, default **off** — ADR-005). Telemetria d'uso `memory/episodic/premortem-runs.md` (single-file append-only, metadati only — ADR-006). Nuovo template `management/risk-registry.md` (append-only, schema 9 colonne — ADR-002). Nuovo seed `meta-prompts/v2-16/factory-bootstrap.md` (**estende v2-15** con la sola Fase 1.quater opt-in; la bozza del design proponeva v2-13 ma è stata corretta in Develop per non perdere il Compression Layer). Dispatcher `/factory-bootstrap` default `v2-16` (branch v2-11..v2-16). **Niente nuova invariante §7** (R.P1 output mai auto-applicato / R.P2 bar minimo fail-loud / R.P3 opt-in totale vivono nella skill, non in §7 — coerente con la filosofia opt-in deferred di v2.15). **Niente gate auto-enforcing**: `/premortem` è sempre esplicito (ADR-003, no phrase-trigger). Backward compat totale: factory che non opta-in si comporta identica a v2.15 (verificato TSK-009: `/lint` v2.15-only = 0 nuove ERROR/WARNING; Check 4m no-op senza blocco). v2.17+ valuterà promozione a operazione canonica + gate condizionali su evidenza telemetrica (gap aperto: soglia di rivalutazione non ancora definita — `premortem-v217-promotion-threshold-undefined`). Self-premortem applicato a v2.16 stessa come release gate (TSK-018, calibration valida). Vedi `design_&_architecture/proposta-premortem-integration-v216.md` + ADR-001..ADR-007 + [[premortem-skill]] + [[factory-premortem-integration]] + [[premortem-runbook]].

- **v2.15**: **Consolidation release**. Nessuna nuova feature di framework: bump versione del PATTERN per chiudere il ciclo v2.14 (Output Compression Layer Fase 1 + Context Compression Layer Fase 2) come baseline stabile. **Riformulazione gate empirici**: i due gate Fase 1.5 ([[compression-validation-template]] — validation empirica OCL su factory derivata con sprint reale) e Fase 3a ([[wiki-as-graph-poc-template]] — PoC karpathy preservation, pre-requisito per Fase 3b wiki-as-graph) restano **setup-ready** ma sono ora classificati come **opt-in deferred**: non bloccanti per il consolidamento del PATTERN, eseguibili a discrezione del derivatore quando dispone di factory candidata + parametri adeguati per misurazione. Il loro stato «pending run empirico» smette di essere un blocker della versione e diventa una *gate aperta che chiunque abbia setup adeguato può eseguire e poi proporre come input per v2.16+*. Motivazione: il meta-framework stesso non ha kanban significativo né sprint reale per essere candidate di validation, e applicazioni concrete del framework non hanno necessariamente parametri di baseline misurabili — bloccare il consolidamento del PATTERN sulla validation empirica significherebbe lasciare v2.14 in stato «WIP» indefinitamente. Tutti gli invarianti R.C1-R.C6 (output) + R.G1-R.G6 (context) restano in vigore identici. Default `compression.output.enabled: false` + `compression.context.enabled: false` invariati. Backward compat totale verso v2.14 (factory v2.14 si comportano identiche su v2.15; nessuna migration necessaria; solo aggiornamento referenze versione). Asse `wiki` come target Graphify (Fase 3b) resta gated da Fase 3a PoC come prima — la riformulazione del gate ne sposta l'attivazione ai termini «*se eseguita con esito positivo*», non «*quando eseguita*»; in assenza di esecuzione il gate resta chiuso. Vedi [[factory-compression-layer]] §«v2.15 consolidation» + [[migration-v215]] (runbook futuro, opzionale: la migration v2.14 → v2.15 è no-op di codice).
- **v2.14**: Compression Layer a due assi (§20) opt-in. **Fase 1 — Output Compression Layer** via [[caveman]] skill (§20.1-§20.9): meccanismo per ridurre i token generati su canali messaging agent-to-agent senza toccare gli artefatti persistenti. Tre `policy_profile` selezionabili: `conservative` (default, drift minimo + chain-depth severity ceiling), `aggressive` (risparmio massimo, factory mature), `custom` (matrice esplicita). Sei invarianti R.C1-R.C6 (§20.4 — invarianti non overridabili `to_user`/`to_artifact`/`propagate-resolution` sempre off, allow-list channel-aware, chain-depth ceiling, cross-factory off, drift fallback automatico su marker ambiguità, opt-in totale). Nuova regola §7 r.18 (compression mai sugli artefatti). Nuovo campo frontmatter agent opzionale `caveman_policy:` (§20.6) per override locale (può solo abbassare, mai abilitare canali R.C1). Nuova skill `caveman-protocol` (5 fasi: Bootstrap → Identify Channel → Apply Compression → Drift Check → Log) + nuovo comando `/compression [show|set|policy|dry-run]`. Hook in `parallel-scheduling` (§20.7): intercept inline nel dispatch della wave, calcolo `tokens_compressed/tokens_raw` per canale nel `wave_report.md`. Topology-aware default (§20.3): `knowledge-only` → `aggressive`, full-stack/hybrid → `conservative`. `wiki-lint` nuovo Check 4k (coerenza `policy_profile == custom` ⇒ `channels` block completo; R.C1 invariants enforced in `caveman-protocol` invocations). Default `compression.output.enabled: false`. **Fase 2 — Context Compression Layer** via [[graphify]] (§20.10-§20.11): quarto sync adapter `graphify-sync` (§16) che estrae knowledge graph da `code_path` (target di `factory.config.yaml.code_paths`) producendo `raw/YYYY-MM-DD-graph-<slug>.md` (summary umano-leggibile) + side-channel `.graphify-state/code_paths/<slug>/` (`graph.json`, `GRAPH_REPORT.md`, `last_full_rebuild.txt`, non versionato in git). Confidence-gated dispatch (§20.10.1): executor agent (dev) ricevono solo `EXTRACTED` (AST deterministico); explorer agent (lead-architect, wiki-query) ricevono `EXTRACTED + INFERRED`; reviewer (code-reviewer) tutto con flag. Sei invarianti R.G1-R.G6 (§20.11 — filesystem single source of truth, confidence-gated dispatch obbligatorio, blast radius pre-check su modifiche, drift mitigation obbligatoria con cron weekly full rebuild + drift monitoring, side-channel write-restricted, opt-in totale). Nuovo agent `graphify-sync` (analogo a `repo-sync` v2.12 + writes side-channel) + nuova skill `graphify-extraction-protocol` (5 fasi: Bootstrap → Discovery → Build Graph → Side-channel write → Log) + nuovo comando `/graphify-sync [<target>|show|status|refresh]`. Integrazione con CQRL (§20.10.3): `get_impact_radius(<file>)` pre-check incluso nel `task_package` come blast radius constraint (R.Q4-ter regression mitigation). Confidence-gated dispatch esteso in `parallel-scheduling` (§20.7 + R.G2). `.graphify-state/` aggiunto al `.gitignore` di bootstrap (R.G6). Default `compression.context.enabled: false`. CI strategy `cache-with-fallback` (zero token su cache hit, fallback scansione filesystem su stale > 7gg, full rebuild solo on-demand). Provider opt-in `graphify-cloud` (default) | `graphify-ollama` (enterprise data residency, 16+ GB VRAM). MCP server `per-agent` (default, isolato) | `shared` (factory mature). Asse `wiki` come target di Graphify (Fase 3, v2.15) gated da PoC karpathy preservation con 4 check non-negoziabili (citation/wikilink/frontmatter/layering); se anche uno fallisce → scartare wiki-as-graph. Fase 1.5 validation on derived factory (gate empirico pre-Fase 2 reale): runbook template [[compression-validation-template]] disponibile per esecuzione su factory derivata v2.14 + Caveman + sprint reale. Backward compat totale verso v2.13 (factory senza i blocchi compression.* si comporta identica). Vedi [[factory-compression-layer]] (design doc + 7 decisioni risolte + aggiornamenti Fase 1 + Fase 1.5) + [[caveman]] + [[graphify]] + [[token-compression]] + [[knowledge-graph-codebase]] + [[migration-v214]] (Fase 1) + [[migration-v214-fase2]] (Fase 2).
- **v2.13**: Multi-adapter scaffolding parallelo (§12 esteso) — formalizzazione del contratto adapter via `adapters/<name>/manifest.yaml`, registry al root del meta-framework con 5 adapter (`.claude/` reference completo, `.cursor/` + `.aider/` full v2.13, `.openai/` partial con setup.py stub, `.gemini/` + `.chatgpt/` manifest-only). Nuovo blocco `factory.config.yaml.adapters:` per dichiarare gli adapter installati nella factory generata. 6 nuove invarianti R.A1-R.A6 (§12.2 — isolamento cartella, state filesystem condiviso, single-committer preservato, manifest immutabile a runtime, adapter aggiungibile a runtime, agent-agnostic preservato). Nuova skill `bootstrap-multiadapter-protocol` come 6° skill del meta-prompt (selezione adapter + loop scaffolding parallelo). Meta-prompt seed riorganizzato: spostato da `~/.claude/factory-bootstrap/` (user-level) a `<meta-framework>/meta-prompts/{v2-11,v2-12,v2-13}/` (repo, versionato col PATTERN). Nuova folder `adapters/` al root del meta-framework. Backward compat: `factory.config.yaml` senza blocco `adapters:` assume `[{name: claude, folder: .claude, maturity: full}]`. PATTERN.md, layer L1-L5, e contratti di citazione (§6) restano agent-agnostic (R.A6). Vedi [[multi-adapter-scaffolding]] (concept futuro) + [[migration-v213]] (runbook futuro).
- **v2.12**: Code Quality Review Layer (§19) — nuovo ruolo *Code Reviewer* (`code-reviewer`, §2) opzionale, nuovo verbo `Review` (§3), nuova KB evolutiva `code_quality/rules/` (3 tier: `canonical`/`emergent`/`team-specific` con tassonomia ID gerarchica `{language}.{framework}.{category}.{specific}`), nuovo storage report `code_quality/reports/<TSK-id>-iter-N.{json,md}`. Nuovi campi frontmatter TSK opzionali: `review_status` (`pending|passed|conditional|rejected`), `review_iter`, `review_report` (§5). Nuovo blocco `code_quality:` in `factory.config.yaml` (§19.7) con `max_iterations` (default 3, R.Q4), thresholds (`confidence_min: 0.6`, `batching_split: 7`, `pass_rate_warn: 0.05`), `passes` (idiomaticity/design/robustness, §19.3), `router` (strategy + max_diff_lines, §19.4), `ruleset` + `reports`. Nuovo sync adapter `repo-sync` (§16) per ingerire repo esistenti — output `raw/YYYY-MM-DD-repo-<slug>.md` riusabile dal `wiki-keeper` per kick-off pipeline da codice pre-esistente. Stack Detector (§19.2) riusabile da `repo-sync` per popolare la sezione Stack del documento. Nuova sezione §16 «Bootstrap da repo esistente — coupling modes» con 3 opzioni (`monorepo` | `sibling-new-repo` | `submodule-new-repo`) che determinano deterministicamente `code_paths[i].path` + `vcs.mode`, e 6 invarianti R.B1-R.B6 (no-write a sorgente in modalità decoupling, conferma esplicita in monorepo, repo-sync read-only sempre, coupling immutabile a runtime, agent-agnostic preservato, multi-repo coupling mix con al più una entry monorepo). **Multi-repo (v2.12)**: schema `factory.config.yaml.code_paths:` come lista di entry `{name, path, layers, tags, vcs}` per supportare FE/BE disaccoppiati, microservizi (N BE), micro-frontend (N FE), monorepo logici con pacchetti multipli. Backward compat: `code_path:` singolare (v2.11-) accettato e auto-promosso a singola entry `default`. Nuovo campo TSK frontmatter `target:` (§5) per disambiguare quando ≥ 2 entry coprono lo stesso layer; obbligatorio in caso di ambiguità (segnalato dal lint Check 4j), opzionale altrimenti (auto-derive). `vcs-handoff` opera per-target. Scheduler R.S2 (§18.4) esteso a `(target, code_path)`: TSK con target diversi sono **sempre** conflict-free su file (filesystem disgiunti) — incremento naturale di parallelismo per microservizi/MFE. Nuove regole §7: r.16 (verdict `reject` = gate umano, mai auto-revert/auto-merge) + r.17 (sync read-only generalizzato — `repo-sync` non muta il repo scansionato). Nuove 7 invarianti del Reviewer R.Q1-R.Q7 (§19.6 — single-committer verdict, scope write chiuso, gate umano reject, bounded loop, stack-aware obbligatorio sopra confidence, ruleset write protetto, no security scope). Nuovo dominio scheduler `review` (§18.3) — TSK indipendenti parallelizzabili. Nuovo bootstrap option "wiki feeding source" (factory-bootstrap meta-prompt v2.12): `empty | pdf | figma | existing-repo`. Default `code_quality.enabled: false` (opt-in esplicito anche con topology dev-agent). Retrocompat: TSK pre-v2.12 senza `review_status` trattati come `pending` quando `code_quality.enabled: true`; report assenti per `enabled: false`. Vedi [[code-quality-review-layer]] + [[stack-aware-ruleset]] + [[code-quality-review-runbook]] + [[repo-sync]] (concept futuro) + [[migration-v212]] (runbook futuro).
- **v2.11**: parallel scheduler agent-agnostic basato su DAG di dipendenze dichiarate nei frontmatter (§18). Nuovi campi frontmatter opzionali: `depends_on` (EP/US/TSK), `blocked_by` esteso a TSK, `code_path` (TSK) — §5. Nuova sezione §18 «Parallel scheduling» con modello (DAG `E_dep ∪ E_conf`), algoritmo a 3 step (toposort + level grouping + graph-coloring partition per conflict detection su `code_path`), domini di parallelismo (§18.3), 8 regole inviolabili dello scheduler (R.S1–R.S8 — single-committer preservato, conflict-free su file, cap di fan-out, gate umano sopra threshold, no rollback collaterale, VCS sempre serializzato), nuovo blocco `scheduler:` in `factory.config.yaml`, output wave-plan in chat. L'Orchestrator espande lo scope con `dispatch` parallelo (multi-`Agent` call nello stesso turno). `wiki-lint` nuovo Check 4g (cicli in `depends_on`, drift `## Dependencies` body ↔ `depends_on` frontmatter). Default `scheduler.enabled: true` con `max_parallel: 4`, `parallel_gate_threshold: 3`. Retrocompat: artefatti senza `depends_on` sono trattati come "nessuna dipendenza" → finiscono al level 0; artefatti senza `code_path` sono trattati conservativamente come serializzanti (politica `empty_code_path_policy: serial`). Vedi [[migration-v211]] (runbook futuro) + [[parallel-scheduling]] (concept futuro) + [[dependency-ordered-dag]] (concept esistente, esteso).
- **v2.10**: Publisher adapters multi-target (L3/L4). Nuovo ruolo *Publisher* (§2) pluralizzabile per provider. Nuovo verbo `Publish` (§3). Nuovo campo frontmatter opzionale `external_id:` (§5) per EP/US/TSK. Nuova regola §7 r.15 (gate cross-tool: conferma esplicita prima di create/update batch su provider esterno; mai delete/close automatici; auth solo da env var). Nuovo blocco `kanban_publish:` in `factory.config.yaml`. Nuova sezione §17 «Publisher adapters» con contratto per nuovi adapter. `github-publisher` come implementazione di riferimento via `gh` CLI; placeholder per `gitlab|jira|linear`. `lint-checks` nuovo Check 4f (coerenza `external_id:` ↔ provider config; orphan se `provider: none`). `publisher-protocol` provider-agnostic + `<provider>-mapping` skill provider-specific. Push-only in v2.10; bidirectional candidato v2.11. Vedi [[migration-v210]] (runbook futuro) + [[publisher-adapters]] (concept futuro).
- v2.9: Sync role pluralizzato (multi-source L1). Nuovo sub-agent `figma-sync` per estrazione Figma via Anthropic API + Figma MCP, basato sul pattern [[chunked-extraction-pipeline]] già documentato. Nuovo shape `.kb.json` come artefatto L1 strutturato (oltre a `.txt`). Nuova grammatica citazione `[^src: <path>.kb.json §<dotted-path>]` (§6). Nuovo §16 «Sync adapters» con contratto per nuovi adapter. `ingest-protocol` esteso per leggere `.kb.json` (Fase 1 ramo strutturato). `lint-checks` Check 4e (coerenza manifest ↔ filesystem ↔ source dichiarata). `.extraction-manifest.json` esteso con `source`/`primary_artifact`/`secondary_artifacts`/`extractor_version`. Retrocompat: manifest pre-v2.9 (chiave-piatta) accettato come `source: pdf`; sync-docs aggiorna in-place quando reingerisce. Vedi [[migration-v29]] (runbook futuro) + [[sync-adapters]] (concept futuro).
- v2.8: VCS integration esplicita. Blocco `vcs:` in `factory.config.yaml` (`mode: monorepo|submodule|sibling|external|none`, + opzionali `submodule_path`, `remote_url`, `branch_strategy`, `commit_coupling`). Nuova skill `vcs-handoff` invocata dal `dev-protocol` Fase 5; nuovo lint check 4d (coerenza VCS). Citazione codice prodotto estesa con un terzo formato (`[^src5-sub:`). Regola §7 r.14 nuova (gate umano obbligatorio per scritture VCS distruttive/cross-repo). File `.factory-lock` opzionale (`commit_coupling: pin`) per reproducibilità. Vedi [[migration-v28]] (runbook) + [[vcs-and-code-path]] (synthesis).
- v2.7: execution layer L5, 4 dev-agent opzionali, operazione `Develop`, topologie esplicite, `factory.config.yaml`, stack modes (`manual/guided/auto`), frontmatter TSK `layer:`+`consumer:`. Vedi [[migration-v27]] + [[topology-and-dev-agents]].
- v2.6: gate L4 graduato (`blocking_level: hard|soft`), state propagation downstream (`Propagate` + `reconcile-needed`), auto-promotion suggerita.
- v2.5: operazione `Heal` (evaluator-optimizer vincolato, whitelist chiusa, max 3 iter).
- v2.4: ingest parallelo per batch ≥ 3.
- v2.3: refactor "thin agents, fat skills" (13 skill).
- v2.2: `memory/` tree, rimozione hook bash/python, two-phase commit, wiki-staging.
- v2.1 → v1.0: separazione PATTERN.md/adapter; rimozione `project_manifest.json` + `reviewer`.
