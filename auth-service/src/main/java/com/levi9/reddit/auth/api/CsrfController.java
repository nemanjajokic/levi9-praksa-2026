package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.contracts.CsrfResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The CSRF token the login and register pages send back in a header. It lives in auth-service's session, not in a
 * cookie: the gateway already uses the {@code XSRF-TOKEN} cookie for its own check, and the two would overwrite
 * each other.
 */
@RestController
class CsrfController {

    @GetMapping("/api/auth/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }
}
