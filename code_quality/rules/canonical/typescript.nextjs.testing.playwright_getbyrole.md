---
rule_id: typescript.nextjs.testing.playwright_getbyrole
version: v1
tier: canonical
title: "E2E prefer getByRole over brittle selectors"
applies_to:
  language: typescript
  framework: nextjs
  context: []
severity_default: medium
auto_fixable: false
status: active
metadata:
  created_at: "2026-05-27"
  author: "agent:cqrl-bootstrap"
---
# Regola

## Rationale
Role selectors align with a11y.

## Detection hints
CSS class chains as primary locators
