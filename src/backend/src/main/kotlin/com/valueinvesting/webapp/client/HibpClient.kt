package com.valueinvesting.webapp.client

/**
 * Have I Been Pwned Pwned Passwords range API (k-anonymity).
 *
 * Only the first five characters of the SHA-1 hash are sent to the remote API;
 * the full password never leaves the application.
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5]
 */
interface HibpClient {

    /**
     * @return `true` when the password hash suffix appears in the HIBP range response.
     */
    fun isPasswordCompromised(plainPassword: String): Boolean
}
