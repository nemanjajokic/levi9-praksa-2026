package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.core.exception.ConflictException;
import com.levi9.reddit.auth.core.exception.DomainException;
import com.levi9.reddit.auth.core.exception.ForbiddenException;
import com.levi9.reddit.auth.core.exception.NotFoundException;
import com.levi9.reddit.auth.core.exception.TooManyRequestsException;
import com.levi9.reddit.auth.core.exception.UnauthorizedException;
import com.levi9.reddit.auth.core.exception.UserBannedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Turns every error leaving a controller into an RFC 9457 problem detail.
 * Domain exceptions get their status, detail and errorType; anything else is a fault and gets a bare 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusOf(ex), ex.getMessage());
        problem.setProperty("errorType", ex.getErrorType().name());
        if (ex instanceof UserBannedException banned && banned.getBanReason() != null) {
            problem.setProperty("banReason", banned.getBanReason());
        }
        return problem;
    }

    // More specific than DomainException, so Spring picks this one: same body, plus the Retry-After header.
    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(wholeSecondsRoundedUp(ex.getRetryAfter())))
                .body(handleDomain(ex));
    }

    // Security exceptions thrown from method security must reach Spring Security's own handling,
    // otherwise the catch-all below would turn a 401/403 into a 500.
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurity(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Bean Validation failures: keep Spring's 400 and add which fields failed.
    // The rejected value is left out on purpose, it could be a password.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldViolation(e.getField(), Objects.requireNonNullElse(e.getDefaultMessage(), "is invalid")))
                .toList();
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static HttpStatus statusOf(DomainException ex) {
        return switch (ex) {
            case UnauthorizedException _ -> HttpStatus.UNAUTHORIZED;
            case ForbiddenException _ -> HttpStatus.FORBIDDEN;
            case ConflictException _ -> HttpStatus.CONFLICT;
            case NotFoundException _ -> HttpStatus.NOT_FOUND;
            case TooManyRequestsException _ -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }

    /** Retry-After takes whole seconds. Rounded up and at least 1, so a client that obeys it is never too early. */
    static long wholeSecondsRoundedUp(Duration duration) {
        long seconds = duration.toSeconds() + (duration.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }

    record FieldViolation(String field, String message) {}
}
