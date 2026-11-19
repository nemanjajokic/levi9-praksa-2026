package com.levi9.reddit.auth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

/** How a login checks an email and password (used by form login in {@code api.SecurityConfig}). */
@Configuration(proxyBeanMethods = false)
public class AuthenticationConfig {

    /**
     * Spring's default checks the lock before the password, so a banned account would fail without BCrypt, measurably
     * faster than a wrong password: a timing hint that the email exists. Here the password is checked first and the
     * lock after. Unknown emails are already checked against a dummy hash by Spring, so every failure costs one BCrypt.
     */
    @Bean
    DaoAuthenticationProvider authenticationProvider(UserAccountService accounts, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwords);
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isAccountNonLocked()) {
                throw new LockedException("Account is locked");
            }
        });
        return provider;
    }
}
