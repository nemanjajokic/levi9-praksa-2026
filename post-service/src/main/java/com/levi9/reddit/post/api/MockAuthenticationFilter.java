package com.levi9.reddit.post.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Temporary stand-in for JWT validation while auth-service cannot issue access tokens. LOCAL ONLY.
 *
 * <p>Puts a made-up {@link Jwt} with the same claims auth-service will issue ({@code sub}, {@code username},
 * {@code roles}) into the security context, so controllers already use {@code @AuthenticationPrincipal Jwt}
 * and nothing changes when the real resource server replaces this filter.
 *
 * <p>The caller can be switched per request with optional headers, e.g. to try editing someone else's post:
 * <ul>
 *   <li>{@code X-Mock-User-Id}: numeric user id (default {@value #DEFAULT_USER_ID})</li>
 *   <li>{@code X-Mock-Username}: username (default {@value #DEFAULT_USERNAME})</li>
 *   <li>{@code X-Mock-Roles}: comma-separated roles, e.g. {@code USER,ADMIN} (default {@value #DEFAULT_ROLES})</li>
 * </ul>
 */
final class MockAuthenticationFilter extends OncePerRequestFilter {
    static final String USER_ID_HEADER = "X-Mock-User-Id";
    static final String USERNAME_HEADER = "X-Mock-Username";
    static final String ROLES_HEADER = "X-Mock-Roles";

    static final String DEFAULT_USER_ID = "1";
    static final String DEFAULT_USERNAME = "mockuser";
    static final String DEFAULT_ROLES = "USER";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && !(current instanceof AnonymousAuthenticationToken)) {
            chain.doFilter(request, response); // already authenticated (e.g. in tests): leave it alone
            return;
        }

        String userId = headerOrDefault(request, USER_ID_HEADER, DEFAULT_USER_ID);
        if (!userId.matches("\\d{1,18}")) {
            ProblemResponses.write(request, response, HttpStatus.BAD_REQUEST,
                    USER_ID_HEADER + " must be a positive number");
            return;
        }
        String username = headerOrDefault(request, USERNAME_HEADER, DEFAULT_USERNAME);
        List<String> roles = Arrays.stream(headerOrDefault(request, ROLES_HEADER, DEFAULT_ROLES).split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .toList();

        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .issuer("levddit-auth")
                .subject(userId)
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .build();
        List<GrantedAuthority> authorities = roles.stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        // Same principal type and name (= sub) as the real resource server produces.
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities, userId));
        chain.doFilter(request, response);
    }

    private static String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
