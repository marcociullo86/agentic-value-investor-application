package com.valueinvesting.webapp.api.error

import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.service.EmailAlreadyRegisteredException
import com.valueinvesting.webapp.service.InvalidRefreshTokenException
import com.valueinvesting.webapp.service.TickerNotInWatchlistException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
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
    // L'eccezione vive in service/exception/InvalidRefreshTokenException.kt
    // e viene sollevata da AuthService.refresh() (vedi TSK-041).
    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun handleInvalidRefreshToken(
        ex: InvalidRefreshTokenException,
        req: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = mapper.build(
            status = HttpStatus.UNAUTHORIZED,
            type = "https://api/errors/invalid-refresh",
            title = "Unauthorized",
            detail = ex.message ?: "Invalid refresh token",
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

    // US-018 AC#2 — registrazione con email duplicata → 409 RFC 9457
    // (ADR-010 §1). L'email viene riflessa nel `detail` perché il client
    // l'ha appena inviata: nessun information leak (l'avversario già la
    // possiede). Sull'endpoint `/login` invece NON la riflettiamo (vedi
    // handleBadCredentials), così il client non può fare enum-attack.
    // [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §1]
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
