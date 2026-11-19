package com.levi9.reddit.auth.core.exception;

/**
 * Stable error codes sent to clients as the {@code errorType} problem-detail extension.
 * Part of the API contract: renaming a value is a breaking change for the frontend.
 */
public enum ErrorType {
    INVALID_CREDENTIALS,
    INVALID_REFRESH_TOKEN,
    USERNAME_TAKEN,
    EMAIL_TAKEN,
    USER_BANNED,
    USER_NOT_FOUND,
    ACCOUNT_DELETION_FORBIDDEN,
    /** Admins cannot be banned, including by themselves. */
    ADMIN_BAN_FORBIDDEN,
    USER_ALREADY_BANNED,
    USER_NOT_BANNED
}
