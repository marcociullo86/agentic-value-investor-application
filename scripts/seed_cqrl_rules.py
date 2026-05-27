#!/usr/bin/env python3
"""One-off seed for canonical CQRL rules (Sprint 16)."""
from pathlib import Path

RULES = Path(__file__).resolve().parents[1] / "code_quality" / "rules" / "canonical"
RULES.mkdir(parents=True, exist_ok=True)

RULES_DATA = {
    "kotlin.spring.di.constructor_injection": (
        "kotlin", "spring", "medium",
        "Prefer constructor injection over field injection",
        "Constructor injection keeps dependencies explicit and testable.",
        "@Autowired on private lateinit var fields",
    ),
    "kotlin.spring.errorhandling.problemdetail_rfc9457": (
        "kotlin", "spring", "high",
        "API errors use RFC 9457 ProblemDetail with flat extensions",
        "ADR-012: extension members at top level in JSON responses.",
        "Nested details object instead of flat extensions",
    ),
    "kotlin.spring.resilience.external_api_guard": (
        "kotlin", "spring", "high",
        "External HTTP clients use rate limit, retry, timeout",
        "FMP calls require Resilience4j per ADR-016.",
        "Unguarded RestClient/WebClient for third-party APIs",
    ),
    "kotlin.spring.api.thin_controller": (
        "kotlin", "spring", "medium",
        "Controllers delegate business logic to services",
        "Fat controllers hinder testing.",
        "Repository access or >30 LOC rules in @RestController",
    ),
    "kotlin.spring.nullability.explicit_api": (
        "kotlin", "spring", "medium",
        "Public API types use explicit nullability",
        "Unchecked nulls cause NPE in production.",
        "Platform types or !! on external API data",
    ),
    "kotlin.spring.security.no_secrets_in_source": (
        "kotlin", "spring", "critical",
        "No secrets or API keys in source code",
        "Secrets in repo are security incidents.",
        "Hardcoded apikey= or JWT secrets in .kt files",
    ),
    "kotlin.spring.persistence.transaction_boundary": (
        "kotlin", "spring", "medium",
        "Write operations use @Transactional at service layer",
        "Multi-step writes need transactional boundaries.",
        "Multiple save() without @Transactional",
    ),
    "kotlin.spring.logging.no_pii_in_logs": (
        "kotlin", "spring", "high",
        "Logs must not contain raw PII or credentials",
        "GDPR / EP-014 fintech hardening.",
        "log.info with password or refresh token",
    ),
    "kotlin.spring.design.single_responsibility_service": (
        "kotlin", "spring", "low",
        "Services have a single cohesive responsibility",
        "God-services become change bottlenecks.",
        "Service >500 LOC mixing unrelated domains",
    ),
    "kotlin.spring.robustness.validate_input": (
        "kotlin", "spring", "medium",
        "Public endpoints validate input",
        "Unvalidated input propagates to adapters and DB.",
        "@RequestBody without @Valid",
    ),
    "typescript.nextjs.boundary.client_server_split": (
        "typescript", "nextjs", "medium",
        "Respect server vs client component boundaries",
        "Misplaced use client increases bundle size.",
        "useState without use client directive",
    ),
    "typescript.nextjs.hooks.rules_of_hooks": (
        "typescript", "react", "high",
        "Hooks follow Rules of Hooks",
        "Conditional hooks cause runtime failures.",
        "useEffect inside if branches",
    ),
    "typescript.nextjs.errorhandling.user_safe_messages": (
        "typescript", "nextjs", "medium",
        "User-facing errors use notification service",
        "EP-015 consistent error UX.",
        "alert(err.message) with backend stack",
    ),
    "typescript.nextjs.accessibility.interactive_name": (
        "typescript", "react", "high",
        "Interactive controls have accessible names",
        "WCAG / EP-016 a11y.",
        "Icon-only button without aria-label",
    ),
    "typescript.react.typing.avoid_any": (
        "typescript", "react", "low",
        "Avoid explicit any in production code",
        "any defeats API type safety.",
        ": any on props without narrowing",
    ),
    "typescript.nextjs.security.no_secrets_in_client": (
        "typescript", "nextjs", "critical",
        "No secrets in client bundles",
        "Client env vars are public.",
        "NEXT_PUBLIC_ on API keys",
    ),
    "typescript.nextjs.design.semantic_tokens": (
        "typescript", "nextjs", "low",
        "UI uses semantic design tokens",
        "ADR-023 M3-aligned tokens.",
        "Hardcoded hex colors on shared components",
    ),
    "typescript.nextjs.robustness.loading_error_states": (
        "typescript", "nextjs", "medium",
        "Data views expose loading and error states",
        "Silent failures confuse users.",
        "fetch without loading or error UI",
    ),
    "typescript.nextjs.testing.playwright_getbyrole": (
        "typescript", "nextjs", "medium",
        "E2E prefer getByRole over brittle selectors",
        "Role selectors align with a11y.",
        "CSS class chains as primary locators",
    ),
    "qa.testing.meaningful_assertion": (
        "typescript", "none", "low",
        "Tests assert behavior not implementation",
        "Brittle tests break on refactors.",
        "toBeTruthy() only or snapshot-only tests",
    ),
}

for rule_id, (lang, fw, sev, title, rationale, hints) in RULES_DATA.items():
    fw_line = f'  framework: {fw}\n' if fw != "none" else ""
    body = f"""---
rule_id: {rule_id}
version: v1
tier: canonical
title: "{title}"
applies_to:
  language: {lang}
{fw_line}  context: []
severity_default: {sev}
auto_fixable: false
status: active
metadata:
  created_at: "2026-05-27"
  author: "agent:cqrl-bootstrap"
---
# Regola

## Rationale
{rationale}

## Detection hints
{hints}
"""
    (RULES / f"{rule_id}.md").write_text(body, encoding="utf-8")

print(f"Wrote {len(RULES_DATA)} rules to {RULES}")
