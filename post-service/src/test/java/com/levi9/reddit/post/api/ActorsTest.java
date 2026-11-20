package com.levi9.reddit.post.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.levi9.reddit.post.core.model.Actor;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ActorsTest {

    @Test
    void mapsSubUsernameAndUserRole() {
        Actor actor = Actors.from(jwt(b -> b.subject("42").claim("username", "marko").claim("roles", List.of("USER"))));

        assertThat(actor).isEqualTo(new Actor(42, "marko", false));
    }

    @Test
    void adminRole_setsAdmin() {
        Actor actor = Actors.from(jwt(b -> b.subject("1").claim("username", "boss")
                .claim("roles", List.of("USER", "ADMIN"))));

        assertThat(actor.admin()).isTrue();
    }

    @Test
    void missingRoles_isNotAdmin() {
        Actor actor = Actors.from(jwt(b -> b.subject("3").claim("username", "ana")));

        assertThat(actor.admin()).isFalse();
    }

    @Test
    void missingUsername_isRejected() {
        assertThatThrownBy(() -> Actors.from(jwt(b -> b.subject("3").claim("roles", List.of("USER")))))
                .isInstanceOf(NullPointerException.class);
    }

    private static Jwt jwt(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.accept(builder);
        return builder.build();
    }
}
