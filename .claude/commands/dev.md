---
description: Invoca un dev-agent su un singolo TSK (per layer derivato dal TSK, o forzato).
argument-hint: <TSK-id> [<layer>]
---

Argomenti: TSK-id obbligatorio; layer opzionale (override del campo `layer:`).

Procedura: glob `TSK-<id>.md` → legge frontmatter → seleziona dev-agent
(`be-dev`/`fe-dev`/`db-dev`/`qa-dev`) corrispondente. Override one-shot: se TSK
ha `consumer: human`, l'invocazione esplicita di `/dev` consuma con agent per
QUESTO run senza modificare il file. Skill: `dev-protocol` + `dev-handoff` + `vcs-handoff` (v2.8).

STOP se:

- dev-agent non esiste nella topologia (`factory.config.yaml` routing mismatch)
- TSK ha dipendenze aperte (sezione `## Dependencies` con TSK non `done`)
- TSK manca di `layer:` o `consumer:` (frontmatter v2.7 incompleto)
- `code_path` non valorizzato in `factory.config.yaml`
- `raw/tech_stack.md` non copre il layer
