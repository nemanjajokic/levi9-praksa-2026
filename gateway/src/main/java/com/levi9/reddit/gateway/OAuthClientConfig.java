package com.levi9.reddit.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

import java.util.Map;

/**
 * The gateway as auth-service's OAuth client: the browser logs in through it, and it keeps the user's tokens.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuthProperties.class)
class OAuthClientConfig {
    static final String REGISTRATION_ID = "levddit";

    /**
     * Written out instead of discovered from the issuer: the browser and the gateway reach auth-service at different
     * addresses. Endpoints the browser visits use the public URL; the ones only the gateway calls use the internal
     * one. The issuer is still checked on every ID token.
     */
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OAuthProperties oauth) {
        String publicUrl = oauth.publicUrl();
        String internal = oauth.authServiceUrl();
        ClientRegistration levddit = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId("levddit-gateway")
                .clientSecret(oauth.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                // Exactly as registered on auth-service, which compares it character by character.
                .redirectUri(publicUrl + "/login/oauth2/code/" + REGISTRATION_ID)
                .scope(OidcScopes.OPENID, OidcScopes.PROFILE)
                .issuerUri(publicUrl)
                .authorizationUri(publicUrl + "/oauth2/authorize")          // the browser goes here
                .tokenUri(internal + "/oauth2/token")                       // only the gateway calls these two
                .jwkSetUri(internal + "/oauth2/jwks")
                .userNameAttributeName(IdTokenClaimNames.SUB)               // the user id
                .providerConfigurationMetadata(Map.of("end_session_endpoint", publicUrl + "/connect/logout"))
                .build();
        return new InMemoryClientRegistrationRepository(levddit);
    }

    /**
     * The user's tokens live in their session, which is in Postgres. Spring's default for login keeps them in the
     * gateway's memory instead, so a restart would lose every user's tokens.
     */
    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    /**
     * Hands {@code TokenRelay} a valid access token, refreshing it first when it is about to expire. When the refresh
     * is refused (the user changed their password, deleted the account or was banned), the tokens are dropped and
     * {@link RefreshFailedFilter} ends the session.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                          OAuth2AuthorizedClientRepository authorizedClients) {
        return new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClients);
    }
}
