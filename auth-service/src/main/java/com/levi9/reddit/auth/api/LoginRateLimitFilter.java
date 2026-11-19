package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.core.exception.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * The login rate limit. A filter, not an MVC interceptor like register's: form login is handled inside the security
 * filter chain and never reaches a controller, so an interceptor would never run. Placed before the CSRF check, so
 * every attempt counts, including ones without a valid token, and a blocked one costs no BCrypt.
 * <p>
 * Created in {@link SecurityConfig}, not as a bean: a {@code Filter} bean would also be registered for every request.
 */
class LoginRateLimitFilter extends OncePerRequestFilter {
    private final RequestMatcher login;
    private final RateLimiter limiter;
    private final JsonMapper json;

    LoginRateLimitFilter(RequestMatcher login, RateLimiter limiter, JsonMapper json) {
        this.login = login;
        this.limiter = limiter;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !login.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> wait = limiter.tryAcquire(request.getRemoteAddr());
        if (wait.isPresent()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(GlobalExceptionHandler.wholeSecondsRoundedUp(wait.get())));
            ProblemResponses.write(response, json, HttpStatus.TOO_MANY_REQUESTS, ErrorType.RATE_LIMITED,
                    "Too many attempts, please try again later");
            return;
        }
        chain.doFilter(request, response);
    }
}
