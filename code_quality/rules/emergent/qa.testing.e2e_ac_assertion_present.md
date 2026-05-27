---
rule_id: qa.testing.e2e_ac_assertion_present
version: v1
tier: emergent
title: E2E suite must assert all TSK acceptance criteria explicitly
applies_to:
  language: typescript
  framework: playwright
  context: [robustness, e2e, qa]
severity_default: medium
auto_fixable: false
status: candidate
---
# Regola

## Rationale
Retro-review and regression safety require that each TSK DoD assertion maps to at least one named Playwright test. Indirect coverage via unrelated scenarios is insufficient for P0 regressions.

## Detection hints
- TSK body lists explicit test cases not present as `test('...')` titles in scoped spec files
- DoD checkbox references selectors (e.g. `data-testid="snapshot-date"`) absent from assertions
