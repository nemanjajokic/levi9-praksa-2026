package com.levi9.reddit.auth.infrastructure.security;

import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.UUID;

/**
 * auth-service as an OAuth 2.1 / OpenID Connect authorization server. Its one client is the gateway (the BFF):
 * the browser never sees a token. Endpoints: {@code /oauth2/authorize}, {@code /oauth2/token}, {@code /oauth2/jwks},
 * {@code /connect/logout}, {@code /userinfo} and {@code /.well-known/openid-configuration}.
 * <p>
 * Everything is stored in Postgres ({@code V2__oauth2_authorization_server.sql}), so a restart logs nobody out.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayClientProperties.class)
public class AuthorizationServerConfig {
    public static final String GATEWAY_CLIENT_ID = "levddit-gateway";
    /** Spring Security's {@code oauth2Login} callback on the gateway; {@code levddit} is its registration id. */
    static final String GATEWAY_CALLBACK_PATH = "/login/oauth2/code/levddit";
    /** The React login page. Same origin as everything else, so a relative path is enough. */
    static final String SIGN_IN_PAGE = "/signin";

    /**
     * Only the authorization server's endpoints. An authorize request from a browser that is not logged in
     * redirects to the login page; the request is saved in the session, and the login answers with its URL.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(SIGN_IN_PAGE),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    /**
     * The gateway, registered at every startup: inserted the first time, updated afterwards, so the secret and
     * settings in config always win over what the database holds.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc, JwtProperties jwt,
                                                          GatewayClientProperties gateway, PasswordEncoder passwords) {
        var clients = new JdbcRegisteredClientRepository(jdbc);
        RegisteredClient existing = clients.findByClientId(GATEWAY_CLIENT_ID);
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();
        clients.save(gatewayClient(id, jwt, passwords.encode(gateway.secret())));
        return clients;
    }

    /**
     * The gateway is a server, so it can keep a secret (a confidential client): that is what gets it refresh tokens.
     * PKCE on top, as OAuth 2.1 recommends for every client. No consent screen: the only client is levddit itself.
     * The public URL is the issuer and the gateway's origin at once, because everything is served from one origin.
     */
    static RegisteredClient gatewayClient(String id, JwtProperties jwt, String encodedSecret) {
        String publicUrl = jwt.issuer();
        return RegisteredClient.withId(id)
                .clientId(GATEWAY_CLIENT_ID)
                .clientName("levddit gateway")
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(publicUrl + GATEWAY_CALLBACK_PATH)
                .postLogoutRedirectUri(publicUrl + "/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(jwt.accessTokenTtl())
                        .refreshTokenTimeToLive(jwt.refreshTokenTtl())
                        // Rotation: every refresh returns a new refresh token and the old one stops working.
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    /** The issuer is the public origin the browser sees, so it matches {@code iss} in every token. */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(JwtProperties jwt) {
        return AuthorizationServerSettings.builder().issuer(jwt.issuer()).build();
    }

    /**
     * Checks access tokens sent to this service's own API ({@code /api/users/**}) and to {@code /userinfo}: signed by
     * our key, not expired, and issued by us ({@code iss} is the public URL). Verified locally, no network call.
     */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, JwtProperties jwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.issuer()));
        return decoder;
    }

    /** Refuses tokens a user may no longer have, and adds {@code username} and {@code roles}: see {@link TokenClaims}. */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenClaims(UserRepository users) {
        return new TokenClaims(users);
    }
}
