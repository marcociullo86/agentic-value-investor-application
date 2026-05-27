---
rule_id: kotlin.spring.di.constructor_injection
version: v1
tier: canonical
title: "Prefer constructor injection over field injection"
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
Constructor injection keeps dependencies explicit and testable.

## Detection hints
@Autowired on private lateinit var fields
