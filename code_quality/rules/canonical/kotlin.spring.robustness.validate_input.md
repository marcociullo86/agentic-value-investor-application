---
rule_id: kotlin.spring.robustness.validate_input
version: v1
tier: canonical
title: "Public endpoints validate input"
applies_to:
  language: kotlin
  framework: spring
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
Unvalidated input propagates to adapters and DB.

## Detection hints
@RequestBody without @Valid
