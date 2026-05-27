---
rule_id: typescript.nextjs.accessibility.interactive_name
version: v1
tier: canonical
title: "Interactive controls have accessible names"
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
WCAG / EP-016 a11y.

## Detection hints
Icon-only button without aria-label
