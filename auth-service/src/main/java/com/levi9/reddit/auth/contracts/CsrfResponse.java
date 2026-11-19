package com.levi9.reddit.auth.contracts;

/** Body of {@code GET /api/auth/csrf}: send {@code token} in the {@code headerName} header on login and register. */
public record CsrfResponse(String headerName, String token) {
}
