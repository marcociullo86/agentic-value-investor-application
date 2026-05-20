# PATTERN — Agentic Factory `llm-wiki++` v2.8

> Contratto universale agent-agnostic. Qualsiasi runtime (Claude Code, OpenAI Assistants,
> Cursor, Aider, …) che rispetti questo file può operare sul repo. Gli adapter di runtime
> vivono in cartelle dedicate (`.claude/`, `.cursor/`, …) e implementano i ruoli §2.

## §0 — Identità & versione

Pattern version: **2.8**.
Origine: llm-wiki (Karpathy) + estensione PM/Arch + memory tree cross-conversazione +
adapter `thin agents, fat skills` + execution layer L5 + topology selection +
stack modes + VCS integration esplicita.
Scope: knowledge-base eseguibile **e** (opzionale) produzione codice via dev-agent
o consumo umano.
Progetto host: **App Template Demo** (`owner: marco.ciullo`, `language: it`).

## §1 — Modello a layer

- **L1 `raw/`** — PDF + estrazioni `.txt` + immagini. **Immutabile** (solo il ruolo *Sync* scrive `.txt`/`images/`).
- **L2 `wiki/`** — wiki llm-style con `log.md` append-only. Unico autore: ruolo *Analyst* (`wiki-keeper`).
- **L3 `management/`** — `kanban/EP-*/`, `roadmap.md`, `questions.md`. Autore: ruolo *PM*.
- **L4 `design_&_architecture/` + `management/kanban/**/TSK-*.md`** — autore: *Arch* + *TPM*.
- **L5 `<code_path>/`** (v2.7) — codice sorgente. `code_path` configurabile in `factory.config.yaml`, può essere esterno al repo. Autore: ruoli *Dev* (`be-dev`, `fe-dev`, `db-dev`, `qa-dev`) o umani in base al routing §13.
- **`memory/`** — persistenza cross-conversazione (side-channel).

Cascata: ogni layer è derivato dal precedente. L'aggiornamento di Lk rende Lk+1..L5 *stale*.
Se `code_path` è esterno al repo, la cascata si interrompe al boundary del repo
(i dev-agent committano fuori; `wiki/log.md` traccia solo il fatto + commit hash quando disponibile).

## §2 — Ruoli (responsabilità, non file)

Ogni runtime mappa questi ruoli ai propri costrutti (agenti, assistant, modes, …).

**Principio**: `wiki/` è **read-universal** (ogni agente la legge), **write-restricted**
(solo `wiki-keeper` scrive contenuto; eccezioni puntuali). Gli agenti L3+ leggono `wiki/`
per contesto; la disciplina di citazione resta cascade (Arch cita storie, TPM cita
US/ADR — ma possono aprire i concept citati per capirli).

| Ruolo | Legge | Scrive | Trigger |
|---|---|---|---|
| **Orchestrator** | tutto (read-only) | `memory/episodic/**`, `wiki/log.md`, **eccezione**: modifica `status:`/`updated:` frontmatter di `wiki/**/*.md` via operazione `promote` (§3) | richiesta dashboard di stato; operazione `promote` |
| **Sync** | `raw/*.pdf` | `raw/*.txt`, `raw/images/`, `raw/.extraction-manifest.json` | nuovi PDF |
| **Analyst** (`wiki-keeper`) | `raw/**`, `raw/tech_stack.md`, `memory/**`, `wiki/**` + obbligatorio `wiki/gaps.md` ad ogni run | `wiki/**` (escluso `query/`, `lint/`) + append `wiki/log.md` + append `wiki/gaps.md` (chiusura gap) | L1 aggiornato OR gap aperti |
| **PM** | `wiki/**`, `memory/**` | `management/kanban/EP-*/**`, `management/{roadmap,questions}.md`, **append-only**: `wiki/gaps.md` + sezione `## Storie collegate` di pagine wiki | L2 aggiornato |
| **Arch** (`lead-architect`) | `management/kanban/**`, `management/questions.md`, `raw/tech_stack.md`, `memory/**`, **`wiki/**`** (contesto) | `design_&_architecture/**`, **append-only**: `wiki/gaps.md` | L3 OK + gate questions resolved |
| **TPM** (`tpm`) | `design_&_architecture/**`, `management/kanban/**`, `raw/tech_stack.md`, `memory/**`, **`wiki/**`** (contesto) | `management/kanban/**/TSK-*.md`, `management/kanban/sprint.md`, **append-only**: `wiki/gaps.md` | L4 architettura OK |
| **Query** (`wiki-query`) | `wiki/**` (esclusivo) | `wiki/query/` (opt-out con `--ephemeral`) + append `wiki/log.md` + append `wiki/gaps.md` | domanda NL |
| **Lint** (`wiki-lint`) | `wiki/**`, `management/kanban/**`, `design_&_architecture/**`, `factory.config.yaml` | `wiki/lint/` + append `wiki/log.md` | richiesta health check |
| **Dev** (`be-dev`/`fe-dev`/`db-dev`/`qa-dev`) — v2.7, opzionali | `management/kanban/**/TSK-*.md` (filtrato per `layer:` proprio + `consumer: agent`), `design_&_architecture/**`, `raw/tech_stack.md`, `factory.config.yaml`, `<code_path>/**`, `wiki/**` (contesto) | `<code_path>/**` (può essere esterno al repo), append-only: `wiki/log.md`, `wiki/gaps.md`, edit `status:`/`updated:` del proprio TSK | TSK ready (layer match + consumer=agent + status=todo + deps ok); OR comando topology-routed |

