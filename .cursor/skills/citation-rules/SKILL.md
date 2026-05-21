---
name: citation-rules
description: Grammatica canonica delle citazioni e dei wikilink. Riferimento unico per ogni agente che scrive in wiki/, management/, design_&_architecture/.
---
# Regole di citazione (canoniche)

Questa è la **single source of truth** della grammatica citazioni della factory.
Tutte le altre skill (`scrivi-wiki-page`, `scrivi-epica`, `scrivi-user-story`,
`scrivi-task`, `ingest-protocol`, `lint-checks`, `query-protocol`) rimandano qui.

## Forme

| Forma | Quando | Esempio |
|---|---|---|
| `[^src: <path>.md §<sez>]` | Citazione fonte (raw o wiki) su claim ≥ 20 parole | `[^src: raw/2026-05-15-spid.txt §Autenticazione]` |
| `[[<slug>]]` | Link interno wiki, senza estensione, senza `../` | `[[oidc]]`, `[[circuit-breaker]]` |
| `[^code: <path>:<line>]` | Citazione codice (solo factory, non progetto host) | `[^code: .claude/agents/wiki-keeper.md:15]` |
| `[^web: <url>] (accessed YYYY-MM-DD)` | Citazione web (solo skill `tech-scout`, v2.7) | `[^web: https://fastapi.tiangolo.com/] (accessed 2026-05-20)` |

## Quando una citazione è obbligatoria

Una citazione è obbligatoria per ogni **claim non triviale**:

- Frase affermativa di **≥ 20 parole**, oppure
- Frase che asserisce un fatto verificabile (nome, numero, data, standard, decisione)
- Frase che cita uno standard normativo (SPID, OIDC, OAuth2, SAML, eIDAS, FHIR, GDPR, HL7, ISO/IEC, RFC numerati)

**Esenzioni**: header, voci di lista TODO, frontmatter YAML, blocchi di codice, frasi imperative del template.

## Disciplina cascade (per layer)

| Layer | Cita |
|---|---|
| `wiki/` | `raw/<file>.txt §<sez>` |
| `management/kanban/EP-*/` | `wiki/<file>.md §<sez>` |
| `management/kanban/EP-*/US-*/` | `wiki/<file>.md §<sez>` |
| `design_&_architecture/` | `management/kanban/EP-*/US-*/US-*.md §<sez>` (storie, non concept) |
| `management/kanban/**/TSK-*.md` | `design_&_architecture/<file>.md §<sez>` o US/ADR |

Regola **cascade**: ogni agente cita la layer immediatamente sopra di sé, **anche
se ha letto la wiki** per contesto.

## Wikilink

- Slug **senza estensione** e **senza path**: `[[oidc]]`, mai `[[wiki/concepts/oidc.md]]` né `[[../../concepts/oidc.md]]`.
- Slug case-sensitive, lowercase con `-` come separatore.
- Wikilink non risolto = `ERROR broken-link` (rilevato dal `lint-checks` Check 1).

## Anti-pattern (vietati)

| Anti-pattern | Correzione |
|---|---|
| Path relativo `../../concepts/foo.md` | Usa `[[foo]]` |
| Citazione su frase < 20 parole non normativa | Ometti |
| Citazione fonte inventata | Usa `wiki/gaps.md` |
| Citazione cross-cascade (es. ADR cita concept) | Cita la layer sopra (storie, non concept) |
| `[^src: ...]` senza `§<sezione>` | Aggiungi sempre la sezione |

## Verifica

Il `wiki-lint` (Check 2 di `lint-checks`) controlla:

1. Ogni claim ≥ 20 parole ha citazione adiacente (entro 3 righe).
2. Il path citato esiste.
3. La sezione `§<sez>` esiste (header markdown matching).
