---
rule_id: kotlin.spring.logging.no_pii_in_logs
version: v1
tier: canonical
title: "Logs must not contain raw PII or credentials"
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
GDPR / EP-014 fintech hardening.

## Detection hints
log.info with password or refresh token
