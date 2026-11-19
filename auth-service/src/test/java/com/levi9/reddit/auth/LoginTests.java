package com.levi9.reddit.auth;

import com.jayway.jsonpath.JsonPath;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.UserService;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static com.levi9.reddit.auth.infrastructure.security.AuthorizationServerConfig.GATEWAY_CLIENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The React login page's side of the flow, against real Postgres: {@code GET /api/auth/csrf}, then
 * {@code POST /api/auth/login} as a form, answered in JSON. The login limit is 3 per minute here; every attempt
 * comes from its own client IP unless a test is about the limit, so tests never use up each other's budget.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.rate-limit.login.requests=3",
        "app.rate-limit.login.per=1m"})
@AutoConfigureMockMvc
class LoginTests {
    private static final String PASSWORD = "correct horse battery";
    /** The longest password register accepts: BCrypt's 72-byte limit. */
    private static final String LONGEST_PASSWORD = "a".repeat(72);
    private static final String REDIRECT_URI = "http://localhost:3000/login/oauth2/code/levddit";
    private static final String VERIFIER = "a-random-pkce-code-verifier-that-is-at-least-43-characters-long";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserService userService;

    private User marko;

    @BeforeEach
    void accounts() {
        jdbc.execute("TRUNCATE oauth2_authorization, users RESTART IDENTITY CASCADE");
        marko = users.save(user("marko", PASSWORD));
        users.save(user("longest", LONGEST_PASSWORD));

        User banned = user("ana", PASSWORD);
        banned.ban(marko.getId(), "spam", Instant.now());
        users.save(banned);

        User deleted = user("petar", PASSWORD);
        deleted.delete();
        users.save(deleted);
    }

