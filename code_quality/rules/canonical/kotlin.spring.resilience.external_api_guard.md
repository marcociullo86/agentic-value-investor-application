---
rule_id: kotlin.spring.resilience.external_api_guard
version: v1
tier: canonical
title: "External HTTP clients use rate limit, retry, timeout"
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
FMP calls require Resilience4j per ADR-016.

## Detection hints
Unguarded RestClient/WebClient for third-party APIs
