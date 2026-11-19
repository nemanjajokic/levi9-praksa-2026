package com.levi9.reddit.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a refused token refresh into "logged out". {@code TokenRelay} refreshes the access token before a call; when
 * auth-service refuses (the user changed their password, deleted the account or was banned), the error would
 * otherwise surface as a 500. Instead the session ends and the answer is 401, so the page sends the user to log in.
 */
@Component
class RefreshFailedFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException e) {
            if (!causedByRefusedAuthorization(e) || response.isCommitted()) {
                throw e;
            }
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.resetBuffer();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Your session has ended, please log in again"}""");
        }
    }

    private static boolean causedByRefusedAuthorization(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ClientAuthorizationException) {
                return true;
            }
        }
        return false;
    }
}
