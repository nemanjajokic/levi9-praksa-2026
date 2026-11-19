package com.levi9.reddit.auth.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The gateway's OAuth client secret, bound from {@code app.oauth.gateway-client.*} and validated at startup.
 * It comes from the environment ({@code APP_OAUTH_GATEWAY_CLIENT_SECRET}) and is never committed. The gateway
 * sends the same value when it exchanges a code or a refresh token.
 */
@Validated
@ConfigurationProperties(prefix = "app.oauth.gateway-client")
public record GatewayClientProperties(@NotBlank String secret) {
}
