package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RefreshRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.TokenPairResponse
import com.valueinvesting.webapp.api.model.UserProfileResponse
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * EP-006 authentication endpoints.
 *
 * [^src: design_&_architecture/api/openapi.yaml §paths /api/auth/*]
 * [^src: design_&_architecture/decisions/ADR-006-authentication.md §Endpoint policy]
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<UserProfileResponse> {
        val profile = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(profile)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenPairResponse> =
        ResponseEntity.ok(authService.login(request))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<TokenPairResponse> =
        ResponseEntity.ok(authService.refresh(request.refreshToken))

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody(required = false) request: RefreshRequest?,
    ): ResponseEntity<Void> {
        authService.logout(principal.userId, request?.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
