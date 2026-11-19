package com.levi9.reddit.gateway;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Who is logged in, for the page: the browser cannot read any token, so this is how it learns. Answered from the
 * session (the ID token's claims), with no call to auth-service. Without a session: 401 (see {@link SecurityConfig}).
 */
@RestController
class MeController {

    @GetMapping("/api/me")
    MeResponse me(@AuthenticationPrincipal OidcUser user) {
        List<String> roles = user.getClaimAsStringList("roles");
        return new MeResponse(Long.parseLong(user.getSubject()), user.getClaimAsString("username"),
                roles != null ? roles : List.of());
    }

    record MeResponse(long id, String username, List<String> roles) {
    }
}
