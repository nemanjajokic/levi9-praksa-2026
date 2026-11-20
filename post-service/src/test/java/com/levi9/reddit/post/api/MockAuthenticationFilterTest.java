package com.levi9.reddit.post.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class MockAuthenticationFilterTest {

    private final MockAuthenticationFilter filter = new MockAuthenticationFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/posts");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /** What the next filter in the chain sees; null if the chain was never called. */
    private final AtomicReference<Authentication> seenByChain = new AtomicReference<>();
    private final AtomicBoolean chainCalled = new AtomicBoolean();

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withoutHeaders_usesDefaultCaller() throws Exception {
        runFilter();

        JwtAuthenticationToken auth = jwtAuthentication();
        Jwt jwt = auth.getToken();
        assertThat(auth.getName()).isEqualTo("1");
        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("mockuser");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("levddit-auth");
        assertThat(authorities(auth)).containsExactly("ROLE_USER");
    }

    @Test
    void headers_overrideTheCaller() throws Exception {
        request.addHeader(MockAuthenticationFilter.USER_ID_HEADER, "42");
        request.addHeader(MockAuthenticationFilter.USERNAME_HEADER, "marko");
        request.addHeader(MockAuthenticationFilter.ROLES_HEADER, "USER,ADMIN");

        runFilter();

        JwtAuthenticationToken auth = jwtAuthentication();
        assertThat(auth.getName()).isEqualTo("42");
        assertThat(auth.getToken().getClaimAsString("username")).isEqualTo("marko");
        assertThat(auth.getToken().getClaimAsStringList("roles")).containsExactly("USER", "ADMIN");
        assertThat(authorities(auth)).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void roles_areTrimmedUppercasedAndEmptyEntriesSkipped() throws Exception {
        request.addHeader(MockAuthenticationFilter.ROLES_HEADER, " admin , ,user ");

        runFilter();

        assertThat(authorities(jwtAuthentication())).containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void blankHeaders_fallBackToDefaults() throws Exception {
        request.addHeader(MockAuthenticationFilter.USER_ID_HEADER, "  ");
        request.addHeader(MockAuthenticationFilter.USERNAME_HEADER, "");

        runFilter();

        JwtAuthenticationToken auth = jwtAuthentication();
        assertThat(auth.getName()).isEqualTo("1");
        assertThat(auth.getToken().getClaimAsString("username")).isEqualTo("mockuser");
    }

    @Test
    void nonNumericUserId_returnsBadRequestAndStopsTheChain() throws Exception {
        request.addHeader(MockAuthenticationFilter.USER_ID_HEADER, "abc");

        runFilter();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(chainCalled).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void negativeUserId_returnsBadRequest() throws Exception {
        request.addHeader(MockAuthenticationFilter.USER_ID_HEADER, "-5");

        runFilter();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void existingAuthentication_isLeftAlone() throws Exception {
        var existing = UsernamePasswordAuthenticationToken.authenticated(
                "7", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader(MockAuthenticationFilter.USER_ID_HEADER, "42");

        runFilter();

        assertThat(seenByChain.get()).isSameAs(existing);
    }

    @Test
    void anonymousAuthentication_isReplacedByMockCaller() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        runFilter();

        assertThat(jwtAuthentication().getName()).isEqualTo("1");
    }

    private void runFilter() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            seenByChain.set(SecurityContextHolder.getContext().getAuthentication());
        });
    }

    private JwtAuthenticationToken jwtAuthentication() {
        assertThat(chainCalled).as("filter chain called").isTrue();
        assertThat(seenByChain.get()).isInstanceOf(JwtAuthenticationToken.class);
        JwtAuthenticationToken auth = (JwtAuthenticationToken) seenByChain.get();
        assertThat(auth.isAuthenticated()).isTrue();
        return auth;
    }

    private static List<String> authorities(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
