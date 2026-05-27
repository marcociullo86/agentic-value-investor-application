---
rule_id: kotlin.spring.nullability.explicit_api
version: v1
tier: canonical
title: "Public API types use explicit nullability"
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
Unchecked nulls cause NPE in production.

## Detection hints
Platform types or !! on external API data
