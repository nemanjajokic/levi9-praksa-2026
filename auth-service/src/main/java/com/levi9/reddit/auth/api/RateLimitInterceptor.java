package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.core.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limits how often one client IP may call an MVC endpoint (register), so nobody can check many emails quickly.
 * Runs before the request body is read and validated: a blocked request costs no BCrypt, and invalid requests count.
 */
class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimiter limiter;

    RateLimitInterceptor(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        limiter.tryAcquire(request.getRemoteAddr()).ifPresent(wait -> {
            // Handled like any domain error by GlobalExceptionHandler: 429 problem detail plus Retry-After.
            throw new TooManyRequestsException(wait);
        });
        return true;
    }
}
