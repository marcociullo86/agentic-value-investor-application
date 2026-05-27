---
rule_id: typescript.nextjs.errorhandling.user_safe_messages
version: v1
tier: canonical
title: "User-facing errors use notification service"
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
EP-015 consistent error UX.

## Detection hints
alert(err.message) with backend stack
