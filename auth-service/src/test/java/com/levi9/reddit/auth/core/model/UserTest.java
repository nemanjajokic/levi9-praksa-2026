package com.levi9.reddit.auth.core.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "m", "me", "😀😀"})
    void rejectsUsernamesShorterThanThreeCharacters(String username) {
        assertThatThrownBy(() -> new User(username, "user@example.test", "hash", "First", "Last"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must be at least 3 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mem", "marko"})
    void acceptsUsernamesOfAtLeastThreeCharacters(String username) {
        User user = new User(username, "user@example.test", "hash", "First", "Last");

        assertThat(user.getUsername()).isEqualTo(username);
    }
}
