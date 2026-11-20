package com.levi9.reddit.post.api;

import com.levi9.reddit.post.core.exception.DomainException;
import com.levi9.reddit.post.core.exception.ForbiddenException;
import com.levi9.reddit.post.core.exception.NotFoundException;
import com.levi9.reddit.post.core.exception.ValidationException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Objects;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error leaving a controller into an RFC 9457 problem detail (see docs/error-convention.md).
 * Domain exceptions get their status, detail and {@code errorType}; validation failures get a 400 with
 * {@code errors}; anything else is a fault and gets a bare 500. Spring's own errors (malformed JSON, wrong
 * parameter type, unsupported media type) keep the base class's status.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String VALIDATION_FAILED = "Request validation failed";

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusOf(ex), ex.getMessage());
        problem.setProperty("errorType", ex.getErrorType().name());
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastSegment(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return problem;
    }

    // Security exceptions thrown from method security must reach Spring Security's own handling,
    // otherwise the catch-all below would turn a 401/403 into a 500.
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurity(RuntimeException ex) {
        throw ex;
    }

    // 500 never has a detail: the cause goes to the log only.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // @Valid on a @RequestBody / @RequestPart. The rejected value is left out on purpose.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldViolation(e.getField(), messageOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ex.getBody();
        problem.setDetail(VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // @Min/@Max/@Size on @RequestParam are checked by method validation, not by @Valid on a body.
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                messageOf(error.getDefaultMessage()))))
                .toList();
        ProblemDetail problem = ex.getBody();
        problem.setDetail(VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // Sealed hierarchy: a new DomainException subtype doesn't compile until it gets a status here.
    private static HttpStatus statusOf(DomainException ex) {
        return switch (ex) {
            case NotFoundException _ -> HttpStatus.NOT_FOUND;
            case ForbiddenException _ -> HttpStatus.FORBIDDEN;
            case ValidationException _ -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    private static String messageOf(String message) {
        return Objects.requireNonNullElse(message, "is invalid");
    }

    private static String lastSegment(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }

    record FieldViolation(String field, String message) {}
}
