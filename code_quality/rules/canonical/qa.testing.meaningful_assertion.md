---
rule_id: qa.testing.meaningful_assertion
version: v1
tier: canonical
title: "Tests assert behavior not implementation"
applies_to:
  language: typescript
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
Brittle tests break on refactors.

## Detection hints
toBeTruthy() only or snapshot-only tests