## §3 — Operazioni canoniche (verbi)

- **Ingest** = transizione L1 → L2 eseguita da *Sync* + *Analyst*. Per batch ≥ 3 nuovi raw, l'*Analyst* delega l'analisi a sub-agent paralleli; scrittura serializzata (single-committer). Append a `wiki/log.md`.
- **Query** = domanda NL → risposta sintetizzata leggendo solo `wiki/`. Append a `wiki/log.md`.
- **Lint** = health check strutturale di L2+L3+L4+config. Append a `wiki/log.md`.
- **Plan** = transizione L2 → L3 eseguita dal *PM*.
- **Design** + **Execute** = transizione L3 → L4 eseguita da *Arch* (fase 1: architettura) poi *TPM* (fase 2: task atomici).
- **Promote** = transizione di `status:` di una pagina wiki (`draft → review → approved`), eseguita dall'*Orchestrator* come modifica meccanica del frontmatter.
- **Heal** (v2.5) = ciclo evaluator-optimizer vincolato su ERROR meccanici flaggati come `heal-eligible`. Opt-in, gated (gate umano bulk), bounded (max 3 iterazioni). Whitelist chiusa: `broken-wikilink` fuzzy ≥ 0.90, `missing-frontmatter-field` deducibile dal path, `citation-section-mismatch` edit-distance ≤ 3. Append `heal-iter-N` a `wiki/log.md`.
- **Propagate** (v2.6) = riconciliazione downstream quando l'*Analyst* chiude un gap che cita una `Q_NNN`. Skill `propagate-resolution`. Mai scrittura su `management/kanban/**` (proprietà PM). L'*Orchestrator* surfaceizza il marker in dashboard.
- **Develop** (v2.7) = transizione L4 → L5 eseguita da un ruolo Dev. Consuma un singolo TSK con `consumer: agent` + `layer:` corrispondente. Scrittura su `<code_path>/**`. Append a `wiki/log.md` (marker `develop TSK-ZZZ → <commit-hash o path>`). Mai edit del corpo del TSK; solo `status:` (`todo → in-progress → done`).
- **Tech-scout** (v2.7) = proposta automatica di stack via skill omonima. Output: `raw/tech_stack.md.proposal` con citazioni a fonti web datate. Mai auto-applicato: gate umano per promuovere `.proposal` → `raw/tech_stack.md`.
- **VCS-handoff** (v2.8) = proposta di commit/branch cross-repo coordinata fra Develop e VCS. Skill `vcs-handoff`. Procedura per-mode (`monorepo`/`submodule`/`sibling`/`external`/`none`). Mai esecuzione automatica di `push`/`clone`/`submodule add`: gate umano obbligatorio (§7 r.14).

## §4 — Naming conventions

| Artefatto | Pattern |
|---|---|
| PDF | `YYYY-MM-DD-<nome>.pdf` (e `.txt` corrispondente) |
| Figura | `YYYY-MM-DD-<nome>-fig-NN.md` |
| Source page | `wiki/sources/<kebab-slug>.md` |
| Concept page | `wiki/concepts/<kebab-slug>.md` |
| Entity page | `wiki/entities/<kebab-slug>.md` |
| Synthesis page | `wiki/syntheses/<kebab-question>.md` |
| Runbook | `wiki/runbooks/<kebab-slug>.md` |
| Incident | `wiki/incidents/YYYY-MM-DD-<kebab-slug>.md` |
| Epica | `management/kanban/EP-XXX-<slug>/EP-XXX.md` |
| Storia | `management/kanban/EP-XXX-<slug>/US-YYY-<slug>/US-YYY.md` |
| Task | `management/kanban/EP-XXX-<slug>/US-YYY-<slug>/TSK-ZZZ.md` |
| ADR | `design_&_architecture/decisions/ADR-NNN.md` |
| Memoria episodica | `memory/episodic/YYYY-MM-DD-HH-MM-<slug>.md` |
| Memoria semantica | `memory/semantic/<slug>.md` |
| Memoria procedurale | `memory/procedural/<slug>.md` |

