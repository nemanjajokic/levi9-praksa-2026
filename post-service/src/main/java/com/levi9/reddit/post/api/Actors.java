package com.levi9.reddit.post.api;

import com.levi9.reddit.post.core.model.Actor;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** The one place that knows the access-token claims (see docs/security-convention.md, "Access token claims"). */
final class Actors {
    static final String USERNAME_CLAIM = "username";
    static final String ROLES_CLAIM = "roles";
    static final String ADMIN_ROLE = "ADMIN";

    private Actors() {}

    static Actor from(Jwt jwt) {
        long id = Long.parseLong(jwt.getSubject());
        String username = jwt.getClaimAsString(USERNAME_CLAIM);
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        boolean admin = roles != null && roles.contains(ADMIN_ROLE);
        return new Actor(id, username, admin);
    }
}
