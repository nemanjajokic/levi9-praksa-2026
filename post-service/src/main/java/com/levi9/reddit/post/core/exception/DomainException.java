package com.levi9.reddit.post.core.exception;

import java.util.Objects;

/**
 * An expected failure a correct client can cause. The subtype decides the HTTP status (see
 * {@code ApiExceptionHandler}); unchecked, so {@code @Transactional} rolls back on it.
 */
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, ForbiddenException, ValidationException {
    private final ErrorType errorType;

    protected DomainException(ErrorType errorType, String detail) {
        super(detail);
        this.errorType = Objects.requireNonNull(errorType, "errorType");
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
