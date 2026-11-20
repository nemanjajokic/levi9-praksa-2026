package com.levi9.reddit.post.api;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint that echoes the caller the way real controllers will read it. Its path is not under
 * {@code /api/posts}, so {@link SecurityConfig} treats it as protected.
 */
@RestController
class WhoAmIController {
    static final String PATH = "/test/whoami";

    @GetMapping(PATH)
    Map<String, Object> whoAmI(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "id", Long.parseLong(jwt.getSubject()),
                "username", jwt.getClaimAsString("username"),
                "roles", jwt.getClaimAsStringList("roles"));
    }
}
