---
name: pattern-b-testbed
description: "TESTBED per EP-060 US-236 TSK-552 — agente dispatched per iniezione (Pattern B) per validare G11. SUNSET-EXEMPT (test-only, regression-test permanente per dispatch-safety V-9)."
tools: [Read, Write, Bash]
model: claude-sonnet-4-6
sunset_date: never
---

# ROLE — Pattern B Testbed

Agente dispatched per iniezione del corpo. Riceve solo il corpo, non
vede il frontmatter.

Legge foglia esterna:
`.claude/agents/references/pattern-b-testbed/foglia-claude-ancorato.md`

Questo path è **`.claude/`-ancorato** — G11 dovrebbe PASS su questo target
(foglia raggiungibile dal subagent iniettato via cwd=root).

> Nota TSK-552: questo agente è un testbed SUNSET-EXEMPT di sola lettura.
> Non contiene logica di produzione. Non modificare il body senza aggiornare
> il test corrispondente in Fase 2 di TSK-552.
