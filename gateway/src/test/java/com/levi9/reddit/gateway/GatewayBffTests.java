package com.levi9.reddit.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The gateway as a BFF against a stand-in for auth-service and the services behind it (one local HTTP server that
 * records what reaches it), with real Postgres for the sessions.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GatewayBffTests {
    private static final String PUBLIC_URL = "http://localhost:3000";
    private static final Backend BACKEND = Backend.start();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClientRegistrationRepository registrations;

    @Autowired
    private OAuth2AuthorizedClientRepository authorizedClients;

    /** Raw on purpose: the JDBC session class is package-private, so the interface is the only way in. */
    @SuppressWarnings("rawtypes")
    @Autowired
    private SessionRepository sessions;

    @DynamicPropertySource
    static void routeToTheBackend(DynamicPropertyRegistry registry) {
        for (String service : List.of("AUTH_SERVICE_URL", "COMMUNITY_SERVICE_URL", "POST_SERVICE_URL")) {
            registry.add(service, BACKEND::url);
        }
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.server.stop(0);
    }

    @BeforeEach
    void forgetRequests() {
        BACKEND.authorizationByPath.clear();
    }

    // --- Logging in ---

    @Test
    void startingALoginSendsTheBrowserToAuthServicesPublicAuthorizeUrlWithPkce() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/levddit"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(location)
                .startsWith(PUBLIC_URL + "/oauth2/authorize?")
                .contains("client_id=levddit-gateway")
                .contains("scope=openid%20profile")
                .contains("redirect_uri=http://localhost:3000/login/oauth2/code/levddit")
                .contains("code_challenge_method=S256");
    }

    @Test
    void aFailedLoginLandsOnTheReactLoginPage() throws Exception {
        // A callback the gateway can't complete (here: no login was started). A refused code exchange ends the same way.
        mvc.perform(get("/login/oauth2/code/levddit").param("code", "c").param("state", "s"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/signin?error=session-ended"));
    }

    @Test
    void whoIsLoggedInComesFromTheSession() throws Exception {
        mvc.perform(get("/api/me").with(loggedInAsMarko()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("marko"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void withoutASessionMeIs401NotARedirect() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    // --- Calls to the services ---

    @Test
    void aLoggedInCallCarriesTheUsersAccessToken() throws Exception {
        mvc.perform(get("/api/posts").with(loggedInAsMarko())).andExpect(status().isOk());

        assertThat(BACKEND.authorizationOf("/api/posts")).contains("Bearer access-token");
    }

    @Test
    void anAnonymousCallGoesThroughWithoutAToken() throws Exception {
        mvc.perform(get("/api/posts")).andExpect(status().isOk());

        assertThat(BACKEND.authorizationByPath).containsKey("/api/posts");
        assertThat(BACKEND.authorizationOf("/api/posts")).isEmpty();
    }

    @Test
    void anExpiredAccessTokenIsRefreshedBeforeTheCall() throws Exception {
        Session session = storedSessionWithTokens("good-refresh");

        mvc.perform(get("/api/posts").cookie(sessionCookie(session))).andExpect(status().isOk());

        assertThat(BACKEND.authorizationOf("/api/posts")).contains("Bearer fresh-access-token");
    }

    @Test
    void aRefusedRefreshEndsTheSessionWith401() throws Exception {
        Session session = storedSessionWithTokens("revoked-refresh");

        mvc.perform(get("/api/posts").cookie(sessionCookie(session)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your session has ended, please log in again"));

        assertThat(sessions.findById(session.getId())).isNull();
        assertThat(BACKEND.authorizationByPath).doesNotContainKey("/api/posts");
    }

    // --- CSRF ---

    @Test
    void theCsrfCookieIsReadableByThePage() throws Exception {
        mvc.perform(get("/api/posts"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void aStateChangingCallNeedsTheCsrfToken() throws Exception {
        mvc.perform(post("/api/posts").with(loggedInAsMarko())).andExpect(status().isForbidden());
        mvc.perform(post("/api/posts").with(loggedInAsMarko()).with(csrfLikeTheBrowser())).andExpect(status().isOk());
    }

    @Test
    void loginAndRegisterAreLeftToAuthServicesOwnCsrfCheck() throws Exception {
        mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register")).andExpect(status().isOk());
    }

    // --- Logging out ---

    @Test
    void logoutEndsTheSessionAndPointsAtAuthServicesLogout() throws Exception {
        mvc.perform(post("/logout").with(loggedInAsMarko()).with(csrfLikeTheBrowser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith(PUBLIC_URL + "/connect/logout?id_token_hint="),
                        org.hamcrest.Matchers.containsString("post_logout_redirect_uri=http://localhost:3000/"))));
    }

    @Test
    void theAuthorizeAndLogoutPagesAreRoutedToAuthService() throws Exception {
        mvc.perform(get("/oauth2/authorize")).andExpect(status().isOk());
        mvc.perform(get("/connect/logout")).andExpect(status().isOk());

        assertThat(BACKEND.authorizationByPath).containsKeys("/oauth2/authorize", "/connect/logout");
    }

    @Test
    void theTokenEndpointIsNotReachableThroughTheGateway() throws Exception {
        mvc.perform(post("/oauth2/token").with(csrfLikeTheBrowser())).andExpect(status().isNotFound());
    }

    private OidcLoginRequestPostProcessor loggedInAsMarko() {
        return oidcLogin()
                .clientRegistration(levddit())
                .idToken(token -> token.subject("42").claim("username", "marko").claim("roles", List.of("USER")));
    }

    private OAuth2AuthenticationToken markoToken() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject("42")
                .claim("username", "marko").claim("roles", List.of("USER"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        var user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), OAuthClientConfig.REGISTRATION_ID);
    }

    /**
     * A logged-in session as a returning browser has it: stored in Postgres, holding marko's login and an access
     * token that expired a minute ago, with the given refresh token. The gateway's own repository writes the tokens,
     * so the attribute names are the real ones.
     */
    private Session storedSessionWithTokens(String refreshToken) {
        Instant past = Instant.now().minusSeconds(960);
        var client = new OAuth2AuthorizedClient(levddit(), "42",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "old-access-token", past, past.plusSeconds(900)),
                new OAuth2RefreshToken(refreshToken, past));
        var scratch = new MockHttpServletRequest();
        authorizedClients.saveAuthorizedClient(client, markoToken(), scratch, new MockHttpServletResponse());

        Session session = sessions.createSession();
        HttpSession tokens = scratch.getSession();
        for (String name : Collections.list(tokens.getAttributeNames())) {
            session.setAttribute(name, tokens.getAttribute(name));
        }
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(markoToken()));
        save(session);
        return session;
    }

    @SuppressWarnings("unchecked")
    private void save(Session session) {
        sessions.save(session);
    }

    /** The cookie the browser would send: Spring Session's {@code SESSION}, the id Base64-encoded. */
    private static Cookie sessionCookie(Session session) {
        return new Cookie("SESSION",
                Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
    }

    /** As the page does it: read the XSRF-TOKEN cookie, send it back in the X-XSRF-TOKEN header. */
    private RequestPostProcessor csrfLikeTheBrowser() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        return request -> {
            List<Cookie> cookies = new ArrayList<>(request.getCookies() == null ? List.of() : List.of(request.getCookies()));
            cookies.add(xsrf);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", xsrf.getValue());
            return request;
        };
    }

    private ClientRegistration levddit() {
        return registrations.findByRegistrationId(OAuthClientConfig.REGISTRATION_ID);
    }

    /**
     * Stands in for auth-service and the services: records each path's Authorization header and answers 200,
     * except the token endpoint, which refreshes {@code good-refresh} and refuses anything else.
     */
    private record Backend(HttpServer server, Map<String, String> authorizationByPath) {
        static Backend start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                Backend backend = new Backend(server, new ConcurrentHashMap<>());
                server.createContext("/", backend::handle);
                server.start();
                return backend;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        Optional<String> authorizationOf(String path) {
            return Optional.ofNullable(authorizationByPath.get(path)).filter(value -> !value.isEmpty());
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/oauth2/token")) {
                String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (form.contains("refresh_token=good-refresh")) {
                    respond(exchange, 200, """
                            {"access_token":"fresh-access-token","token_type":"Bearer","expires_in":900,
                             "refresh_token":"rotated-refresh"}""");
                } else {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                }
                return;
            }
            String authorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            authorizationByPath.put(path, authorization == null ? "" : authorization);
            respond(exchange, 200, "{}");
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
