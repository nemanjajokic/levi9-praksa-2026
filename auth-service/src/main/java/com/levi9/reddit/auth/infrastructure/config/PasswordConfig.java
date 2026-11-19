package com.levi9.reddit.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

@Configuration
public class PasswordConfig {
    /** BCrypt only uses 72 bytes; register never accepts more, so a longer password can never be right. */
    static final int MAX_PASSWORD_BYTES = 72;

    /**
     * BCrypt, except that a password over 72 bytes never matches. BCrypt's check silently cuts input at 72 bytes,
     * so a longer password whose first 72 bytes are right would otherwise pass. The hash still runs, so the answer
     * takes as long as any other wrong password.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder() {
            @Override
            protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
                if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
                    super.matchesNonNull("", encodedPassword);
                    return false;
                }
                return super.matchesNonNull(rawPassword, encodedPassword);
            }
        };
    }
}
