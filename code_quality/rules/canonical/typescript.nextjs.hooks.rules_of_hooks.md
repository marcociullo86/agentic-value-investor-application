---
rule_id: typescript.nextjs.hooks.rules_of_hooks
version: v1
tier: canonical
title: "Hooks follow Rules of Hooks"
applies_to:
  language: typescript
  framework: react
  context: []
severity_default: high
auto_fixable: false
status: active
metadata:
  created_at: "2026-05-27"
  author: "agent:cqrl-bootstrap"
---
# Regola

## Rationale
Conditional hooks cause runtime failures.

## Detection hints
useEffect inside if branches
