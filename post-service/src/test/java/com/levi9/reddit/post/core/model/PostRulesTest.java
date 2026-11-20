package com.levi9.reddit.post.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.levi9.reddit.post.core.exception.NotPostAuthorException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.core.exception.PostRemovalForbiddenException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private static final Actor AUTHOR = new Actor(5, "marko", false);
    private static final Actor STRANGER = new Actor(6, "ana", false);
    private static final Actor ADMIN = new Actor(7, "boss", true);

    private final Post post = new Post("Title", AUTHOR.id(), AUTHOR.username(), 1L);

    // --- edit ---

    @Test
    void editBy_author_changesTitleContentAndUpdatedAt() {
        post.editBy(AUTHOR, "New title", "New body", NOW);

        assertThat(post.getTitle()).isEqualTo("New title");
        assertThat(post.getContent()).isEqualTo("New body");
        assertThat(post.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void editBy_stranger_isForbiddenAndChangesNothing() {
        assertThatThrownBy(() -> post.editBy(STRANGER, "Hacked", null, NOW))
                .isInstanceOf(NotPostAuthorException.class);

        assertThat(post.getTitle()).isEqualTo("Title");
        assertThat(post.getUpdatedAt()).isNull();
    }

    @Test
    void editBy_admin_isForbidden_onlyTheAuthorEdits() {
        assertThatThrownBy(() -> post.editBy(ADMIN, "Admin title", null, NOW))
                .isInstanceOf(NotPostAuthorException.class);
    }

    @Test
    void editBy_onRemovedPost_isNotFound_evenForTheAuthor() {
        post.removeBy(AUTHOR, NOW);

        assertThatThrownBy(() -> post.editBy(AUTHOR, "New title", null, LATER))
                .isInstanceOf(PostNotFoundException.class);
    }

    // --- remove ---

    @Test
    void removeBy_author_softRemoves() {
        post.removeBy(AUTHOR, NOW);

        assertThat(post.isRemoved()).isTrue();
        assertThat(post.getRemovedAt()).isEqualTo(NOW);
        assertThat(post.getRemovedBy()).isEqualTo(AUTHOR.id());
    }

    @Test
    void removeBy_admin_softRemovesAndRecordsTheAdmin() {
        post.removeBy(ADMIN, NOW);

        assertThat(post.isRemoved()).isTrue();
        assertThat(post.getRemovedBy()).isEqualTo(ADMIN.id());
    }

    @Test
    void removeBy_stranger_isForbiddenAndChangesNothing() {
        assertThatThrownBy(() -> post.removeBy(STRANGER, NOW))
                .isInstanceOf(PostRemovalForbiddenException.class);

        assertThat(post.isRemoved()).isFalse();
    }

    @Test
    void removeBy_twice_succeedsAndKeepsTheFirstRemoval() {
        post.removeBy(AUTHOR, NOW);

        post.removeBy(ADMIN, LATER);
        post.removeBy(AUTHOR, LATER);

        assertThat(post.isRemoved()).isTrue();
        assertThat(post.getRemovedBy()).isEqualTo(AUTHOR.id());
        assertThat(post.getRemovedAt()).isEqualTo(NOW);
    }

    @Test
    void removeBy_strangerOnRemovedPost_isStillForbidden() {
        post.removeBy(AUTHOR, NOW);

        assertThatThrownBy(() -> post.removeBy(STRANGER, LATER))
                .isInstanceOf(PostRemovalForbiddenException.class);
    }
}
