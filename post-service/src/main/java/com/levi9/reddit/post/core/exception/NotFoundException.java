package com.levi9.reddit.post.core.exception;

/** 404. */
public sealed class NotFoundException extends DomainException
        permits PostNotFoundException, ImageNotFoundException {
    public NotFoundException(ErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
