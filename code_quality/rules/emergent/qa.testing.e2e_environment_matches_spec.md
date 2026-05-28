---
rule_id: qa.testing.e2e_environment_matches_spec
version: v1
tier: emergent
title: E2E environment must match TSK-declared runtime stack
applies_to:
  language: typescript
  framework: playwright
  context: [robustness, e2e, qa, design]
severity_default: high
auto_fixable: false
status: candidate
---
# Regola

## Rationale
When a TSK specifies a prod-like test environment (e.g. static export served by backend via docker compose), mocked dev-server tests alone do not satisfy the acceptance contract. Environment fidelity prevents false confidence from simulations that never exercise the declared stack.

## Detection hints
- TSK Technical Specs name docker/backend/static-export serving but code_path changes exclude docker/backend configs
- playwright.config uses `npm run dev` while TSK DoD claims static-export+backend execution
- All API calls mocked via page.route() when TSK requires real BE integration
