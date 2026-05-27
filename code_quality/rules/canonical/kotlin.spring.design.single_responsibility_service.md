---
rule_id: kotlin.spring.design.single_responsibility_service
version: v1
tier: canonical
title: "Services have a single cohesive responsibility"
applies_to:
  language: kotlin
  framework: spring
  context: []
severity_default: low
auto_fixable: false
status: active
metadata:
  created_at: "2026-05-27"
  author: "agent:cqrl-bootstrap"
---
# Regola

## Rationale
God-services become change bottlenecks.

## Detection hints
Service >500 LOC mixing unrelated domains
