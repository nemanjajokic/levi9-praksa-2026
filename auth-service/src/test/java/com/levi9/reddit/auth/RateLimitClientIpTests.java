package com.levi9.reddit.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which client a rate limit counts, on a real server, because only the real Tomcat resolves X-Forwarded-For.
 * The rule: the client is the address the gateway added (the rightmost public entry); anything a client writes
 * in front of it is ignored, so faking that header doesn't buy a fresh limit. Requests come from 127.0.0.1,
 * a private address like the gateway's. Login is limited to 2 per hour here; without a CSRF token it simply gets 403.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rate-limit.login.requests=2",
        "app.rate-limit.login.per=1h"})
class RateLimitClientIpTests {
    /** No CSRF token on purpose: answered 403 without touching the database, and still counted. */
    private static final String BODY = "email=nobody%40example.test&password=whatever";

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Test
    void theClientIsTheAddressTheGatewayForwarded() throws Exception {
        assertThat(loginForwardedFor("203.0.113.10")).isEqualTo(403);
        assertThat(loginForwardedFor("203.0.113.10")).isEqualTo(403);

        assertThat(loginForwardedFor("203.0.113.10")).isEqualTo(429);
        assertThat(loginForwardedFor("203.0.113.11")).isEqualTo(403);
    }

    @Test
    void addressesAClientWritesInFrontOfTheForwardedOneAreIgnored() throws Exception {
        assertThat(loginForwardedFor("198.51.100.1, 203.0.113.20")).isEqualTo(403);
        assertThat(loginForwardedFor("198.51.100.2, 203.0.113.20")).isEqualTo(403);

        assertThat(loginForwardedFor("198.51.100.3, 203.0.113.20")).isEqualTo(429);
    }

    private int loginForwardedFor(String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(BODY))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
