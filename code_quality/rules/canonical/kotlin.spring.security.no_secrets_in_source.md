---
rule_id: kotlin.spring.security.no_secrets_in_source
version: v1
tier: canonical
title: "No secrets or API keys in source code"
applies_to:
  language: kotlin
  framework: spring
  context: []
severity_default: critical
auto_fixable: false
status: active
metadata:
  created_at: "2026-05-27"
  author: "agent:cqrl-bootstrap"
---
# Regola

## Rationale
Secrets in repo are security incidents.

## Detection hints
Hardcoded apikey= or JWT secrets in .kt files
