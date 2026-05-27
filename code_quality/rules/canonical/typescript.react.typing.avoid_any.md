---
rule_id: typescript.react.typing.avoid_any
version: v1
tier: canonical
title: "Avoid explicit any in production code"
applies_to:
  language: typescript
  framework: react
  context: []
severity_default: low
auto_fixable: false
status: active
metadata:
  created_at: "2026-05-27"
  author: "agent:cqrl-bootstrap"
---
# Regola

## Rationale
any defeats API type safety.

## Detection hints
: any on props without narrowing
