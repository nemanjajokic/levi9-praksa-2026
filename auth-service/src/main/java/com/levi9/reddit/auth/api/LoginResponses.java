package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.contracts.LoginResponse;
import com.levi9.reddit.auth.core.exception.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

/**
 * JSON answers to the React login page's {@code POST /api/auth/login}, instead of form login's redirects: the page
 * decides where to go.
 * <ul>
 *   <li>Success: {@code 200 {"redirectUrl": ...}}. The URL is the authorize request that sent the browser to the
 *   login page (saved in the session), or, when the user opened the login page directly, the gateway's
 *   "start login" URL, which leads to the same place.</li>
 *   <li>Failure: one {@code 401} for a wrong password, an unknown email, a deleted and a banned account alike, so a
 *   login never reveals whether an email is registered.</li>
 * </ul>
 */
class LoginResponses implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    /** The gateway's {@code oauth2Login} entry point for the {@code levddit} registration. */
    static final String GATEWAY_LOGIN_START = "/oauth2/authorization/levddit";

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final JsonMapper json;

    LoginResponses(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SavedRequest saved = requestCache.getRequest(request, response);
        String redirectUrl = saved != null ? pathAndQuery(saved.getRedirectUrl()) : GATEWAY_LOGIN_START;
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), new LoginResponse(redirectUrl));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        ProblemResponses.write(response, json, HttpStatus.UNAUTHORIZED, ErrorType.INVALID_CREDENTIALS,
                "Invalid email or password");
    }

    /** Relative, so the browser stays on the origin it is on, whatever host name this service saw. */
    private static String pathAndQuery(String url) {
        URI uri = URI.create(url);
        return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
    }
}
