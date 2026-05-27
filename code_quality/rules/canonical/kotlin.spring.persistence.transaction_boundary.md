---
rule_id: kotlin.spring.persistence.transaction_boundary
version: v1
tier: canonical
title: "Write operations use @Transactional at service layer"
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
Multi-step writes need transactional boundaries.

## Detection hints
Multiple save() without @Transactional
