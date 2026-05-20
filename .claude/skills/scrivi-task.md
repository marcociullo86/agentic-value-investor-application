---
name: scrivi-task
description: Template per un task TSK-ZZZ.md atomico, contractuale, con frontmatter v2.7 (layer + consumer).
---
# Procedura per scrivere un task (v2.7)

Riferimenti: `citation-rules` (cascade L4 → US/ADR), `factory.config.yaml` per default `consumer`.

## Path

`management/kanban/EP-XXX-<slug>/US-YYY-<slug>/TSK-ZZZ.md`

## Frontmatter (v2.7)

```yaml
---
id: TSK-ZZZ
sprint: NN
layer: be | fe | db | qa | infra
consumer: agent | human
priority: P0 | P1 | P2
estimate: XS | S | M | L
status: todo | in-progress | done
---
```

### Note v2.7

- `team:` è **deprecato**, sostituito da `layer:` + `consumer:`. Il `wiki-lint` Check 3 segnala `WARNING deprecated-team-field`.
- Default `consumer:` = `routing.<layer>` da `factory.config.yaml`. Per override puntuale (es. TSK di onboarding da fare a mano), specificare `consumer: human`.

## Corpo

```markdown
# TSK-ZZZ — <Titolo conciso>

## Context
<US riferita, perché serve questo task>
[^src: management/kanban/EP-XXX-<slug>/US-YYY-<slug>/US-YYY.md §AC]

## Technical Specs
- **BE/FE/DB/QA:** endpoint OpenAPI / pagina / tabella / suite di test specifici
- **Auth:** ruoli abilitati
- **Standards:** OIDC/SAML/eIDAS verbatim se citati

## Implementation Steps
1. <step 1>
2. <step 2>

## Definition of Done
- [ ] Test unitario passa
- [ ] Test integrazione passa
- [ ] Documentazione aggiornata
- [ ] Code review approvata

## Dependencies
- TSK-XXX (prerequisito)
```

## Regole

- **Atomicità:** un task = una unità testabile. Mai "Crea modulo Login" → spezza.
- **`layer:` + `consumer:` obbligatori** (v2.7). Senza → wiki-lint ERROR.
- Cita endpoint/pagina specifici, non astratti.
- Estimate: XS=<2h, S=mezza giornata, M=1 giorno, L=2+ giorni.
- Citazioni: vedi `citation-rules`.
