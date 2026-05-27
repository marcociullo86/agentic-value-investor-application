---
rule_id: typescript.nextjs.robustness.loading_error_states
version: v1
tier: canonical
title: "Data views expose loading and error states"
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
Silent failures confuse users.

## Detection hints
fetch without loading or error UI
