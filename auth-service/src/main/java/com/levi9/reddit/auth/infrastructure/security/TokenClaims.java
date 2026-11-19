package com.levi9.reddit.auth.infrastructure.security;

import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every time an access or ID token is about to be issued, for a code exchange and for a refresh alike, and does
 * two things.
 * <p>
 * <b>Refuses the token</b> ({@code 400 invalid_grant}) when the user may no longer have one: the account is deleted
 * or banned, or the password changed after this login. Deleting the user's authorizations
 * ({@link AuthorizationRevoker}) stops refreshes, but not auth-service's own login session: that session could still
 * get a new code from {@code /oauth2/authorize} without a password. This check is what closes that, at the only place
 * tokens come from. The login time is the {@code FACTOR_PASSWORD} authority Spring Security stamps at login; without
 * it the token is refused too.
 * <p>
 * <b>Adds the claims</b> {@code username} and {@code roles} (e.g. {@code ["USER"]}), the names the services read.
 * {@code sub} is already the user id: it is the principal name. Read from the database at every issue, so a role
 * change shows up at the next refresh.
 */
class TokenClaims implements OAuth2TokenCustomizer<JwtEncodingContext> {
    private final UserRepository users;

    TokenClaims(UserRepository users) {
        this.users = users;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }
        Authentication principal = context.getPrincipal();
        User user = users.findById(Long.parseLong(principal.getName())).orElseThrow(TokenClaims::refused);
        if (user.isDeleted() || user.isBanned() || loggedInBeforePasswordChange(principal, user)) {
            throw refused();
        }
        // A plain ArrayList, not List.of: the claims are stored as JSON with the authorization and read back on
        // refresh, and Spring's Jackson allowlist accepts ArrayList but not the JDK's internal immutable lists.
        context.getClaims()
                .claim("username", user.getUsername())
                .claim("roles", new ArrayList<>(List.of(user.getRole().name())));
    }

    private static boolean loggedInBeforePasswordChange(Authentication principal, User user) {
        Instant loggedInAt = passwordLoginTime(principal);
        if (loggedInAt == null) {
            return true; // no proof of when the password was typed: refuse rather than guess
        }
        Instant changedAt = user.getPasswordChangedAt();
        return changedAt != null && !loggedInAt.isAfter(changedAt);
    }

    private static Instant passwordLoginTime(Authentication principal) {
        for (GrantedAuthority authority : principal.getAuthorities()) {
            if (authority instanceof FactorGrantedAuthority factor
                    && FactorGrantedAuthority.PASSWORD_AUTHORITY.equals(factor.getAuthority())) {
                return factor.getIssuedAt();
            }
        }
        return null;
    }

    /** The same answer as an unknown or used-up code: the client only learns it has to log in again. */
    private static OAuth2AuthenticationException refused() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                "This login is no longer valid", null));
    }
}
