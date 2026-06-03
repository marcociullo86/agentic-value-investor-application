---
name: dev-protocol
description: Procedura per un dev-agent che consuma un TSK e produce codice in code_path. Single source of truth per Develop (PATTERN §3).
---
# Procedura — consumare un TSK (Develop, L4 → L5)

Skill condivisa fra `be-dev`, `fe-dev`, `db-dev`, `qa-dev`. La specializzazione
per layer vive nell'agente; questa skill è la spina dorsale comune.

## Fase 0 — Gate preliminare + target resolution (v2.12 multi-repo)

Prima di qualsiasi scrittura:

1. **Leggi `factory.config.yaml`** (root del repo).
2. Verifica:
   - `topology` ammette il tuo layer (es. per `be-dev`: topologia ∈
     {`full-stack-agents`, `hybrid-be-agents`, `custom` con `be-dev` listato}).
   - `routing.<tuo-layer> == agent` (oppure override esplicito via `/dev`).
   - Esiste un percorso L5 risolvibile (vedi step 2-bis: target resolution).
3. **Leggi il TSK**: deve avere `layer: <tuo>`, `consumer: agent`, `status: todo`,
   dipendenze chiuse. Se manca anche solo un campo o un gate, **STOP** e
   segnala in chat (non procedere "in modalità best-effort").

### Step 2-bis — Target resolution (v2.12 multi-repo, PATTERN §5 + §13)

Determina il `code_path` effettivo (`resolved_code_path`) e la `resolved_vcs` da usare:

**Caso A — Legacy single-repo** (`code_path:` valorizzato, `code_paths: []` o assente):
- `resolved_code_path = factory.config.yaml.code_path`
- `resolved_vcs = factory.config.yaml.vcs` (top-level)
- `resolved_target_name = "default"` (per logging)
- Procedi.

**Caso B — Multi-repo** (`code_paths: [<entry>, ...]` non vuoto):

1. Read TSK `target:` frontmatter.
2. Se `target:` valorizzato:
   - Cerca `entry = code_paths[name == target]`.
   - Se non trovato → **ERROR** «TSK <id> ha `target: <X>` ma nessuna entry in `code_paths` con quel nome». STOP.
   - Se trovato ma `<tuo-layer>` non in `entry.layers` → **ERROR** «TSK <id> ha layer <Y> e target <X>, ma entry <X> non lista <Y> in `layers`». STOP.
   - `resolved_code_path = entry.path`; `resolved_vcs = entry.vcs`; `resolved_target_name = entry.name`. Procedi.
3. Se `target:` assente:
   - Filtra `candidates = [e for e in code_paths if <tuo-layer> in e.layers]`.
   - Se `len(candidates) == 0` → **ERROR** di config «Nessuna entry in `code_paths` lista <tuo-layer>; routing.<layer>: agent richiede almeno una entry. Lint Check 4c violato». STOP.
   - Se `len(candidates) == 1` → auto-derive: `entry = candidates[0]`. Procedi con `resolved_*` da quell'entry.
   - Se `len(candidates) >= 2` → **ERROR** «TSK <id> layer <Y> ambiguo: ≥ 2 entry in `code_paths` listano <Y> (`<n1>`, `<n2>`, ...). Il TPM doveva valorizzare `target:`. Lint Check 4j violato». STOP. Mai indovinare.

4. Verifica accessibilità di `resolved_code_path`:
   - Esiste sul filesystem oppure è creabile (es. submodule path con `git submodule init` ancora da fare).
   - Se non esiste e non creabile → **ERROR** «code_path <path> per target <name> non accessibile». STOP.

5. Log a chat:
   ```
   Target resolved: <name> → <resolved_code_path> (vcs: <mode>)
   ```

Tutto il resto del protocollo (Fasi 1-5) usa `resolved_code_path` e `resolved_vcs` al
posto del legacy `code_path` + `vcs`. La citazione codice nei dev-agent (§6) usa il
prefisso appropriato in base a `resolved_vcs.mode`.

