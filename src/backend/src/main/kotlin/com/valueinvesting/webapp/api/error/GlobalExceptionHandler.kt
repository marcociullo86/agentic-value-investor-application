package com.valueinvesting.webapp.api.error

import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.llm.LlmFrozenException
import com.valueinvesting.webapp.service.AccountLockedException
import com.valueinvesting.webapp.service.CaptchaRequiredException
import com.valueinvesting.webapp.service.CompromisedPasswordException
import com.valueinvesting.webapp.service.EmailAlreadyRegisteredException
import com.valueinvesting.webapp.service.FilingsNotIndexedException
import com.valueinvesting.webapp.service.InvalidRecoveryCodeException
import com.valueinvesting.webapp.service.InvalidRefreshTokenException
import com.valueinvesting.webapp.service.InvalidTotpCodeException
import com.valueinvesting.webapp.service.LlmUnavailableException
import com.valueinvesting.webapp.service.MfaAlreadyEnabledException
import com.valueinvesting.webapp.service.MfaNotEnabledException
import com.valueinvesting.webapp.service.MfaNotEnrolledException
import com.valueinvesting.webapp.service.NoSecFilingsException
import com.valueinvesting.webapp.service.TickerNotInWatchlistException
import com.valueinvesting.webapp.service.exception.DcfMethodUnfeasibleException
import com.valueinvesting.webapp.service.exception.DcfOverrideNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException

