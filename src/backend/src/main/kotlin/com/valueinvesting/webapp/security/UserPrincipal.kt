package com.valueinvesting.webapp.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

/**
 * Spring Security principal carrying the authenticated user's UUID.
 *
 * Controllers obtain `UserPrincipal` via `@AuthenticationPrincipal` — eliminates
 * the X-User-Id header stub previously used in DcfOverrideController (TSK-017).
 */
data class UserPrincipal(
    val userId: UUID,
    val emailValue: String,
    val passwordHash: String = "",
) : UserDetails {

    override fun getAuthorities() = listOf(SimpleGrantedAuthority("ROLE_USER"))

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = emailValue

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true
}
