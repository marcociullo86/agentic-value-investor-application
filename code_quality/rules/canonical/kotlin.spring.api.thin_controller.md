---
rule_id: kotlin.spring.api.thin_controller
version: v1
tier: canonical
title: "Controllers delegate business logic to services"
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
Fat controllers hinder testing.

## Detection hints
Repository access or >30 LOC rules in @RestController