// Centralized exception → ProblemDetails mapper (RFC 9457).
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Status codes + Error format]
// [^src: design_&_architecture/components/backend-components.md §API LAYER]
@RestControllerAdvice
class GlobalExceptionHandler(
    private val mapper: ProblemDetailsMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val errors = ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to (it.defaultMessage ?: "invalid"))
        }
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/validation-failed",
            title = "Validation failed",
            detail = "Request body validation failed",
            request = req,
            extensions = mapOf("errors" to errors),
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraint(
        ex: ConstraintViolationException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/constraint-violation",
            title = "Constraint violation",
            detail = ex.message ?: "Constraint violation",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    // Generic-error policy for login (ADR-010 §2, US-019 AC#2): email
    // inesistente e password errata producono lo stesso 401 con
    // detail="Invalid email or password" e type=invalid-credentials.
    // AuthService.login() solleva BadCredentialsException con quel testo
    // su entrambi i rami — la verifica formale vive nel contract-test
    // di TSK-042.
    // [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §2]
    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(
        ex: BadCredentialsException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.UNAUTHORIZED,
            type = "https://api/errors/invalid-credentials",
            title = "Unauthorized",
            detail = ex.message ?: "Invalid email or password",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    // Refresh-token specific 401 (ADR-010 §3): catena rifiutata per expiry,
    // revoca o cap assoluto raggiunto. Tipo distinto da invalid-credentials
    // così il FE può differenziare "ri-login" da "credenziali errate".
    // L'eccezione vive in service/InvalidRefreshTokenException.kt e viene
    // sollevata da AuthService.refresh() (vedi TSK-041).
    //
    // Anti-enum-attack (TSK-041 finding iter-1): la `detail` esposta al
    // client è il valore uniforme [InvalidRefreshTokenException.CLIENT_DETAIL]
    // — mai `ex.reason`. La causa specifica (revoked / sliding_expired /
    // absolute_cap / not_found / user_unknown) finisce solo nel log
    // server-side, così un attaccante non può discriminare via response.
    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun handleInvalidRefreshToken(
        ex: InvalidRefreshTokenException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn(
            "Invalid refresh token: reason={} on {} {}",
            ex.reason,
            req.method,
            req.requestURI,
        )
        val problem = mapper.build(
            status = HttpStatus.UNAUTHORIZED,
            type = "https://api/errors/invalid-refresh",
            title = "Unauthorized",
            detail = InvalidRefreshTokenException.CLIENT_DETAIL,
            request = req,
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(
        ex: AuthenticationException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.UNAUTHORIZED,
            type = "https://api/errors/unauthorized",
            title = "Unauthorized",
            detail = "Authentication required or invalid",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.FORBIDDEN,
            type = "https://api/errors/forbidden",
            title = "Forbidden",
            detail = "Access denied",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFound(
        ex: NoHandlerFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.NOT_FOUND,
            type = "https://api/errors/not-found",
            title = "Not Found",
            detail = "Resource not found: ${ex.requestURL}",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    // Spring throws MethodArgumentTypeMismatchException when a query/path param
    // cannot be bound to the controller parameter type (e.g. ?marketCap=NANO when
    // MarketCapBand has no NANO entry). Map to 400 ProblemDetails per RFC 9457
    // — non gestirlo significherebbe 500 via handleGeneric.
    // [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §Test]
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val paramName = ex.name
        val requiredType = ex.requiredType?.simpleName ?: "unknown"
        val value = ex.value?.toString() ?: "null"
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/type-mismatch",
            title = "Bad Request",
            detail = "Parameter '$paramName' value '$value' is not a valid $requiredType",
            request = req,
            extensions = mapOf(
                "parameter" to paramName,
                "rejectedValue" to value,
                "requiredType" to requiredType,
            ),
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    // Missing @RequestParam(required = true) → 400 ProblemDetails (RFC 9457).
    // Otherwise falls through to handleGeneric -> 500.
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(
        ex: MissingServletRequestParameterException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/missing-parameter",
            title = "Bad Request",
            detail = "Required parameter '${ex.parameterName}' is missing",
            request = req,
            extensions = mapOf(
                "parameter" to ex.parameterName,
                "requiredType" to ex.parameterType,
            ),
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    // @CookieValue(required=true) on /api/auth/refresh throws
    // MissingRequestCookieException when the cookie is absent.
    // Without this handler the generic Exception catch-all returns 500.
    @ExceptionHandler(MissingRequestCookieException::class)
    fun handleMissingCookie(
        ex: MissingRequestCookieException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/missing-cookie",
            title = "Bad Request",
            detail = "Required cookie '${ex.cookieName}' is missing",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/illegal-argument",
            title = "Bad Request",
            detail = ex.message ?: "Illegal argument",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    // FMP integration errors -> RFC 9457 ProblemDetails
    // [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Fallback]
    // [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Status codes]
    @ExceptionHandler(FmpTickerNotFoundException::class)
    fun handleFmpTickerNotFound(
        ex: FmpTickerNotFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.NOT_FOUND,
            type = "https://api/errors/ticker-not-found",
            title = "Ticker not found",
            detail = "Ticker '${ex.ticker}' not found on FMP",
            request = req,
            extensions = mapOf("ticker" to ex.ticker),
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(FmpUnavailableException::class)
    fun handleFmpUnavailable(
        ex: FmpUnavailableException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn("FMP unavailable on {} {}: {}", req.method, req.requestURI, ex.message)
        val problem = mapper.build(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            type = "https://api/errors/fmp-unavailable",
            title = "Service Unavailable",
            detail = ex.message ?: "FMP unavailable",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem)
    }

    @ExceptionHandler(TickerNotInWatchlistException::class)
    fun handleTickerNotInWatchlist(
        ex: TickerNotInWatchlistException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.NOT_FOUND,
            type = "https://api/errors/ticker-not-in-watchlist",
            title = "Ticker not in watchlist",
            detail = "Ticker '${ex.ticker}' is not in the watchlist",
            request = req,
            extensions = mapOf("ticker" to ex.ticker),
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(DcfOverrideNotFoundException::class)
    fun handleDcfOverrideNotFound(
        ex: DcfOverrideNotFoundException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.NOT_FOUND,
            type = "https://api/errors/dcf-override-not-found",
            title = "DCF override not found",
            detail = "No DCF override for ticker '${ex.ticker}'",
            request = req,
            extensions = mapOf("ticker" to ex.ticker),
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(DcfMethodUnfeasibleException::class)
    fun handleDcfMethodUnfeasible(
        ex: DcfMethodUnfeasibleException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val detail = when (ex.reason) {
            "PPE_RATIO_HISTORY_INSUFFICIENT" ->
                "Greenwald requires ≥ ${ex.requiredYears} years of PPE_Ratio history; ticker has ${ex.availableYears} years"
            "FCF_HISTORY_INSUFFICIENT" ->
                "FCF fallback requires ≥ ${ex.requiredYears} year(s) of FCF history; ticker has ${ex.availableYears} years"
            else -> "DCF method ${ex.method.name} is not feasible for this ticker"
        }
        val problem = mapper.build(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            type = "https://api/errors/dcf-method-unfeasible",
            title = "DCF method not feasible",
            detail = detail,
            request = req,
            extensions = mapOf(
                "method" to ex.method.name,
                "reason" to ex.reason,
                "availableYears" to ex.availableYears,
                "requiredYears" to ex.requiredYears,
            ),
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    // US-018 AC#2 — registrazione con email duplicata → 409 RFC 9457
    // (ADR-010 §1). L'email viene riflessa nel `detail` perché il client
    // l'ha appena inviata: nessun information leak (l'avversario già la
    // possiede). Sull'endpoint `/login` invece NON la riflettiamo (vedi
    // handleBadCredentials), così il client non può fare enum-attack.
    // [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §1]
    // US-081 / ADR-025 §5 — password in HIBP breach set; no count in detail.
    @ExceptionHandler(CompromisedPasswordException::class)
    fun handleCompromisedPassword(
        ex: CompromisedPasswordException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/password-compromised",
            title = "Password not allowed",
            detail = ex.message ?: "This password has appeared in a known data breach. Please choose a different password.",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun handleEmailConflict(
        ex: EmailAlreadyRegisteredException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.CONFLICT,
            type = "https://api/errors/email-already-registered",
            title = "Email already registered",
            detail = "Email already registered: ${ex.email}",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    // MFA flow errors (US-081 / ADR-025 §4 / TSK-228) — RFC 9457 ProblemDetail.
    // Distinct types let the FE branch on the specific failure (re-enroll vs.
    // already-enabled vs. wrong code) without parsing user-facing strings.
    @ExceptionHandler(MfaAlreadyEnabledException::class)
    fun handleMfaAlreadyEnabled(
        ex: MfaAlreadyEnabledException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.CONFLICT,
            type = "https://api/errors/mfa-already-enabled",
            title = "MFA already enabled",
            detail = ex.message ?: "MFA is already enabled for this account",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    @ExceptionHandler(MfaNotEnrolledException::class)
    fun handleMfaNotEnrolled(
        ex: MfaNotEnrolledException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.CONFLICT,
            type = "https://api/errors/mfa-not-enrolled",
            title = "MFA enrollment not started",
            detail = ex.message ?: "MFA enrollment has not been initiated",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    @ExceptionHandler(MfaNotEnabledException::class)
    fun handleMfaNotEnabled(
        ex: MfaNotEnabledException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.CONFLICT,
            type = "https://api/errors/mfa-not-enabled",
            title = "MFA not enabled",
            detail = ex.message ?: "MFA is not enabled for this account",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    @ExceptionHandler(InvalidTotpCodeException::class)
    fun handleInvalidTotpCode(
        ex: InvalidTotpCodeException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/invalid-totp-code",
            title = "Invalid TOTP code",
            detail = ex.message ?: "Invalid TOTP code",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    @ExceptionHandler(InvalidRecoveryCodeException::class)
    fun handleInvalidRecoveryCode(
        ex: InvalidRecoveryCodeException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.BAD_REQUEST,
            type = "https://api/errors/invalid-recovery-code",
            title = "Invalid recovery code",
            detail = ex.message ?: "Invalid or already-used recovery code",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    // ADR-025 §5 (TSK-230): account lockout after 20+ failed logins in 15min.
    // RFC 7231 §6.5.15 — 423 Locked is the canonical status; we also surface
    // `Retry-After` (seconds) so the FE can drive a countdown without storing
    // the lockout deadline locally.
    @ExceptionHandler(AccountLockedException::class)
    fun handleAccountLocked(
        ex: AccountLockedException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn(
            "Account locked on {} {}: retryAfter={}s",
            req.method,
            req.requestURI,
            ex.retryAfterSeconds,
        )
        val problem = mapper.build(
            status = HttpStatus.LOCKED,
            type = "https://api/errors/account-locked",
            title = "Account temporarily locked",
            // Generic detail (no email echo) to avoid enumeration — the same
            // 423 fires regardless of whether the locked account exists, in
            // line with the ADR-010 §2 generic-credentials policy.
            detail = "Account temporarily locked due to repeated failed login attempts",
            request = req,
            extensions = mapOf("retryAfterSeconds" to ex.retryAfterSeconds),
        )
        return ResponseEntity.status(HttpStatus.LOCKED)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .body(problem)
    }

    // ADR-025 §5 (TSK-230): per-IP CAPTCHA gate. 401 with `captchaRequired=true`
    // extension so the FE can show the Turnstile widget. The `detail` is the
    // standard "Invalid email or password" string so a wrong-password attempt
    // when the IP threshold trips looks indistinguishable from a normal failure
    // except for the captchaRequired flag (no leak of brute-force counters).
    @ExceptionHandler(CaptchaRequiredException::class)
    fun handleCaptchaRequired(
        ex: CaptchaRequiredException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn(
            "CAPTCHA required on {} {}: reason={}",
            req.method,
            req.requestURI,
            ex.reason,
        )
        val problem = mapper.build(
            status = HttpStatus.UNAUTHORIZED,
            type = "https://api/errors/captcha-required",
            title = "Captcha required",
            detail = "Invalid email or password",
            request = req,
            extensions = mapOf("captchaRequired" to true),
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    @ExceptionHandler(NoSecFilingsException::class)
    fun handleNoSecFilings(
        ex: NoSecFilingsException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn("No SEC filings for ticker={} on {} {}", ex.ticker, req.method, req.requestURI)
        val problem = mapper.build(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            type = "https://api/errors/no-sec-filings",
            title = "No SEC filings available",
            detail = "No SEC filings available for ticker '${ex.ticker}'",
            request = req,
            extensions = mapOf("ticker" to ex.ticker, "reason" to "no_sec_filings"),
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    // EP-011 split INGEST/ANALYSIS (V028): l'ANALYSIS-with-LLM richiede chunk
    // già indicizzati. Se l'utente non ha lanciato l'INGEST (o l'INGEST è
    // FAILED) → 409 Conflict reason=not_indexed così il FE può proporre
    // "lancia ingest prima di analisi LLM". Distinto da no_sec_filings: qui i
    // filing potrebbero esserci, è l'embedding store che è vuoto.
    @ExceptionHandler(FilingsNotIndexedException::class)
    fun handleFilingsNotIndexed(
        ex: FilingsNotIndexedException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn(
            "Filings not indexed for ticker={} on {} {}",
            ex.ticker, req.method, req.requestURI,
        )
        val problem = mapper.build(
            status = HttpStatus.CONFLICT,
            type = "https://api/errors/filings-not-indexed",
            title = "Filings not indexed",
            detail = ex.message ?: "Filings not indexed for ticker '${ex.ticker}'",
            request = req,
            extensions = mapOf("ticker" to ex.ticker, "reason" to "not_indexed"),
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    // ADR-019 §6 — explicit handler for the admin-driven freeze. Distinct from
    // generic LlmUnavailable (network/Anthropic outage) because:
    //   - distinct problem type so the FE can surface a "frozen by admin" banner;
    //   - "reason" stays human-readable for ops dashboards;
    //   - 503 is the appropriate status (service intentionally unavailable).
    @ExceptionHandler(LlmFrozenException::class)
    fun handleLlmFrozen(
        ex: LlmFrozenException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn("LLM frozen by admin on {} {}", req.method, req.requestURI)
        val problem = mapper.build(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            type = "https://api/errors/llm-frozen-by-admin",
            title = "LLM frozen by admin",
            detail = "LLM traffic is currently frozen by an administrator",
            request = req,
            extensions = mapOf("reason" to "llm_frozen_by_admin"),
        )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem)
    }

    @ExceptionHandler(LlmUnavailableException::class)
    fun handleLlmUnavailable(
        ex: LlmUnavailableException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error("LLM unavailable for ticker={} on {} {}: {}", ex.ticker, req.method, req.requestURI, ex.message)
        val problem = mapper.build(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            type = "https://api/errors/llm-unavailable",
            title = "LLM Service Unavailable",
            detail = "LLM service unavailable during deep analysis for ticker '${ex.ticker}'",
            request = req,
            extensions = mapOf("ticker" to ex.ticker, "reason" to "llm_unavailable"),
        )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception while serving {} {}", req.method, req.requestURI, ex)
        val problem = mapper.build(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            type = "https://api/errors/internal",
            title = "Internal Server Error",
            detail = "An unexpected error occurred",
            request = req,
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }
}
