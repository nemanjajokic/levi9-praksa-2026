package com.levi9.reddit.post.core.exception;

/**
 * Stable error codes sent to clients as the {@code errorType} problem-detail extension.
 * Part of the API contract: renaming a value is a breaking change for the frontend.
 */
public enum ErrorType {
    POST_NOT_FOUND,
    NOT_POST_AUTHOR,
    POST_REMOVAL_FORBIDDEN,
    NOT_COMMUNITY_MEMBER,
    COMMUNITY_UNAVAILABLE,
    TOO_MANY_IMAGES,
    UNSUPPORTED_IMAGE_TYPE,
    IMAGE_TOO_LARGE,
    IMAGE_NOT_FOUND
}
