package com.levi9.reddit.post.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Reading posts and comments is public. Everything else needs a caller.
 *
 * <p>Until auth-service issues access tokens, {@code app.mock-auth.enabled=true} (local only) registers
 * {@link MockAuthenticationFilter}. When JWT validation is added, remove the filter and the flag and use
 * {@code .oauth2ResourceServer(o -> o.jwt(...))}.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.mock-auth.enabled:false}") boolean mockAuthEnabled) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // no cookies: the token travels in a header
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .anyRequest().authenticated())
                // Problem details for errors raised before any controller runs. Without the entry point Spring would
                // also answer 403 instead of 401 to a missing caller, since no login mechanism is configured yet.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.forbidden()));

        if (mockAuthEnabled) {
            log.warn("MOCK AUTH ENABLED: every request without a token gets a made-up caller (X-Mock-* headers). "
                    + "Never enable app.mock-auth in production.");
            http.addFilterBefore(new MockAuthenticationFilter(), AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }
}