## Fase 1 — Preparazione contesto

1. Leggi la US riferita dal TSK (path deducibile: `EP-XXX-*/US-YYY-*/US-YYY.md`).
2. Leggi l'ADR / sezione di `design_&_architecture/` citato.
3. Apri le pagine `wiki/` citate transitivamente dalla US (concept/entity/synthesis).
   Non citarle direttamente nel codice — citazione cascade: il codice cita TSK/ADR.
4. Leggi `raw/tech_stack.md` per vincoli (versioni, standards).
5. Esplora `<code_path>/**` per capire layout esistente.

## Fase 2 — Handoff iniziale

1. Edit del TSK: `status: in-progress`, aggiungi `updated: YYYY-MM-DD HH:MM`.
2. Non toccare il corpo del TSK.

## Fase 3 — Implementazione

1. Implementa secondo:
   - Implementation Steps del TSK (ordine indicativo, non vincolante)
   - Technical Specs del TSK
   - Standards verbatim citati nei raw (PATTERN §11)
2. Atomicità: tutto il cambiamento per **un singolo TSK** deve essere
   coerente (un commit logico, anche se il VCS lo separa in più commit).
3. Se durante l'implementazione scopri che il TSK è **sotto-specificato**:
   - Gap di knowledge base → append `wiki/gaps.md` (vedi `wiki-gap-protocol`)
   - Decisione architetturale mancante → STOP e segnala in chat (`tpm` o
     `lead-architect` la prenderanno; non improvvisare design)
   - Bug pre-esistente fuori scope → segnala in chat (TPM aprirà TSK separato),
     non fixare opportunisticamente (PATTERN §7 r.8)

## Fase 4 — Definition of Done

Verifica la DoD del TSK punto per punto:
- [ ] Codice compila / build passa
- [ ] Test unitari relativi passano
- [ ] (Se applicabile) Test integrazione passano
- [ ] Documentazione inline minima (docstring, README locale solo se richiesto)
- [ ] Niente file fuori scope toccati

Se anche un solo punto fallisce e non puoi risolverlo nel TSK corrente:
- Rollback delle modifiche già fatte (preferibile) o segnala chiaramente in chat
  lo stato parziale.
- Edit `status: in-progress` (NON `done`), e descrivi il blocker in chat.

## Fase 4-bis — Visual Verification (opt-in fe_correctness)

Sub-step di Develop FE, posizionato **dopo la Fase 4** (build/typecheck verde) e
**prima della Fase 5** (handoff a `done`). Formalizza il Punto 1 di ADR-013
(ordering `develop → visual-oracle → review`).

**Trigger (condizione AND)**:
- `TSK.layer == 'fe'` **AND** `factory.config.yaml.fe_correctness.enabled == true`.

**No-op esplicito** (backward compat):
- A flag spento (`fe_correctness.enabled: false`, default) **oppure** `TSK.layer != 'fe'`,
  la Fase 4-bis è **no-op**: il TSK passa direttamente da Fase 4 a Fase 5, con
  `visual_status` assente o `pending`. **Comportamento identico a v2.16.** Una factory
  che non opta-in non vede alcuna differenza.

**Fail-loud**:
- Se il trigger è soddisfatto (`layer: fe` + `fe_correctness.enabled: true`) ma la skill
  `visual-oracle-protocol` **non è presente** nell'adapter → **ERROR** «`fe_correctness.enabled: true`
  ma skill `visual-oracle-protocol` assente; impossibile eseguire la Fase 4-bis». STOP.
  Mai degradare silenziosamente a no-op quando il flag è attivo.

**Azione**:
1. Invoca `visual-oracle-protocol` (skill) come sub-procedura, passando il `TSK-id` e il
   `resolved_code_path` (da Fase 0 step 2-bis). La skill produce un output strutturato
   `{verdict, defects}` (il critic è lo stesso `fe-dev` in passata multimodale, vedi ADR-009 §Conseguenze).

