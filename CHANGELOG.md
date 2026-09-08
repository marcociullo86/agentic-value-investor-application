# CHANGELOG — agentic-value-investor-application

## [v2.42.0] — 2026-09-08 (cumulative delta v2.33 → v2.42, additive)

Upgrade orchestrato via `factory-upgrade-protocol` sul master
`soli-multi-agents-factory`. Tutte le capability nuove sono **opt-in** (R.P3):
`enabled: false` di default in `factory.config.yaml` — nessun cambio di
comportamento a bandiere spente. Backward compat totale con v2.33.

### Bundle cumulativo (9 delta)

- **v2.34** — Governance Enforcement (EP-049) + Adoption Onboarding (EP-050) + Bus Factor Mitigation (EP-051) + Tech Debt (EP-052): lint-checks modulare 9 famiglie + `pattern_version` SSOT.
- **v2.35** — Bus Factor Mitigation gate formale: `PATTERN.md §23.8` sunset policy + `sunset_date` annotations.
- **v2.36** — Backport portale-servizi-factory (EP-053): R.21 cooperative locking, skill `/onboarding`, vcs-preflight step 2-bis, `PATTERN.md §23.9` AI attribution policy.
- **v2.37** — **Code Intelligence Stack** (EP-054, opt-in): L1 ctags + L2 tree-sitter/nomic-embed/LanceDB + L3 graphify — `tools/code-intelligence/` + `/code-search`. Zero SaaS.
- **v2.38** — **Semantic Purpose Layer** (EP-055, opt-in): `wiki/purpose.md` template + `PATTERN.md §34`. **Wiki Keeper 2.0** (EP-056, opt-in): CoT handoff esplicito + `sweep-reviews-protocol` + comando `/sweep-reviews` + `PATTERN.md §35`. `PATTERN.md §23.10` policy licenze terze parti.
- **v2.39** — **Ponytail Decision Ladder** (EP-057, opt-in): YAGNI a 7 livelli, skill `ponytail-review`/`ponytail-audit`, comandi `/ponytail-review` + `/ponytail-audit`, R.PY1 (security/a11y/trust-boundary inviolabili). **Factory-as-MCP-Server** (EP-058, opt-in): `adapters/mcp/` read-only stdio server, R.MCP1 no-write.
- **v2.40** — Backport delta portale-servizi-factory (EP-059): agente `release-manager` + skill `tpm-reconcile` + skill `deep-functional-probe` + skill `release-protocol` + `tools/analytics/statusline-ledger.py`.
- **v2.41** — **Fleet Health / Refactor Skill Layer** (EP-060, opt-in): agente `fleet-doctor` + skill `refactor-agent-skills` + foglie `references/refactor/` + `tools/refactor/` + comando `/refactor` + `PATTERN.md §36`. Check 4ap WARNING-only. V-1..V-9 + G1..G11.
- **v2.42** — **Session Observability** (EP-061 + EP-062, opt-in): skill `session-analysis-protocol` + comando `/session-analysis` + `tools/session-analysis/` (parse-transcript / fleet-metrics / detect-anomalies / generate-report + `schema-anomaly.json`) + fan-in walker `subagents/` per `tools/analytics/harvest-session-tokens.py` (EP-062) + schema-guard + fixture + contract-test + `PATTERN.md §37`. Runbook fleet-doctor handoff.

### Files aggiunti / aggiornati

Vedi `git log` sul commit di upgrade per l'inventario completo. Riassunto per famiglia:

- Config: `factory.config.yaml#pattern_version` `2.33` → `2.42` + blocchi opt-in
  (`voice_channel`, `wiki_purpose`, `wiki_sweep`, `ponytail`, `mcp_server`,
  `code_intelligence`, `refactor_agent_skills`, `session_analysis` +
  `temporal.estimate_protocol` + `analytics.sprint_progress`).
- Documenti canonici: `PATTERN.md` esteso con §32..§37; `CLAUDE.md` aggiornato
  con la sezione «Meta-prompt versioning» v2-42.
- Codebase framework: dir aggiuntive `voice/`, `adapters/mcp/`,
  `tools/{code-intelligence,refactor,session-analysis}/` + agenti/skill/comandi
  elencati sopra.

### Vincoli rispettati

- Additivo puro (unica eccezione: bugfix `tools/analytics/harvest-session-tokens.py` EP-062, aggiunto ex-novo perché assente in target).
- Nessuna nuova dipendenza esterna installata; capability opt-in caricano dipendenze solo a flag on.
- VCS preflight verde: `git status` pulito pre e post upgrade.
- Personalizzazioni pre-esistenti preservate: `.claude/agents/infra-dev.md` e file già presenti (voice/search/temporal artifacts, tavola-rotonda, prototype, content-share) non toccati.

### Runbook attivazione (post-upgrade)

Ciascuna capability nuova richiede attivazione esplicita in `factory.config.yaml`:

```yaml
code_intelligence.enabled: true          # + l1/l2/l3 sub-flag
refactor_agent_skills.enabled: true      # /refactor + fleet-doctor
ponytail.enabled: true                   # + code_quality.enabled: true
mcp_server.enabled: true                 # + client MCP configurato
session_analysis.enabled: true           # + tools/session-analysis/
wiki_purpose.enabled: true               # + wiki/purpose.md compilato
wiki_sweep.enabled: true                 # + wiki-keeper 2.0
voice_channel.enabled: true              # + prerequisiti STT/TTS/VAD
```

Per riferimenti operativi: `wiki/runbooks/skill-hygiene.md`,
`wiki/runbooks/session-analysis-fleet-doctor-handoff.md`,
`wiki/concepts/session-agentic-analyser.md`.
