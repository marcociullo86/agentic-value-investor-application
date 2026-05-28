---
rule_id: kotlin.spring.testing.bcrypt_cost_factor
version: v1
tier: emergent
title: Use low BCrypt cost factor in unit and integration tests
applies_to:
  language: kotlin
  framework: spring
  context: [design, robustness, qa]
severity_default: low
auto_fixable: true
status: candidate
metadata:
  created_at: "2026-05-28"
  author: "agent:code-reviewer@2.12.0"
  observed_in: "TSK-234 / MfaServiceTest.kt:46"
---
# Regola

## Rationale
`BCryptPasswordEncoder` with a production cost factor (≥ 10) inside unit or integration tests adds 300–600 ms per `encode()`/`matches()` call. A typical test class that encodes a handful of passwords per test can add 5–15 seconds to the suite, which accumulates significantly in CI with parallel Testcontainers runs. The production strength is irrelevant in tests because no attacker is brute-forcing test data. Use cost factor 4 in test scope or a `NoOpPasswordEncoder` / `mockk` mock where password-strength semantics are not under test.

## Detection hints
- `BCryptPasswordEncoder(` with argument ≥ 8 in any file under `src/test/`
- `BCryptPasswordEncoder()` (default = cost 10) in test classes

## Fix template
```kotlin
// Before (slow in CI)
private val passwordEncoder = BCryptPasswordEncoder(12)

// After (test-safe)
private val passwordEncoder = BCryptPasswordEncoder(4)
// or, when password correctness is not the SUT:
private val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
```

## Exceptions
- Integration tests that explicitly verify BCrypt round-trip semantics (e.g. `CryptographyRoundTripTest`) may retain a higher cost factor with an inline comment explaining the reason.
