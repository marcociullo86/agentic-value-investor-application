---
rule_id: typescript.playwright.design.fixture_imports_esm
version: v1
tier: emergent
title: Prefer ESM imports for JSON fixtures in Playwright specs
applies_to:
  language: typescript
  framework: playwright
  context: [idiomaticity, design, e2e]
severity_default: low
auto_fixable: true
status: candidate
---
# Regola

## Rationale
Repeated `require('./fixtures/*.json')` with eslint-disable bypasses TypeScript module resolution. A shared `fixtures/index.ts` or `import` with `resolveJsonModule` improves maintainability.

## Detection hints
- `@typescript-eslint/no-require-imports` disable comments in e2e specs
- Duplicate fixture require blocks across multiple spec files
