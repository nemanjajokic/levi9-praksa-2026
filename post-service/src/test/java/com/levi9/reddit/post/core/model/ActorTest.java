package com.levi9.reddit.post.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ActorTest {

    @Test
    void isAuthorOf_comparesIdWithPostAuthor() {
        Post post = new Post("Title", 5L, "marko", 1L);

        assertThat(new Actor(5, "marko", false).isAuthorOf(post)).isTrue();
        assertThat(new Actor(6, "ana", false).isAuthorOf(post)).isFalse();
        assertThat(new Actor(6, "admin", true).isAuthorOf(post)).as("admin is not the author").isFalse();
    }

    @Test
    void rejectsNonPositiveId() {
        assertThatThrownBy(() -> new Actor(0, "marko", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(-1, "marko", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingUsername() {
        assertThatThrownBy(() -> new Actor(1, null, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Actor(1, " ", false)).isInstanceOf(IllegalArgumentException.class);
    }
}
