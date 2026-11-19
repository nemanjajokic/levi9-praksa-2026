package com.levi9.reddit.auth.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyConfigTest {
    private static KeyPair keyPair;
    private static String pem;

    private final JwtKeyConfig config = new JwtKeyConfig();

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        // Same format as `openssl genpkey`: PKCS#8, base64 in 64-character lines. Generated here, never committed.
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    void loadsConfiguredKeyAndDerivesTheMatchingPublicKey() throws Exception {
        RSAKey key = config.jwtSigningKey(properties("2026-11", pem, false));

        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getKeyID()).isEqualTo("2026-11");
        assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(key.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
        assertThat(key.toRSAPublicKey().getModulus())
                .isEqualTo(((RSAPublicKey) keyPair.getPublic()).getModulus());
    }

    @Test
    void acceptsThePemOnASingleLine() {
        RSAKey singleLine = config.jwtSigningKey(properties("2026-11", pem.replace("\n", ""), false));
        RSAKey multiLine = config.jwtSigningKey(properties("2026-11", pem, false));

        assertThat(singleLine.getModulus()).isEqualTo(multiLine.getModulus());
    }

    @Test
    void configuredKeyWinsOverTheGenerateFlag() {
        RSAKey key = config.jwtSigningKey(properties("2026-11", pem, true));

        assertThat(key.getKeyID()).isEqualTo("2026-11");
    }

    @Test
    void generatesATemporaryKeyWithARandomKidWhenAllowed() {
        RSAKey first = config.jwtSigningKey(properties(null, null, true));
        RSAKey second = config.jwtSigningKey(properties(null, null, true));

        assertThat(first.isPrivate()).isTrue();
        assertThat(first.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(first.getKeyID()).isNotBlank().isNotEqualTo(second.getKeyID());
    }

    @Test
    void refusesToStartWithoutAKeyOrTheGenerateFlag() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties(null, null, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.private-key is not set");
    }

    @Test
    void rejectsAPrivateKeyWithoutAKeyId() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties(" ", pem, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.key-id");
    }

    @Test
    void rejectsSomethingThatIsNotAPrivateKey() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties("2026-11", "not a key", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid PKCS#8 RSA private key");
    }

    @Test
    void encoderSignsTokensThatThePublicKeyVerifies() throws Exception {
        RSAKey key = config.jwtSigningKey(properties("2026-11", pem, false));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("levddit-auth")
                .subject("42")
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        Jwt issued = config.jwtEncoder(config.jwkSource(key)).encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));
        Jwt verified = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(issued.getTokenValue());

        assertThat(issued.getHeaders()).containsEntry("kid", "2026-11");
        assertThat(verified.getSubject()).isEqualTo("42");
    }

    private static JwtProperties properties(String keyId, String privateKey, boolean generateKey) {
        return new JwtProperties("levddit-auth", Duration.ofMinutes(15), Duration.ofDays(7),
                keyId, privateKey, generateKey);
    }
}
