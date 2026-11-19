package com.levi9.reddit.auth;

import com.jayway.jsonpath.JsonPath;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/users/**} as a resource server, with real signed tokens: only a token auth-service issued, unexpired,
 * for the public issuer, gets in. A login session cookie, which the gateway also forwards, never counts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserApiResourceServerTests {
    private static final String ISSUER = "http://localhost:3000";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder ourKey;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private User marko;

    @BeforeEach
    void freshUsers() {
        jdbc.execute("TRUNCATE oauth2_authorization, users RESTART IDENTITY CASCADE");
        users.save(new User("first", "first@example.test", "hash", "First", "User")); // so marko is not user 1
        marko = users.save(new User("marko", "marko@example.test", passwords.encode(PASSWORD), "Marko", "Markovic"));
    }

    @Test
    void aValidTokenGetsTheProfileOfItsSubject() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token(ourKey, ISSUER, Instant.now()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(marko.getId()))
                .andExpect(jsonPath("$.username").value("marko"));
    }

    @Test
    void noTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedByAnyOtherKeyIs401() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("not-ours").generate();
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token(forger, ISSUER, Instant.now()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIs401() throws Exception {
        String expired = token(ourKey, ISSUER, Instant.now().minus(Duration.ofHours(1)));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnotherIssuerIs401() throws Exception {
        mvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(ourKey, "levddit-auth", Instant.now()))))
                .andExpect(status().isUnauthorized());
    }

    /** The gateway forwards the browser's cookies too. A logged-in auth-service session must not open the API. */
    @Test
    void aLoginSessionWithoutATokenIs401() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        String csrf = mvc.perform(get("/api/auth/csrf").session(browser)).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/auth/login").session(browser)
                        .header(JsonPath.read(csrf, "$.headerName"), (String) JsonPath.read(csrf, "$.token"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "marko@example.test")
                        .param("password", PASSWORD))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/me").session(browser)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingTheAccountDeletesTheTokensSubjectNotUserOne() throws Exception {
        mvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(ourKey, ISSUER, Instant.now())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(users.findById(marko.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(users.findByUsernameIgnoringCase("first").orElseThrow().isDeleted()).isFalse();
    }

    /** A token as auth-service issues one: sub = user id, username, roles. {@code issuedAt} sets the 15-minute window. */
    private String token(JwtEncoder key, String issuer, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(marko.getId()))
                .claim("username", "marko")
                .claim("roles", new ArrayList<>(List.of("USER")))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .build();
        return key.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
