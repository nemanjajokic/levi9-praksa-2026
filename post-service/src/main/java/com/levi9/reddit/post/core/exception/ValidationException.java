package com.levi9.reddit.post.core.exception;

/** 422: the request is well-formed but breaks a business rule. */
public sealed class ValidationException extends DomainException
        permits CommunityUnavailableException, TooManyImagesException, UnsupportedImageTypeException,
                ImageTooLargeException {
    public ValidationException(ErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
