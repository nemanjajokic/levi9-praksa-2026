package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.MutableClock;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.core.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The register rate limit, per client IP (workspace DECISIONS.md, 2026-11-18). A small limit here so the rules are
 * visible: 3 per minute, one back every 20 s. The context is shared between tests, so each test uses its own client
 * IP and starts with a full budget. The login limit is a servlet filter and is tested in {@code LoginTests}.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RateLimitTest.TestClock.class})
@TestPropertySource(properties = {
        "app.rate-limit.register.requests=3",
        "app.rate-limit.register.per=1m"})
class RateLimitTest {
    private static final String REGISTER_BODY = """
            {"username":"marko","email":"marko@example.test","password":"correct horse battery",
             "firstName":"Marko","lastName":"Markovic"}""";

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private AuthService auth;

    @BeforeEach
    void serviceSucceeds() {
        when(auth.register(any())).thenReturn(new User("marko", "marko@example.test", "hash", "Marko", "Markovic"));
    }

    @Test
    void allowsThreeAttemptsPerClientThenAnswers429WithRetryAfter() throws Exception {
        String client = "203.0.113.1";

        for (int i = 0; i < 3; i++) {
            register(client).andExpect(status().isCreated());
        }

        register(client)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errorType").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", "20"));
    }

    @Test
    void aBlockedAttemptNeverReachesTheService() throws Exception {
        String client = "203.0.113.2";

        for (int i = 0; i < 4; i++) {
            register(client);
        }

        verify(auth, times(3)).register(any());
    }

    @Test
    void invalidRequestsCountTowardsTheLimit() throws Exception {
        String client = "203.0.113.3";

        for (int i = 0; i < 3; i++) {
            postJson("{}", client).andExpect(status().isBadRequest());
        }

        register(client).andExpect(status().isTooManyRequests());
    }

    @Test
    void eachClientIpHasItsOwnLimit() throws Exception {
        String busy = "203.0.113.4";

        for (int i = 0; i < 3; i++) {
            register(busy);
        }

        register(busy).andExpect(status().isTooManyRequests());
        register("203.0.113.5").andExpect(status().isCreated());
    }

    @Test
    void oneAttemptComesBackEveryTwentySeconds() throws Exception {
        String client = "203.0.113.6";
        for (int i = 0; i < 3; i++) {
            register(client);
        }

        clock.advance(Duration.ofSeconds(19));
        register(client).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(1));
        register(client).andExpect(status().isCreated());
        register(client).andExpect(status().isTooManyRequests());
    }

    private ResultActions register(String clientIp) throws Exception {
        return postJson(REGISTER_BODY, clientIp);
    }

    private ResultActions postJson(String body, String clientIp) throws Exception {
        return mvc.perform(post("/api/auth/register").with(from(clientIp)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor from(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }
}
