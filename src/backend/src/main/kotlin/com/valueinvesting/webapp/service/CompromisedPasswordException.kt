package com.valueinvesting.webapp.service

/**
 * Raised when HIBP k-anonymity check finds the password in a known breach.
 * Message is user-facing and must not include breach counts (TSK-231).
 */
class CompromisedPasswordException :
    RuntimeException(
        "This password has appeared in a known data breach. Please choose a different password.",
    )
