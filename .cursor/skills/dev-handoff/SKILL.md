---
name: dev-handoff
description: Entry per wiki/log.md a chiusura di un TSK consumato da dev-agent (operazione Develop, PATTERN §3).
---
# Procedura — handoff dev-agent → wiki/log.md

Append-only su `wiki/log.md` quando un dev-agent completa un TSK
(status `in-progress` → `done`).

## Formato entry

```markdown
## YYYY-MM-DD HH:MM — develop TSK-ZZZ
**Agente:** <be-dev|fe-dev|db-dev|qa-dev>
**TSK:** [[../management/kanban/EP-XXX-<slug>/US-YYY-<slug>/TSK-ZZZ]]
**Layer:** <be|fe|db|qa|infra>
**Code path:** <code_path da factory.config.yaml — relativo o assoluto>
**Files touched:** <count> (lista compatta solo se ≤ 5; altrimenti "vedi commit")
**Commit:** <hash short se code_path è git tracciato; oppure "n/a">
**DoD:** <pass | partial — descrivi> 
**Note:** <free-form, max 2-3 righe; segnala blocker non-bloccanti rilevati>
```

## Esempi

### Caso normale (DoD pass, code_path interno al repo)

```markdown
## 2026-05-20 14:32 — develop TSK-042
**Agente:** be-dev
**TSK:** [[../management/kanban/EP-003-auth/US-012-login/TSK-042]]
**Layer:** be
**Code path:** ./src/
**Files touched:** 3 (src/auth/login.py, src/auth/router.py, src/tests/test_login.py)
**Commit:** a1b2c3d
**DoD:** pass
**Note:** Implementato OIDC verbatim per coerenza con raw/tech_stack.md.
```

### Caso code_path esterno

```markdown
## 2026-05-20 16:10 — develop TSK-043
**Agente:** fe-dev
**TSK:** [[../management/kanban/EP-003-auth/US-012-login/TSK-043]]
**Layer:** fe
**Code path:** /Users/me/Repos/customer-portal/
**Files touched:** vedi commit
**Commit:** e4f5g6h (su repo esterno customer-portal)
**DoD:** pass
**Note:** —
```

### Caso DoD parziale (blocker)

```markdown
## 2026-05-20 18:00 — develop TSK-044 (PARTIAL)
**Agente:** db-dev
**TSK:** [[../management/kanban/EP-003-auth/US-012-login/TSK-044]]
**Layer:** db
**Code path:** ./src/
**Files touched:** 1 (migrations/004_add_session_table.sql)
**Commit:** —
**DoD:** partial — test integration non disponibile (db test fixture mancante)
**Note:** Status TSK resta `in-progress`. Apro gap "missing-db-test-fixture" in wiki/gaps.md.
```

## Regole

- **Append-only**: mai editare entry passate (PATTERN §7 r.5).
- **Una entry per TSK chiuso**. Se serve correggere, append nuova entry con marker
  `## YYYY-MM-DD HH:MM — develop TSK-ZZZ (correction)`.
- **Mai citare il codice prodotto direttamente in wiki/log.md** (rumore). Cita
  TSK; chi vuole il codice apre il commit / il file.
- **Coerenza con `dev-protocol`**: l'entry si scrive SOLO se `status: done` o
  `status: in-progress (partial)`. Mai per TSK in fase di gate.

## Cross-reference

- Cita: `wiki-log-entry` (formato generale log entries)
- Invocata da: `dev-protocol` (Fase 5)
