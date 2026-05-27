---
name: scrivi-task
description: Template per un task TSK-ZZZ.md atomico, contractuale.
---
# Procedura per scrivere un task

Riferimenti: `citation-rules` (cascade: il task cita US/ADR/design).

## Path

`management/kanban/EP-XXX-<slug>/US-YYY-<slug>/TSK-ZZZ.md`

## Frontmatter (minimal, v2.11)

```yaml
---
id: TSK-ZZZ
sprint: NN
layer: be | fe | db | qa | infra
consumer: agent | human
priority: P0 | P1 | P2
estimate: XS | S | M | L
status: todo | in-progress | done
depends_on: []           # v2.11: lista TSK prerequisiti (es. [TSK-007, TSK-012]) — hard block
blocked_by: []           # v2.11: lista Q_NNN hard aperte che bloccano il TSK
code_path: []            # v2.11: lista path/glob L5 toccati (es. ["src/auth/**", "src/api/v1/login.py"])
---
```

Note:
- `story` ed `epic` deducibili dal path.
- `layer` sostituisce il vecchio `team` (deprecato in v2.7).
- `consumer` = default da `factory.config.yaml.routing.<layer>`. Override esplicito ammesso.
- Se la topologia non include un dev-agent per `<layer>` ma `consumer: agent`,
  il `wiki-lint` segnalerà incoerenza.

### Campi v2.11 — input per il parallel scheduler (PATTERN §18)

- **`depends_on: [TSK-XXX, ...]`** — TSK che DEVONO essere `status: done` prima
  che questo possa partire. Hard dependency. Lo scheduler usa questa lista per
  costruire il DAG (toposort + level grouping). **Sezione `## Dependencies` del
  body è ora derivata da questo campo, non canonica.** Se entrambi presenti,
  vince il frontmatter; `wiki-lint` segnala drift.
- **`blocked_by: [Q_NNN, ...]`** — `Q_NNN` con `Bloccante: hard` aperte che
  toccano il TSK. Equivalente al campo omonimo su US (vedi `scrivi-user-story`).
  Risolta la Q (gap chiuso da `propagate-resolution`), va rimossa.
- **`code_path: ["<glob>", ...]`** — path/glob in `<code_path>/**` che questo TSK
  prevede di toccare in scrittura. Lo scheduler li confronta fra TSK candidate
  allo stesso level: overlap di glob ≠ ∅ → non parallelizzabili (race su file).
  Lista vuota = "scope sconosciuto" → lo scheduler tratta il TSK come
  serializzante (conservativo). Esempi: `["src/auth/**"]`, `["db/migrations/0042_*.sql"]`,
  `["tests/e2e/login.spec.ts"]`. Usa glob, non path assoluti.

## Corpo

```markdown
# TSK-ZZZ — <Titolo conciso>

## Context
<US riferita, perché serve questo task>
[^src: management/kanban/EP-XXX-<slug>/US-YYY-<slug>/US-YYY.md §AC]

## Technical Specs
- **BE:** endpoint OpenAPI specifico → `POST /api/v1/foo` ([openapi_schema.yaml §paths./foo](../../../../design_&_architecture/api_specs/openapi_schema.yaml))
- **FE:** pagina/componente → `LoginPage` consuma `POST /api/v1/auth/login`
- **DB:** tabelle impattate
- **Auth:** ruoli abilitati

## Implementation Steps
1. <step 1>
2. <step 2>
3. <step 3>

## Definition of Done
- [ ] Test unitario passa
- [ ] Test integrazione passa
- [ ] Documentazione aggiornata
- [ ] Code review approvata

## Dependencies
<!-- v2.11: lista derivata dal frontmatter `depends_on:` + `blocked_by:`.
     Questa sezione è OPZIONALE e serve solo a esporre rationale umano-leggibile
     (es. "TSK-007 deve girare prima perché definisce lo schema DB").
     La verità per lo scheduler è il frontmatter. -->
- TSK-XXX — <rationale opzionale>
```

## Regole

- **Atomicità:** un task = una unità testabile. Mai "Crea modulo Login" → spezza
  in "Crea endpoint POST /auth/login" + "Crea LoginPage React".
- Cita endpoint OpenAPI o pagina FE specifica, non astratti.
- Estimate: XS=<2h, S=mezza giornata, M=1 giorno, L=2+ giorni.
- Citazioni: vedi `citation-rules` (cascade L4 → US/ADR).
