---
rule_id: typescript.nextjs.security.no_secrets_in_client
version: v1
tier: canonical
title: "No secrets in client bundles"
applies_to:
  language: typescript
  framework: nextjs
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
Client env vars are public.

## Detection hints
NEXT_PUBLIC_ on API keys
