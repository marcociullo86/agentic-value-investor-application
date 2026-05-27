---
rule_id: typescript.nextjs.boundary.client_server_split
version: v1
tier: canonical
title: "Respect server vs client component boundaries"
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
Misplaced use client increases bundle size.

## Detection hints
useState without use client directive
