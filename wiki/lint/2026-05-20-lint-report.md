---
type: lint-report
date: 2026-05-20
status: complete
scope: full
---

# Lint Report — 2026-05-20

## Severity Summary

| Level | Count |
|-------|-------|
| ERROR | 0 |
| WARNING | 2 |
| INFO | 3 |

---

## Check 1: Orphan Detection & Wikilinks

43 file linkati in `wiki/index.md`. No broken wikilinks detected.

---

## Check 2: Citation Audit (Sampling)

Sample: concepts, syntheses, runbooks. No unsourced claim detected.

---

## Check 3: Kanban Frontmatter Integrity (v2.7)

- 6 EP, 17 US, 38 TSK con frontmatter completo.
- No deprecated `team:` field.
- ID univoci validati.

---

## Check 4: Wiki ↔ Kanban Coherence

17 US con `wiki_page` valido; sezioni "Storie collegate" risolvono.

---

## Check 4b: Questions ↔ Kanban (v2.6)

`questions.md` `[APERTE]` = ∅. Q_001/Q_002/Q_003 in `[RISOLTE]`. US-013 residuo `pending_clarification:[Q_001]` — reconcile marker presente in log.

---

## Check 4c: Topology (v2.7)

`full-stack-agents` + 4 agent in `.claude/agents/`. 38 TSK mappati con `consumer:agent` su layer.

---

## Check 4d: VCS & Code Path (v2.8)

`code_path:./src/` (monorepo), `commit_coupling:float`.

---

## Top 5 Warnings

1. US-013 `pending_clarification` residuo (Q_001).
2. gap `arch-auth-provider-choice` (soft).
3. gap `arch-deployment-target` (soft).
4. gap `tpm-profile-snapshot-ttl` (soft).
5. gap `tpm-watchlist-default-creation` (soft).

---

## Suggestion

No `/heal` needed (0 ERROR heal-eligible). Citation audit manuale consigliato pre-cutover R1.0.
