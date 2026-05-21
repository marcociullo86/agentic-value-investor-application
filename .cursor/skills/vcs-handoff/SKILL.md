---
name: vcs-handoff
description: Skill canonica per la coordinazione VCS cross-layer (v2.8). Procedura per-mode (monorepo|submodule|sibling|external|none). Mai esecuzione automatica di push/clone/submodule add. Gate umano obbligatorio (PATTERN §7 r.14).
---
# VCS Handoff (v2.8)

Riferimenti: `dev-protocol` (Fase 5), `wiki-log-entry` (template `vcs-handoff`), `PATTERN.md §3` (operazione `VCS-handoff`) + `§7 r.14` (gate umano).

## Quando si attiva

- Fase 5 di `dev-protocol`: dev-agent ha implementato un TSK e chiede l'handoff VCS.
- Invocata anche da TPM/PM per refactoring di branch su `management/` (raro, sempre con gate).

## Vincolo §7 r.14 (assoluto)

**Nessuna operazione VCS distruttiva o cross-repo viene MAI eseguita automaticamente.**

Mai automatici:
- `git commit --amend`
- `git push --force` / `--force-with-lease`
- `git clone`
- `git submodule add` / `update --init`
- Branch deletion (`-D`, `-d` su branch unmerged)
- Tag operations su tag esistenti

Operazioni che la skill **propone** (e l'umano conferma):
- `git add <files>` + `git commit -m "..."`
- `git push` su branch corrente non protetto
- `git submodule update` (non-distruttivo, solo se `vcs.mode: submodule`)

## Procedura per-mode

### Mode `monorepo`

1. Stage: identifica file modificati in `<code_path>/` dal TSK corrente.
2. Proposta:
   ```
   VCS HANDOFF — MONOREPO
   ======================
   Mode: monorepo
   Branch corrente: <branch>
   File da committare:
   - <list>
   Messaggio proposto:
     <type>(<scope>): <subject>

     Refs: TSK-ZZZ, US-YYY
   Procedere con commit? (sì/no)
   Push remoto? (sì/no, default no)
   ```
3. Attendi conferma. Eseguire **solo** quando confermato.
4. Append a `wiki/log.md` (template `vcs-handoff`).

### Mode `submodule`

1. STOP se `.gitmodules` non presente al root (apri gap setup).
2. Proposta in due step:
   - Step A: commit nel submodule (`<code_path>` punta al submodule)
   - Step B: bump del submodule ref nel factory repo (gate separato)
3. Per ciascun step: testo proposta + attesa conferma.
4. Mai `submodule add` automatico.

### Mode `sibling`

1. Identifica il sibling repo (`code_path` assoluto fuori dal factory).
2. Proposta:
   ```
   VCS HANDOFF — SIBLING
   =====================
   Sibling repo: <abs_path>
   Branch: <branch> (in sibling)
   File:
   - <list>
   Messaggio:
     <type>(<scope>): <subject>

     Refs: TSK-ZZZ (factory: <factory_repo_path>)
   Avviso: questa operazione tocca un repo diverso dal factory.
           Conferma di volerlo fare. (sì/no)
   Aprire/aggiornare PR? (sì/no, default no)
   ```
3. Attendi conferma esplicita (gate rinforzato per cross-repo).
4. Append a `wiki/log.md` annotando entrambi i repo coinvolti.

### Mode `external`

1. La factory non coordina git. Surface in chat:
   ```
   VCS HANDOFF — EXTERNAL
   ======================
   Mode: external — la factory non gestisce git per <code_path>.
   File toccati:
   - <list>
   Gestisci tu il versioning sul sistema di destinazione.
   ```
2. Append a `wiki/log.md` con `Commit: n/a`.

### Mode `none`

No-op. `code_path` è vuoto o solo locale (es. solo prototipi non versionati). Append a `wiki/log.md` solo con `Files touched: N`.

## Branch strategy

| `vcs.branch_strategy` | Comportamento |
|---|---|
| `shared` (default) | Tutti i TSK sullo stesso branch attivo (es. `main` o `dev`). |
| `per-tsk` | Un branch per TSK (es. `tsk/TSK-001-foo`). Crea branch se mancante (gate umano). |
| `per-sprint` | Un branch per sprint (es. `sprint/01`). |

## Commit coupling

| `vcs.commit_coupling` | Comportamento |
|---|---|
| `float` (default) | Nessun lock tra factory e code. |
| `pin` | Aggiorna `.factory-lock` con `factory_commit ↔ code_commit` mapping (richiede gate umano per ogni update). |

## Log entry

Append a `wiki/log.md`:

```
[YYYY-MM-DD HH:MM] vcs-handoff — proposed <action> on <mode> — gate: <approved|pending|rejected>
```

## Anti-pattern

| Anti-pattern | Correzione |
|---|---|
| `git push --force` automatico | Vietato (§7 r.14) |
| `git submodule add` automatico | Vietato — apri gap setup |
| Commit cross-repo (sibling) senza gate rinforzato | Conferma esplicita obbligatoria |
| Branch deletion automatico | Vietato — l'umano decide |
| Skippare il log entry | Audit trail obbligatorio |
