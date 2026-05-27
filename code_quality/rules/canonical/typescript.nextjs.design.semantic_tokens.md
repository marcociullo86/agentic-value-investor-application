---
rule_id: typescript.nextjs.design.semantic_tokens
version: v1
tier: canonical
title: "UI uses semantic design tokens"
applies_to:
  language: typescript
  framework: nextjs
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
ADR-023 M3-aligned tokens.

## Detection hints
Hardcoded hex colors on shared components
