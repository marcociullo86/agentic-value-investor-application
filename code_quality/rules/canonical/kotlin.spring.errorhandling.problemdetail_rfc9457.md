---
rule_id: kotlin.spring.errorhandling.problemdetail_rfc9457
version: v1
tier: canonical
title: "API errors use RFC 9457 ProblemDetail with flat extensions"
applies_to:
  language: kotlin
  framework: spring
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
ADR-012: extension members at top level in JSON responses.

## Detection hints
Nested details object instead of flat extensions