**Esiti** (gestiti analogamente a CQRL, PATTERN §19):

```
verdict: pass        → visual_status: pass; TSK transita a status: done (→ Fase 5).
verdict: conditional → loop fe-dev (bounded fe_correctness.max_iterations, default 3);
                       la lista difetti (defects) è l'input handoff dell'iterazione
                       successiva; il TSK resta in-progress fino a pass o esaurimento bound.
verdict: reject      → visual_status: reject; TSK resta in-progress; gate umano
                       (difetto strutturale, non risolvibile in 1-3 iter; coerente con
                       CQRL §19 reject → gate umano, non auto-loop).
```

- **Loop `conditional`**: bounded da `fe_correctness.max_iterations` (default `3`, analogo a
  `code_quality.max_iterations` / R.Q4 di CQRL). A ogni iterazione la lista `defects` è
  passata come handoff a `fe-dev`, che ri-implementa e ri-sottopone al visual oracle.
  Esaurito il bound senza `pass`, l'esito degrada a gate umano (non `done`).
- **`reject`**: il TSK **non** transita a `done`; resta `in-progress` con `visual_status: reject`.
  La review è bloccata a valle (precondition Fase 0 di `code-review-protocol`, ADR-013 §Punto 2).

**Input**: TSK FE con build/typecheck verde (output Fase 4); `factory.config.yaml.fe_correctness`.
**Output**: `visual_status: pass` (→ Fase 5) | loop fe-dev (`conditional`) | `visual_status: reject` + gate umano.
**Criterio**: `verdict == pass` → procedi a Fase 5; altrimenti loop bounded o STOP per gate umano.

[^src: design_&_architecture/decisions/ADR-013.md §Punto 1 — dev-protocol Fase 4-bis (flusso verbatim)]
[^src: design_&_architecture/decisions/ADR-009.md §Conseguenze — critic = stesso fe-dev, ordering visual → review]

## Fase 5 — Handoff finale (Develop completato)

1. Edit del TSK: `status: done`, `updated: YYYY-MM-DD HH:MM`.
2. Invoca `dev-handoff` (skill) per scrivere l'entry su `wiki/log.md`.
3. **Invoca `vcs-handoff`** (skill, v2.8 esteso multi-repo v2.12) passando
   `resolved_vcs` + `resolved_target_name` (da Fase 0 step 2-bis). La skill coordina
   i commit per la topologia VCS del **target risolto**, non per la factory globale:
   - `monorepo` → propone commit nel factory repo (path = `resolved_code_path` sotto factory root).
   - `submodule` → propone commit nel submodule referenziato da `resolved_vcs.submodule_path`, poi bump del ref nel factory.
   - `sibling` → propone commit nel repo esterno (`resolved_code_path`) + avviso PR.
   - `external` → solo log, nessuna operazione VCS.
   - `none` → STOP (incoerenza: develop su mode `none` non dovrebbe accadere).

   In multi-repo, **ogni vcs-handoff è per-target**: mai operazioni coordinate
   cross-target automaticamente. Se un TSK richiede modifiche cross-repo, si scompone
   in N TSK con target distinti (responsabilità del TPM).

   Gate umano obbligatorio per ogni `git commit` (vedi PATTERN §7 r.14).

## Vincoli inviolabili

- **Mai editare il corpo del TSK** (solo `status:` e `updated:`).
- **Mai scrivere su `wiki/**`** se non append a `wiki/log.md` e `wiki/gaps.md`.
- **Mai scrivere su `design_&_architecture/`** (è proprietà di Arch).
- **Mai scrivere su `management/kanban/**`** fuori dal proprio TSK (la
  generazione TSK è proprietà del TPM).
- **Mai inventare endpoint, tabelle, classi** non specificati nel design.
- **Standards verbatim** (PATTERN §11): se SAML/OIDC/FHIR citati, implementa
  esattamente quelli.
- **Stop se code_path non è valorizzato.** Mai scrivere "a indovinare" in `./src/`.