    @Test
    void theCsrfEndpointNamesTheHeaderAndGivesAToken() throws Exception {
        mvc.perform(get("/api/auth/csrf").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    /** The whole browser journey: authorize, sent to the login page, log in, back to authorize, a code, tokens. */
    @Test
    void loggingInFromTheAuthorizeRedirectEndsWithACodeForThatUser() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        String ip = nextIp();

        mvc.perform(get(authorizeUri()).session(browser).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/signin"));

        String redirectUrl = JsonPath.read(login(browser, ip, "MARKO@example.test", PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.redirectUrl");
        assertThat(redirectUrl).startsWith("/oauth2/authorize?");

        // As is: the URL is already encoded, and the String overload of get() would encode it a second time.
        String location = mvc.perform(get(URI.create(redirectUrl)).session(browser))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(REDIRECT_URI);
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

        String tokens = mvc.perform(post("/oauth2/token")
                        .with(httpBasic(GATEWAY_CLIENT_ID, "test-gateway-secret"))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Jwt access = jwtDecoder.decode(JsonPath.read(tokens, "$.access_token"));
        assertThat(access.getSubject()).isEqualTo(String.valueOf(marko.getId()));
        assertThat(jwtDecoder.decode(JsonPath.read(tokens, "$.id_token")).getClaimAsInstant("auth_time")).isNotNull();
    }

    @Test
    void loggingInWithoutAnAuthorizeRequestSendsTheBrowserToTheGatewaysLoginStart() throws Exception {
        login(new MockHttpSession(), nextIp(), "marko@example.test", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.redirectUrl").value("/oauth2/authorization/levddit"));
    }

    @Test
    void everyFailureIsTheSame401SoItNeverRevealsWhetherAnEmailIsRegistered() throws Exception {
        String wrongPassword = failedLogin("marko@example.test", "wrong password");

        assertThat(wrongPassword).contains("\"errorType\":\"INVALID_CREDENTIALS\"");
        assertThat(failedLogin("nobody@example.test", PASSWORD)).isEqualTo(wrongPassword);
        assertThat(failedLogin("ana@example.test", PASSWORD)).as("banned").isEqualTo(wrongPassword);
        assertThat(failedLogin("petar@example.test", PASSWORD)).as("deleted").isEqualTo(wrongPassword);
    }

    @Test
    void aPasswordLongerThanBcryptsLimitNeverMatchesEvenWhenItsFirst72BytesDo() throws Exception {
        login(new MockHttpSession(), nextIp(), "longest@example.test", LONGEST_PASSWORD + "b")
                .andExpect(status().isUnauthorized());
        login(new MockHttpSession(), nextIp(), "longest@example.test", LONGEST_PASSWORD)
                .andExpect(status().isOk());
    }

    @Test
    void aLoginWithoutTheCsrfTokenIs403() throws Exception {
        mvc.perform(post("/api/auth/login").with(from(nextIp()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "marko@example.test")
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void theFourthAttemptFromOneClientInAMinuteIs429EvenWithTheRightPassword() throws Exception {
        String ip = nextIp();
        for (int i = 0; i < 3; i++) {
            login(new MockHttpSession(), ip, "marko@example.test", "wrong password").andExpect(status().isUnauthorized());
        }

        login(new MockHttpSession(), ip, "marko@example.test", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorType").value("RATE_LIMITED"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "20"));

        login(new MockHttpSession(), nextIp(), "marko@example.test", PASSWORD).andExpect(status().isOk());
    }

    // --- A login session that outlives what it was for ---
    // Deleting authorizations stops refreshes, but auth-service's own login session survives and can get a new code
    // without a password. These go back through /oauth2/authorize with that old session: a code still comes back,
    // but exchanging it is refused, so no tokens.

    @Test
    void aDeletedAccountsOldLoginSessionGetsNoMoreTokens() throws Exception {
        MockHttpSession browser = loggedInBrowser(PASSWORD);

        marko.delete();
        users.save(marko);

        exchange(authorizeAgain(browser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void aBannedAccountsOldLoginSessionGetsNoMoreTokens() throws Exception {
        MockHttpSession browser = loggedInBrowser(PASSWORD);

        marko.ban(marko.getId(), "spam", Instant.now());
        users.save(marko);

        exchange(authorizeAgain(browser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void afterAPasswordChangeOldLoginsGetNoTokensButANewLoginDoes() throws Exception {
        MockHttpSession before = loggedInBrowser(PASSWORD);

        userService.changePassword(marko.getId(), PASSWORD, "a brand new passphrase");

        exchange(authorizeAgain(before))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        MockHttpSession after = loggedInBrowser("a brand new passphrase");
        exchange(authorizeAgain(after)).andExpect(status().isOk());
    }

    /** A browser that has logged in to auth-service through the authorize redirect, as in the journey above. */
    private MockHttpSession loggedInBrowser(String password) throws Exception {
        MockHttpSession browser = new MockHttpSession();
        mvc.perform(get(authorizeUri()).session(browser).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        login(browser, nextIp(), "marko@example.test", password).andExpect(status().isOk());
        return browser;
    }

    /** {@code /oauth2/authorize} with an existing login session: no password asked, a code in the redirect. */
    private String authorizeAgain(MockHttpSession browser) throws Exception {
        String location = mvc.perform(get(authorizeUri()).session(browser))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(REDIRECT_URI);
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    }

    private ResultActions exchange(String code) throws Exception {
        return mvc.perform(post("/oauth2/token")
                .with(httpBasic(GATEWAY_CLIENT_ID, "test-gateway-secret"))
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER));
    }

    /** As the React page does it: fetch the token for this session, then post the form with it in the header. */
    private ResultActions login(MockHttpSession session, String ip, String email, String password) throws Exception {
        String csrf = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mvc.perform(post("/api/auth/login").session(session).with(from(ip))
                .header(JsonPath.read(csrf, "$.headerName"), (String) JsonPath.read(csrf, "$.token"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", password));
    }

    private String failedLogin(String email, String password) throws Exception {
        return login(new MockHttpSession(), nextIp(), email, password)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    private User user(String username, String password) {
        return new User(username, username + "@example.test", passwords.encode(password), "First", "Last");
    }

    private static URI authorizeUri() {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", GATEWAY_CLIENT_ID)
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("state", "xyz")
                .queryParam("code_challenge", challenge())
                .queryParam("code_challenge_method", "S256")
                .encode().build().toUri();
    }

    private static String challenge() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nextIp() {
        return "203.0.113." + NEXT_IP.getAndIncrement();
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
