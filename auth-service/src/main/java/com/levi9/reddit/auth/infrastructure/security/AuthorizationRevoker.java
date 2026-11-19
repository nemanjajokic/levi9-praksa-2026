package com.levi9.reddit.auth.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ends every session a user has. Deleting their {@code oauth2_authorization} rows makes the gateway's next
 * refresh fail with {@code invalid_grant}, so the gateway session loses its tokens. Access tokens already issued
 * stay valid until they expire (15 minutes): they are self-contained JWTs.
 * <p>
 * Spring's {@code OAuth2AuthorizationService} has no "remove all for a principal", hence plain SQL. The principal
 * name is the user id (see {@code UserAccountService}). Runs in the caller's transaction.
 */
@Component
public class AuthorizationRevoker {
    private final JdbcTemplate jdbc;

    public AuthorizationRevoker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int revokeAllOf(long userId) {
        return jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", String.valueOf(userId));
    }
}
