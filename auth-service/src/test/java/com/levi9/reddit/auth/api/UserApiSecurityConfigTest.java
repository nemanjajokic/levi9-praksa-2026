package com.levi9.reddit.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiSecurityConfigTest {

    @Test
    void theRolesClaimBecomesSpringRolesAndTheNameIsTheSubject() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("42")
                .claim("roles", List.of("ADMIN"))
                .claim("scope", "openid profile")
                .build();

        var authentication = UserApiSecurityConfig.rolesAsAuthorities().convert(jwt);

        assertThat(authentication.getName()).isEqualTo("42");
        // Spring Security 7 also adds FACTOR_BEARER (how the user proved who they are); scopes are not roles here.
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN")
                .noneMatch(authority -> authority.startsWith("SCOPE_"));
    }
}
