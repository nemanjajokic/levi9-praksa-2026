package com.levi9.reddit.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

/**
 * {@code POST /logout} answered in JSON, like the login: {@code 200 {"redirectUrl": ...}}. By then the gateway session
 * is gone; the URL is auth-service's OIDC logout, which ends the login session there too and comes back to {@code /}.
 * Without that second step, the next "log in" would find auth-service still logged in and skip the password.
 */
class LogoutResponses extends OidcClientInitiatedLogoutSuccessHandler {
    private final JsonMapper json;

    LogoutResponses(ClientRegistrationRepository registrations, OAuthProperties oauth, JsonMapper json) {
        super(registrations);
        setPostLogoutRedirectUri(oauth.publicUrl() + "/");   // exactly as registered on auth-service
        this.json = json;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        String redirectUrl = determineTargetUrl(request, response, authentication);
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Map.of("redirectUrl", redirectUrl));
    }
}
