---
name: tech-scout
description: Skill di proposta automatica dello stack tecnologico via ricerca web (stack_mode=auto, v2.7). Genera raw/tech_stack.md.proposal con citazioni datate. Mai auto-applicato: gate umano per promuovere a raw/tech_stack.md.
---
# Tech Scout (v2.7, stack_mode=auto)

Riferimenti: `citation-rules` (forma `[^web:]`), `wiki-log-entry` (template `tech-scout`), `PATTERN.md §14`, `wiki-gap-protocol`.

## Quando si attiva

- `factory.config.yaml` ha `stack_mode: auto`.
- Comando esplicito (es. invocata dal `wiki-keeper` o da uno script di setup).
- `raw/tech_stack.md` mancante o sezione layer assente.

**Mai** sostituisce un `raw/tech_stack.md` esistente: scrive sempre `.proposal`.

## Vincolo §11 (assoluto)

Standards normativi citati nei raw (SPID, OIDC, OAuth2, SAML, eIDAS, FHIR, GDPR, HL7, ISO/IEC, RFC numerati) **non si propongono come "equivalenti"**. La proposta DEVE adottarli verbatim. Sostituire silenziosamente uno standard è una violazione del contratto.

## Procedura (4 fasi)

### Fase 1 — Estrazione vincoli da wiki

```
Glob wiki/concepts/**, wiki/syntheses/**
```

Per ogni layer (be/fe/db/qa/infra): identifica vincoli citati (es. "deve supportare OIDC", "tabelle PostgreSQL", "test framework Jest", "deployment Docker").

Se zero vincoli → apri gap "tech_stack mancante per layer X" e ferma per quel layer.

### Fase 2 — Ricerca web (fonti 2026)

Per ogni layer con vincoli identificati:

- Cerca opzioni mainstream stato dell'arte (es. backend OIDC-capable: FastAPI + Authlib, Express + openid-client, Spring Boot + Spring Security OAuth2).
- Filtra per fonti datate ≥ 2025 (preferire 2026).
- Cita ogni opzione con `[^web: <url>] (accessed YYYY-MM-DD)`.

### Fase 3 — Scrittura proposta

Write `raw/tech_stack.md.proposal` (mai overwrite di `raw/tech_stack.md`):

```markdown
---
type: tech_stack_proposal
generated: YYYY-MM-DD
generated_by: tech-scout
status: pending_human_approval
---
# Tech Stack — App Template Demo (PROPOSTA)

> Proposta automatica. Mai applicata senza gate umano.
> Per promuovere: rivedere e copiare su `raw/tech_stack.md`.

## Backend
**Vincoli identificati (da wiki/)**: <lista>
**Proposta**: <framework> — <rationale 1 riga>
**Alternative considerate**:
- <opzione A> [^web: <url>] (accessed YYYY-MM-DD)
- <opzione B> [^web: <url>] (accessed YYYY-MM-DD)

## Frontend
...

## Database
...

## QA / Testing
...

## Infra / Deployment
...

## Standards verbatim (PATTERN §11)
<lista degli standards citati nei raw, adottati come-sono>
```

### Fase 4 — Handoff

Append a `wiki/log.md` (template `tech-scout`):

```
[YYYY-MM-DD HH:MM] tech-scout — raw/tech_stack.md.proposal generated (N alternative) — files touched: 1
```

Surface in chat: "Proposta scritta in `raw/tech_stack.md.proposal`. Revisiona e copia
su `raw/tech_stack.md` per applicare. Standards `<lista>` adottati verbatim da PATTERN §11."

## Regole

- **Mai overwrite di `raw/tech_stack.md`**. Solo `.proposal`.
- **Sempre citazioni datate** in forma `[^web: <url>] (accessed YYYY-MM-DD)`.
- **Standards verbatim**: §11 non negoziabile.
- **Gap su layer scoperti**: se la wiki non documenta vincoli, apri gap, non procedere a caso.
- **Almeno 2 alternative per layer** (proposta + 1 alternativa, ideale 2-3).

## Anti-pattern

| Anti-pattern | Correzione |
|---|---|
| Overwrite di `raw/tech_stack.md` | Solo `.proposal` |
| Citazione web senza data accessed | Forma `[^web:] (accessed YYYY-MM-DD)` obbligatoria |
| Sostituire OIDC con "JWT custom" | PATTERN §11 violation |
| Procedere senza vincoli | Apri gap per layer scoperto |