Slug: lowercase, spazi→`-`, rimuovi `()/'`, max 40 char. XXX/YYY/ZZZ/NNN = 3 cifre zero-padded.

## §5 — Frontmatter (minimo necessario, deduci dal path quando possibile)

- **Wiki page:** `type`, `sources` (array), `status` (`draft|review|approved`)
- **Epica:** `id`, `title`, `status`, `priority`, `confidence`, `confidence_rationale`, `wiki_pages`, `created`
- **User Story:** `id`, `title`, `role`, `priority`, `status`, `wiki_page`, `blocked_by` (`epic` deducibile dal path)
- **Task (v2.7):** `id`, `sprint`, `layer` (`be|fe|db|qa|infra`), `consumer` (`agent|human`), `priority`, `estimate`, `status` (`story`/`epic` deducibili dal path; `team` deprecato dalla v2.7)
- **ADR:** `id`, `title`, `status` (`proposed|accepted|superseded|deprecated`), `created`, `deciders`
- **Figura:** `source_pdf`, `page`, `figure_number`, `type`
- **Memoria:** `type` (`episodic`/`semantic`/`procedural`), `created`, `tags`

Regola: `id` e `status` (dove applicabile) sono **sempre obbligatori**; tutto il resto deducibile dal path va rimosso.

## §6 — Grammatica delle citazioni

- Citazione fonte: `[^src: <path-relativo>.md §<sezione>]` su ogni claim ≥ 20 parole.
- Link interno wiki: `[[nome-pagina-senza-estensione]]`, **mai** path relativi `../../`.
- Citazione codice (factory): `[^code: <path>:<line>]`.
- Claim senza citazione = claim invalido (segnalato dal *Lint*, mai bloccato deterministicamente — il framework opera in regime LLM-trust).

## §7 — Regole inviolabili (14, v2.8)

1. **L1 read-only** (eccetto *Sync*).
2. **Zero invenzione.** Info assente → `wiki/gaps.md` o `management/questions.md`.
3. **Citazione obbligatoria** su ogni claim non triviale.
4. **Wikilink** per link interni, mai path relativi.
5. **`wiki/log.md` append-only.** Stesso vincolo per `wiki/gaps.md` e `wiki/incidents/`.
6. **Report preliminare e STOP** prima di scrivere file in batch.
7. **Update non distruttivo** su pagine `review|approved`: aggiungi `## Aggiornamenti (vYYYY-MM-DD)`.
8. **Scope di scrittura chiuso** per ruolo (§2). I dev-agent scrivono solo `<code_path>/**` + `status:`/`updated:` del proprio TSK.
9. **Gate L4 graduato (`blocking_level`, v2.6).** Q `hard` aperta blocca US dipendenti; *soft* lascia procedere con `pending_clarification`. Analogo gate per L5.
10. **`raw/tech_stack.md` priorità assoluta.** SAML/OIDC/SOAP/SPID/eIDAS citati non si sostituiscono. La skill `tech-scout` propone, mai applica.
11. **`memory/` non è `wiki/`.**
12. **`wiki/` è read-universal**, **single-committer** (§10). Eccezioni: PM su `## Storie collegate`, orchestrator su `status:` via `promote`, append-only di L3+ su `wiki/gaps.md`, entry `develop` di dev-agent su `wiki/log.md`.
13. **Topology e routing dichiarati (v2.7).** Se esistono dev-agent nell'adapter, deve esistere `factory.config.yaml` con `topology:`, `code_path:`, `routing:` coerenti. Un dev-agent può rifiutarsi di operare se il TSK non ha `layer:` + `consumer:` espliciti.
14. **VCS dichiarato (v2.8).** Se `code_path:` è valorizzato, DEVE esistere `vcs.mode:` in `factory.config.yaml` (`monorepo | submodule | sibling | external | none`). Nessuna operazione `git submodule add|update`, `git clone`, `git push`, `git commit --amend`, o force-push viene MAI eseguita automaticamente: la skill `vcs-handoff` propone, l'umano conferma (gate non bypassabile per scritture VCS distruttive o cross-repo).

## §8 — State derivation (single source of truth)

Lo stato del progetto si deduce SOLO da:

