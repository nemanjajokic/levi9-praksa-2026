package com.levi9.reddit.auth.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Everything that is neither an authorization server endpoint ({@code AuthorizationServerConfig}, order 1) nor the
 * user API ({@link UserApiSecurityConfig}, order 2): login, register, the CSRF token, health and the API docs. Login
 * is Spring Security's form login with JSON answers; the session it creates is what the authorize endpoint then sees,
 * so the browser is logged in to auth-service itself (single sign-on).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    static final String LOGIN_URL = "/api/auth/login";
    /** The React login page. Form login only needs it so it doesn't generate a login page of its own. */
    private static final String SIGN_IN_PAGE = "/signin";
    private static final RequestMatcher LOGIN = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGIN_URL);
    private static final RequestMatcher REGISTER =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/register");

    @Bean
    @Order(3)
    SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitProperties limits, Clock clock, JsonMapper json)
            throws Exception {
        LoginResponses loginResponses = new LoginResponses(json);
        http
                // Only the two cookie-based POSTs need it: the token comes from GET /api/auth/csrf and is kept in the
                // session. Requests with a bearer token carry no cookie a forged request could ride on.
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(new OrRequestMatcher(LOGIN, REGISTER)))
                .formLogin(form -> form
                        .loginPage(SIGN_IN_PAGE)
                        .loginProcessingUrl(LOGIN_URL)
                        .usernameParameter("email")
                        .successHandler(loginResponses)
                        .failureHandler(loginResponses))
                .addFilterBefore(new LoginRateLimitFilter(LOGIN, new RateLimiter(limits.login(), clock), json),
                        CsrfFilter.class)
                // An API, not pages: a request that needs a login gets 401, never a redirect to the login page.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Tomcat forwards every error to /error to render it. Locked, a 403 or 400 would turn into 401.
                        .requestMatchers("/error").permitAll()
                        // The login and register pages need the token before anyone is signed in.
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        // Signing up cannot require being signed in.
                        .requestMatchers(REGISTER).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
