package com.levi9.reddit.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * The gateway as a BFF: the browser holds only this session cookie, and the gateway attaches the user's access token
 * to API calls ({@code TokenRelay} in application.yml).
 * <p>
 * <b>Who may call what is still decided by each service.</b> Every route is open here: a request without a session
 * simply goes on without a token, and the service answers 401 if it needs one.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository registrations,
                                            OAuthProperties oauth, JsonMapper json) throws Exception {
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        http
                // The session cookie goes along on every request, so every state-changing call needs the token from
                // the XSRF-TOKEN cookie in the X-XSRF-TOKEN header. Login and register are left to auth-service,
                // which checks its own token: one request should not need two.
                .csrf(csrf -> csrf.spa()
                        .ignoringRequestMatchers(
                                path.matcher(HttpMethod.POST, "/api/auth/login"),
                                path.matcher(HttpMethod.POST, "/api/auth/register")))
                // spa() only writes the XSRF-TOKEN cookie once something reads the token; this reads it on every request.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // A failed code exchange (auth-service refused: the account was deleted or banned, or the password
                // changed since that login) lands on the React login page, which says "please log in again".
                .oauth2Login(login -> login
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/signin?error=session-ended"))
                .logout(logout -> logout.logoutSuccessHandler(new LogoutResponses(registrations, oauth, json)))
                // An API for a single-page app: "not logged in" is a 401 the page handles, never a redirect.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/me").authenticated()
                        .anyRequest().permitAll());
        return http.build();
    }

    /** Loads the CSRF token on every request, so the cookie is (re)written and the page can always read it. */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
