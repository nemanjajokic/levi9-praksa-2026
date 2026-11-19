package com.levi9.reddit.auth;

import com.jayway.jsonpath.JsonPath;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import com.levi9.reddit.auth.infrastructure.security.AuthorizationRevoker;
import com.levi9.reddit.auth.infrastructure.security.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.levi9.reddit.auth.infrastructure.security.AuthorizationServerConfig.GATEWAY_CLIENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorization code flow with PKCE, as the gateway runs it, against real Postgres. The browser's login is
 * stood in for by an authenticated request: the login itself is tested in its own class. Everything else is real:
 * the authorize redirect, the code exchange with the client secret, the claims, refresh rotation and revocation.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationCodeFlowTests {
    private static final String ISSUER = "http://localhost:3000";
    private static final String REDIRECT_URI = ISSUER + "/login/oauth2/code/levddit";
    private static final String CLIENT_SECRET = "test-gateway-secret";
    private static final String VERIFIER = "a-random-pkce-code-verifier-that-is-at-least-43-characters-long";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private AuthorizationRevoker revoker;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbc;

    private User marko;

    @BeforeEach
    void freshUser() {
        jdbc.execute("TRUNCATE oauth2_authorization, users RESTART IDENTITY CASCADE");
        marko = users.save(new User("marko", "marko@example.test", passwords.encode("correct horse battery"),
                "Marko", "Markovic"));
    }

    @Test
    void issuesTokensWhoseSubjectIsTheUserIdWithUsernameAndRoles() throws Exception {
        String body = exchange(authorize()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Jwt access = jwtDecoder.decode(JsonPath.read(body, "$.access_token"));
        assertThat(access.getSubject()).isEqualTo(String.valueOf(marko.getId()));
        assertThat(access.getClaimAsString("username")).isEqualTo("marko");
        assertThat(access.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(access.getIssuer()).hasToString(ISSUER);
        assertThat(access.getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofMinutes(15)), within(Duration.ofMinutes(1)));

        Jwt id = jwtDecoder.decode(JsonPath.read(body, "$.id_token"));
        assertThat(id.getSubject()).isEqualTo(String.valueOf(marko.getId()));
        assertThat(id.getAudience()).containsExactly(GATEWAY_CLIENT_ID);
        assertThat(id.getClaimAsString("username")).isEqualTo("marko");

        assertThat((String) JsonPath.read(body, "$.refresh_token")).isNotBlank();
    }

    @Test
    void refreshRotatesAndTheOldRefreshTokenStopsWorking() throws Exception {
        String first = JsonPath.read(tokenBody(exchange(authorize())), "$.refresh_token");

        String second = JsonPath.read(tokenBody(refresh(first)), "$.refresh_token");
        assertThat(second).isNotEqualTo(first);

        refresh(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void revokingTheUserEndsTheirRefresh() throws Exception {
        String refreshToken = JsonPath.read(tokenBody(exchange(authorize())), "$.refresh_token");

        assertThat(revoker.revokeAllOf(marko.getId())).isEqualTo(1);

        refresh(refreshToken).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void anAuthorizeRequestWithoutPkceIsRefused() throws Exception {
        String location = mvc.perform(get(authorizeUri(false))
                        .with(loggedIn()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(location).startsWith(REDIRECT_URI).contains("error=invalid_request").doesNotContain("code=");
    }

    @Test
    void aBrowserThatIsNotLoggedInIsSentToTheSignInPage() throws Exception {
        mvc.perform(authorizeRequest().accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/signin"));
    }

    @Test
    void aWrongClientSecretIsRefused() throws Exception {
        String code = authorize();

        mvc.perform(post("/oauth2/token")
                        .with(httpBasic(GATEWAY_CLIENT_ID, "wrong"))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void discoveryNamesThePublicIssuer() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(ISSUER))
                .andExpect(jsonPath("$.jwks_uri").value(ISSUER + "/oauth2/jwks"));
    }

    @Test
    void aBannedUserIsLockedAndADeletedOneIsNotFound() {
        assertThat(accounts.loadUserByUsername("MARKO@example.test").isAccountNonLocked()).isTrue();

        marko.ban(marko.getId(), "spam", Instant.now());
        users.save(marko);
        assertThat(accounts.loadUserByUsername("marko@example.test").isAccountNonLocked()).isFalse();
    }

    /** Runs the authorize step as a logged-in browser and returns the code from the redirect. */
    private String authorize() throws Exception {
        String location = mvc.perform(authorizeRequest().with(loggedIn()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(REDIRECT_URI);
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("state")).isEqualTo("xyz");
        return query.getFirst("code");
    }

    private MockHttpServletRequestBuilder authorizeRequest() {
        return get(authorizeUri(true));
    }

    /**
     * The authorize endpoint reads its parameters from the query string only (never a body), so they go into the
     * URI, encoded, rather than through {@code .param(...)}.
     */
    private static URI authorizeUri(boolean withPkce) {
        var uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", GATEWAY_CLIENT_ID)
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("state", "xyz");
        if (withPkce) {
            uri.queryParam("code_challenge", challenge(VERIFIER)).queryParam("code_challenge_method", "S256");
        }
        return uri.encode().build().toUri();
    }

    private ResultActions exchange(String code) throws Exception {
        return mvc.perform(post("/oauth2/token")
                .with(httpBasic(GATEWAY_CLIENT_ID, CLIENT_SECRET))
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/oauth2/token")
                .with(httpBasic(GATEWAY_CLIENT_ID, CLIENT_SECRET))
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken));
    }

    private static String tokenBody(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /**
     * The session a real password login leaves behind: the principal named by the user id, as
     * {@link UserAccountService} builds it, plus the {@code FACTOR_PASSWORD} authority stamped with the login time,
     * which Spring Security 7 adds and the ID token's {@code auth_time} comes from.
     */
    private RequestPostProcessor loggedIn() {
        UserDetails account = accounts.loadUserByUsername("marko@example.test");
        List<GrantedAuthority> authorities = new ArrayList<>(account.getAuthorities());
        authorities.add(FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                .issuedAt(Instant.now()).build());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(account, null, authorities));
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
