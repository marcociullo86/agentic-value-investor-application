package com.valueinvesting.webapp.service

/**
 * MFA-flow exceptions mapped to RFC 9457 ProblemDetail by GlobalExceptionHandler.
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
 */
class MfaAlreadyEnabledException : RuntimeException("MFA is already enabled for this account")

class MfaNotEnrolledException : RuntimeException("MFA enrollment has not been initiated for this account")

class MfaNotEnabledException : RuntimeException("MFA is not enabled for this account")

class InvalidTotpCodeException : RuntimeException("Invalid TOTP code")

class InvalidRecoveryCodeException : RuntimeException("Invalid or already-used recovery code")
