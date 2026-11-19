package com.levi9.reddit.gateway;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the gateway reaches auth-service as an OAuth client, bound from {@code app.oauth.*}, validated at startup.
 *
 * @param publicUrl      the origin the browser sees; auth-service's issuer and the base of every redirect
 * @param authServiceUrl auth-service inside the network, for the calls the browser never makes (token, keys)
 * @param clientSecret   the gateway's client secret ({@code APP_OAUTH_GATEWAY_CLIENT_SECRET}), never committed
 */
@Validated
@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(@NotBlank String publicUrl, @NotBlank String authServiceUrl, @NotBlank String clientSecret) {
}