- Filesystem (presenza/assenza di file e cartelle, **inclusa la presenza di agenti dev nell'adapter** che codifica la topologia).
- `wiki/log.md` (ultima entry per tipo di operazione).
- `memory/episodic/` (ultimo run rilevante).
- Data modifica file (`git log` o `stat`).
- `factory.config.yaml` (configurazione, **non stato** — vedi distinzione sotto).

**Vietato:** `project_manifest.json` o qualsiasi file di stato scritto a mano.
**Vietato:** doppia source-of-truth.

**Distinzione config vs stato (v2.7)**: `factory.config.yaml` è configurazione utente
(topology, code_path, routing, stack_mode, vcs) sotto controllo umano — non descrive
*cosa è stato fatto* (stato) ma *come la factory è configurata* (config).

## §9 — Memoria cross-conversazione

- **`memory/episodic/`** — record narrativo del run (chi è stato invocato, perché, esito). Scritto dall'*Orchestrator*. Letto dai run successivi per continuità.
- **`memory/semantic/`** — fatti consolidati cross-progetto (es. "preferiamo OIDC per federated auth"). Promossi da episodic dopo validazione umana.
- **`memory/procedural/`** — playbook riutilizzabili (es. "come spezzare una storia troppo grande"). Curati a mano o distillati da run riusciti.

Distinto da `wiki/log.md` (narrazione operativa) e da `wiki/incidents/` (post-mortem operativi).

## §10 — Wiki maintenance & feedback loop

`wiki/` è la **source of truth** del progetto. Per restare tale:

1. **Accessibile a tutti** (read-universal, vedi §2). Nessun agente lavora alla cieca sulla layer compilata: ognuno può aprire concept/entity/synthesis per contesto. La disciplina di citazione cascade resta intatta.
2. **Manutenuta con disciplina stringente** (write-restricted, single-committer). Solo `wiki-keeper` scrive contenuto; eccezioni meccaniche (PM su `## Storie collegate`, orchestrator su `status:` frontmatter via operazione `promote`).
3. **Aggiornabile via feedback loop**. `wiki/gaps.md` è il canale formale: ogni agente L3+ che identifica un gap di knowledge base lo formalizza qui (append-only condiviso), il `wiki-keeper` lo legge all'inizio di ogni run.

### Formato gap canonico

```markdown
## YYYY-MM-DD HH:MM — <slug-gap>
**Origine:** <agente> @ <artefatto in lavorazione>
**Gap:** <cosa manca in wiki/>
**Sospetta fonte:** <raw da ingerire | "nessuna fonte chiara">
**Impatto:** <quale produzione è frenata>
**Bloccante:** sì | no  (se sì, riferisci Q_NNN)
```

Quando colmato: `**Risolto:** YYYY-MM-DD — [[<pagina>]]` + log entry `gap-closed`.

### Eventi che innescano un update wiki/

| Evento | Trigger | Chi | Cosa |
|---|---|---|---|
| Nuovo PDF | Sync completato | wiki-keeper | Ingest L1→L2 |
| Re-ingest | log segnala precedente ingest | wiki-keeper | Append `## Aggiornamenti (vYYYY-MM-DD)` |
| Gap segnalato | append a `wiki/gaps.md` | wiki-keeper | Ingest mirato o nuova synthesis |
| Storia creata | PM completa US | product-manager | Append `## Storie collegate` a wiki page |
| Synthesis candidata | risposta ri-askable | wiki-keeper su proposta di query | Promote query/ → syntheses/ |
| Promotion status | operazione `promote` (§3) | orchestrator | Modifica solo `status:`+`updated:` frontmatter |
| Auto-promotion suggerita (v2.6) | concept citata da ≥ 2 US committed/in-progress | orchestrator (suggerimento dashboard) | Surface "Considera promote review" in dashboard |
| Gap chiuso che cita `Q_NNN` (v2.6) | wiki-keeper chiude gap → invoca `propagate-resolution` | wiki-keeper (append-only log) | Append marker `reconcile-needed: US-XXX → Q_NNN closed` |
| Develop completato (v2.7) | dev-agent chiude TSK → `dev-handoff` | `<layer>-dev` | Append entry `develop TSK-ZZZ → <commit-hash o path>` a `wiki/log.md` |

### Invarianti di manutenzione

- **Append-only** su `wiki/log.md`, `wiki/gaps.md`, `wiki/incidents/`.
- **Non distruttivo** su pagine `review`/`approved`: aggiungi `## Aggiornamenti (vYYYY-MM-DD)`.
- **Touch many small files**: un ingest sano produce 5–15 piccole pagine, non una mega-pagina.
- **Flag, don't resolve**: contraddizioni tra fonti vanno in `## Contradictions`, non risolte silenziosamente.
- **Citation chain integrity**: ogni claim in `management/` e `design_&_architecture/` traccia transitivamente fino a `raw/` via `wiki/`. Il `wiki-lint` verifica periodicamente.

## §11 — Standards as constraints (tenant-driven)

Quando un raw cita uno standard normativo (SPID, OIDC, OAuth2, SAML, eIDAS, FHIR, GDPR, HL7, ISO/IEC, RFC numerati), il `lead-architect` deve trattarlo come **vincolo verbatim** e produrre un ADR che lo adotta esplicitamente. Sostituire silenziosamente uno standard con "un equivalente" (es. JWT custom in scope OIDC) è una violazione del contratto — il `wiki-lint` e il revisore umano lo segnalano.

## §12 — Adapter (runtime-specific)

Ogni adapter implementa i ruoli §2 con i costrutti del proprio runtime:

- `.claude/` — Claude Code: agents + skills + commands (adapter di default)
- `.cursor/` — adapter Cursor (futuro)
- `.openai/` — adapter OpenAI Assistants (futuro)
- `.aider/` — adapter Aider (futuro)

Più adapter possono coesistere sullo stesso repo: condividono `raw/`, `wiki/`, `management/`,
`design_&_architecture/`, `memory/`. Un adapter è "conforme" se rispetta scope §2, gate §7,
naming §4, frontmatter §5.

**Principio di taglio adapter**: gli agenti sono **identità contrattuali** (scope, trigger,
modello); le procedure ricorrenti vivono in **skill** (single source of truth). Una stessa
procedura non è mai duplicata fra agenti.

## §13 — Topology & consumer routing (v2.7)

La topologia è codificata da: (a) presenza dei file dev-agent nell'adapter,
(b) campo `topology:` di `factory.config.yaml`. Coerenza fra i due verificata dal *Lint*.

Topologie: `knowledge-only` | `plan-only` | `full-stack-agents` |
`hybrid-be-agents` | `hybrid-fe-agents` | `custom`.

`factory.config.yaml` (schema minimo v2.8):

```yaml
pattern_version: "2.8"
topology: <una delle sei sopra>
code_path: "./src/" | "/abs/path/outside-repo/" | ""
stack_mode: manual | guided | auto
routing:
  be: agent | human
  fe: agent | human
  db: agent | human
  qa: agent | human
  infra: agent | human
stack:
  backend: "..."
  frontend: "..."
  database: "..."
  qa: "..."
vcs:
  mode: monorepo | submodule | sibling | external | none
  submodule_path: "..."        # opzionale, solo per submodule
  remote_url: "..."            # opzionale
  branch_strategy: shared | per-tsk | per-sprint
  commit_coupling: pin | float
```

TPM applica `consumer: <routing[layer]>` come default ai TSK; override puntuale ammesso.
Un comando topology-routed può forzare un dev-agent one-shot anche su TSK con
`consumer: human` (senza modificare il file).

## §14 — Tech stack modes (v2.7)

- **`manual`**: `raw/tech_stack.md` scritto a mano.
- **`guided`**: bootstrap mostra opzioni curate per layer (FastAPI/Express/Spring; React/Vue/Svelte; PostgreSQL/MongoDB/SQLite; ...) e l'utente sceglie.
- **`auto`**: skill `tech-scout` legge wiki + ricerca web fonti 2026 → `raw/tech_stack.md.proposal` (mai overwrite, gate umano). Standards normativi sempre verbatim.

## §15 — Versioning

- **v2.8** (questa): VCS integration esplicita. Blocco `vcs:` in `factory.config.yaml`. Skill `vcs-handoff`. Lint check 4d. Regola §7 r.14 nuova (gate umano obbligatorio per scritture VCS).
- v2.7: execution layer L5, 4 dev-agent opzionali, operazioni `Develop` + `Tech-scout`, topologie esplicite, `factory.config.yaml`, frontmatter TSK con `layer:`+`consumer:`. Regola §7 r.13 nuova.
- v2.6: gate L4 graduato (`blocking_level: hard|soft`), operazione `Propagate`, auto-promotion suggerita.
- v2.5: operazione `Heal` (evaluator-optimizer).
- v2.4: ingest parallelo (batch ≥ 3), single-committer.
- v2.3: refactor thin agents, fat skills (13 skill).
- v2.2: memory tree, rimozione hook/two-phase commit.
- v2.1 → v1.0: legacy.
