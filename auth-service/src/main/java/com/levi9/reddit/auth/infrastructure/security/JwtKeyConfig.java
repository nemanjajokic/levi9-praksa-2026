package com.levi9.reddit.auth.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * The RS256 key pair auth-service signs access tokens with. Only the public half ever leaves this
 * service, through the authorization server's JWKS endpoint ({@code /oauth2/jwks}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {
    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey jwtSigningKey(JwtProperties properties) {
        if (StringUtils.hasText(properties.privateKey())) {
            if (!StringUtils.hasText(properties.keyId())) {
                throw new IllegalStateException("app.jwt.key-id must be set together with app.jwt.private-key");
            }
            return fromPem(properties.privateKey(), properties.keyId());
        }
        if (properties.generateKey()) {
            log.warn("No JWT private key configured, generating a temporary one. "
                    + "Local development only: issued tokens stop verifying when this process restarts.");
            return generate();
        }
        throw new IllegalStateException(
                "app.jwt.private-key is not set. Provide it, or set app.jwt.generate-key=true for local development.");
    }

    /** Signs tokens, and is published (public half only) at {@code /oauth2/jwks} by the authorization server. */
    @Bean
    JWKSource<SecurityContext> jwkSource(RSAKey jwtSigningKey) {
        return new ImmutableJWKSet<>(new JWKSet(jwtSigningKey));
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /** Accepts a PKCS#8 PEM ("BEGIN PRIVATE KEY") with or without line breaks, so it fits in one env var. */
    static RSAKey fromPem(String pem, String keyId) {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            var publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("app.jwt.private-key is not a valid PKCS#8 RSA private key", e);
        }
    }

    /** A random kid, so services that cached the previous key fetch the JWKS again after a restart. */
    static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate a JWT signing key", e);
        }
    }
}
