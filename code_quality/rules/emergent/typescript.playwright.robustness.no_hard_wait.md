---
rule_id: typescript.playwright.robustness.no_hard_wait
version: v1
tier: emergent
title: Avoid page.waitForTimeout in E2E specs
applies_to:
  language: typescript
  framework: playwright
  context: [robustness, e2e]
severity_default: medium
auto_fixable: false
status: candidate
---
# Regola

## Rationale
Hard waits (`page.waitForTimeout`) mask race conditions and increase CI flakiness. Prefer `expect` auto-waiting, `waitForURL`, or event-driven assertions.

## Detection hints
- `page.waitForTimeout(` in `*.spec.ts`
- Fixed sleeps before assertions instead of locator visibility checks
