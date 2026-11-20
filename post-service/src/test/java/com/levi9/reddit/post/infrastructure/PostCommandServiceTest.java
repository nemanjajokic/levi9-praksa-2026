package com.levi9.reddit.post.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.levi9.reddit.post.contracts.CreatePostRequest;
import com.levi9.reddit.post.contracts.EditPostRequest;
import com.levi9.reddit.post.contracts.PostResponse;
import com.levi9.reddit.post.core.CommunityDirectory;
import com.levi9.reddit.post.core.ImageStorage;
import com.levi9.reddit.post.core.exception.CommunityUnavailableException;
import com.levi9.reddit.post.core.exception.NotCommunityMemberException;
import com.levi9.reddit.post.core.exception.NotPostAuthorException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.core.exception.PostRemovalForbiddenException;
import com.levi9.reddit.post.core.exception.TooManyImagesException;
import com.levi9.reddit.post.core.exception.UnsupportedImageTypeException;
import com.levi9.reddit.post.core.model.Actor;
import com.levi9.reddit.post.core.model.Post;
import com.levi9.reddit.post.infrastructure.repository.PostRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final long COMMUNITY = 7L;
    private static final long POST_ID = 3L;

    private static final Actor AUTHOR = new Actor(5, "marko", false);
    private static final Actor STRANGER = new Actor(6, "ana", false);
    private static final Actor ADMIN = new Actor(8, "boss", true);

    private final PostRepository posts = mock(PostRepository.class);
    private final CommunityDirectory communities = mock(CommunityDirectory.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final PostCommandService service =
            new PostCommandService(posts, communities, imageStorage, Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    class Create {
        private final CreatePostRequest request = new CreatePostRequest("Hello", "Body", COMMUNITY);

        @BeforeEach
        void memberOfActiveCommunity() {
            when(communities.isActive(COMMUNITY)).thenReturn(true);
            when(communities.isMember(COMMUNITY, AUTHOR.id())).thenReturn(true);
            when(posts.save(any(Post.class))).thenAnswer(call -> call.getArgument(0));
        }

        @Test
        void savesPostWithCallerAsAuthor() {
            PostResponse response = service.create(request, List.of(), AUTHOR);

            ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
            verify(posts).save(saved.capture());
            Post post = saved.getValue();
            assertThat(post.getTitle()).isEqualTo("Hello");
            assertThat(post.getContent()).isEqualTo("Body");
            assertThat(post.getCommunityId()).isEqualTo(COMMUNITY);
            assertThat(post.getAuthorId()).isEqualTo(AUTHOR.id());
            assertThat(post.getAuthorUsername()).isEqualTo("marko");
            assertThat(post.getUpdatedAt()).as("a new post is not edited").isNull();
            assertThat(post.isRemoved()).isFalse();

            assertThat(response.title()).isEqualTo("Hello");
            assertThat(response.authorUsername()).isEqualTo("marko");
            assertThat(response.score()).isZero();
            assertThat(response.commentCount()).isZero();
        }

        @Test
        void withoutContent_isAllowed() {
            PostResponse response = service.create(new CreatePostRequest("Only a title", null, COMMUNITY), List.of(), AUTHOR);

            assertThat(response.content()).isNull();
        }

        @Test
        void inactiveCommunity_isUnprocessableAndSavesNothing() {
            when(communities.isActive(COMMUNITY)).thenReturn(false);

            assertThatThrownBy(() -> service.create(request, List.of(), AUTHOR))
                    .isInstanceOf(CommunityUnavailableException.class);
            verify(posts, never()).save(any());
        }

        @Test
        void nonMember_isForbiddenAndSavesNothing() {
            assertThatThrownBy(() -> service.create(request, List.of(), STRANGER))
                    .isInstanceOf(NotCommunityMemberException.class);
            verify(posts, never()).save(any());
        }
    }

    @Nested
    class CreateWithImages {
        private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
        private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5};
        private static final byte[] TEXT = "not an image".getBytes(StandardCharsets.US_ASCII);

        private final CreatePostRequest request = new CreatePostRequest("Hello", null, COMMUNITY);

        @BeforeEach
        void memberOfActiveCommunityInsideATransaction() {
            when(communities.isActive(COMMUNITY)).thenReturn(true);
            when(communities.isMember(COMMUNITY, AUTHOR.id())).thenReturn(true);
            when(posts.save(any(Post.class))).thenAnswer(call -> call.getArgument(0));
            // What @Transactional provides at runtime: a place to register the rollback clean-up.
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void endTransaction() {
            TransactionSynchronizationManager.clearSynchronization();
        }

        @Test
        void storesEachImageWithDetectedTypeAndLinksKeysToThePost() {
            service.create(request, List.of(PNG, JPEG), AUTHOR);

            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            verify(imageStorage).store(keys.capture(), eq(PNG), eq("image/png"));
            verify(imageStorage).store(keys.capture(), eq(JPEG), eq("image/jpeg"));
            assertThat(keys.getAllValues().get(0)).matches("posts/[0-9a-f-]{36}\\.png");
            assertThat(keys.getAllValues().get(1)).matches("posts/[0-9a-f-]{36}\\.jpg");

            ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
            verify(posts).save(saved.capture());
            assertThat(saved.getValue().getImages()).extracting(image -> image.getS3Key())
                    .containsExactlyElementsOf(keys.getAllValues());
        }

        @Test
        void committedTransaction_keepsTheFiles() {
            service.create(request, List.of(PNG), AUTHOR);

            complete(TransactionSynchronization.STATUS_COMMITTED);

            verify(imageStorage, never()).delete(any());
        }

        @Test
        void rolledBackTransaction_deletesTheUploadedFiles() {
            service.create(request, List.of(PNG, JPEG), AUTHOR);
            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            verify(imageStorage, times(2)).store(keys.capture(), any(), any());

            complete(TransactionSynchronization.STATUS_ROLLED_BACK);

            keys.getAllValues().forEach(key -> verify(imageStorage).delete(key));
        }

        @Test
        void failedUploadHalfway_deletesWhatWasAlreadyStored() {
            doThrow(new IllegalStateException("S3 down")).when(imageStorage).store(any(), eq(JPEG), any());

            assertThatThrownBy(() -> service.create(request, List.of(PNG, JPEG), AUTHOR))
                    .isInstanceOf(IllegalStateException.class);
            verify(posts, never()).save(any());
            ArgumentCaptor<String> firstKey = ArgumentCaptor.forClass(String.class);
            verify(imageStorage).store(firstKey.capture(), eq(PNG), any());

            complete(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(imageStorage).delete(firstKey.getValue());
        }

        @Test
        void failedCleanup_isSwallowed() {
            service.create(request, List.of(PNG), AUTHOR);
            doThrow(new IllegalStateException("S3 down")).when(imageStorage).delete(any());

            complete(TransactionSynchronization.STATUS_ROLLED_BACK); // must not throw
        }

        @Test
        void invalidImage_isRejectedBeforeAnythingHappens() {
            assertThatThrownBy(() -> service.create(request, List.of(PNG, TEXT), AUTHOR))
                    .isInstanceOf(UnsupportedImageTypeException.class);

            verifyNoInteractions(imageStorage, communities);
            verify(posts, never()).save(any());
        }

        @Test
        void tooManyImages_isRejectedBeforeAnythingHappens() {
            assertThatThrownBy(() -> service.create(request, List.of(PNG, PNG, PNG, PNG), AUTHOR))
                    .isInstanceOf(TooManyImagesException.class);

            verifyNoInteractions(imageStorage);
        }

        @Test
        void nonMember_uploadsNothing() {
            assertThatThrownBy(() -> service.create(request, List.of(PNG), STRANGER))
                    .isInstanceOf(NotCommunityMemberException.class);

            verifyNoInteractions(imageStorage);
        }

        @Test
        void unavailableCommunity_uploadsNothing() {
            when(communities.isActive(COMMUNITY)).thenReturn(false);

            assertThatThrownBy(() -> service.create(request, List.of(PNG), AUTHOR))
                    .isInstanceOf(CommunityUnavailableException.class);

            verifyNoInteractions(imageStorage);
        }

        private void complete(int status) {
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
        }
    }

    @Nested
    class Edit {
        private final EditPostRequest request = new EditPostRequest("New title", "New body");
        private final Post post = new Post("Old title", "Old body", AUTHOR.id(), AUTHOR.username(), COMMUNITY);

        @BeforeEach
        void postExists() {
            when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        }

        @Test
        void author_changesTitleContentAndUpdatedAt() {
            PostResponse response = service.edit(POST_ID, request, AUTHOR);

            assertThat(post.getTitle()).isEqualTo("New title");
            assertThat(post.getContent()).isEqualTo("New body");
            assertThat(response.title()).isEqualTo("New title");
            assertThat(response.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void stranger_isForbidden() {
            assertThatThrownBy(() -> service.edit(POST_ID, request, STRANGER))
                    .isInstanceOf(NotPostAuthorException.class);
            assertThat(post.getTitle()).isEqualTo("Old title");
        }

        @Test
        void admin_isForbidden() {
            assertThatThrownBy(() -> service.edit(POST_ID, request, ADMIN))
                    .isInstanceOf(NotPostAuthorException.class);
        }

        @Test
        void removedPost_isNotFound() {
            post.remove(AUTHOR.id(), NOW.minusSeconds(60));

            assertThatThrownBy(() -> service.edit(POST_ID, request, AUTHOR))
                    .isInstanceOf(PostNotFoundException.class);
        }

        @Test
        void missingPost_isNotFound() {
            assertThatThrownBy(() -> service.edit(99L, request, AUTHOR))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    class Delete {
        private final Post post = new Post("Title", "Body", AUTHOR.id(), AUTHOR.username(), COMMUNITY);

        @BeforeEach
        void postExists() {
            when(posts.findByIdForUpdate(POST_ID)).thenReturn(Optional.of(post));
        }

        @Test
        void author_softRemoves() {
            service.delete(POST_ID, AUTHOR);

            assertThat(post.isRemoved()).isTrue();
            assertThat(post.getRemovedAt()).isEqualTo(NOW);
            assertThat(post.getRemovedBy()).isEqualTo(AUTHOR.id());
            verify(posts, never()).delete(any());
        }

        @Test
        void admin_softRemoves() {
            service.delete(POST_ID, ADMIN);

            assertThat(post.getRemovedBy()).isEqualTo(ADMIN.id());
        }

        @Test
        void twice_succeedsAndKeepsFirstRemoval() {
            service.delete(POST_ID, AUTHOR);
            service.delete(POST_ID, ADMIN);

            assertThat(post.getRemovedBy()).isEqualTo(AUTHOR.id());
        }

        @Test
        void stranger_isForbidden() {
            assertThatThrownBy(() -> service.delete(POST_ID, STRANGER))
                    .isInstanceOf(PostRemovalForbiddenException.class);
            assertThat(post.isRemoved()).isFalse();
        }

        @Test
        void missingPost_isNotFound() {
            assertThatThrownBy(() -> service.delete(99L, AUTHOR)).isInstanceOf(PostNotFoundException.class);
        }
    }
}
