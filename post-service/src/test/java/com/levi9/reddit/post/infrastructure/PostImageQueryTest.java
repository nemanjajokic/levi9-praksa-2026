package com.levi9.reddit.post.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.levi9.reddit.post.contracts.PostImageResponse;
import com.levi9.reddit.post.contracts.PostResponse;
import com.levi9.reddit.post.core.ImageStorage;
import com.levi9.reddit.post.core.ImageStorage.StoredImage;
import com.levi9.reddit.post.core.exception.ImageNotFoundException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.core.model.Post;
import com.levi9.reddit.post.core.model.PostImage;
import com.levi9.reddit.post.infrastructure.repository.CommentRepository;
import com.levi9.reddit.post.infrastructure.repository.PostRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Images in post responses and serving one image file. */
class PostImageQueryTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final PostRepository posts = mock(PostRepository.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final PostQueryService service =
            new PostQueryService(posts, mock(CommentRepository.class), imageStorage, Clock.systemUTC());

    private final Post post = withId(new Post("Hello", 1L, "luka", 7L), 3L);
    private final PostImage first = withId(post.addImage("posts/a.png"), 10L);
    private final PostImage second = withId(post.addImage("posts/b.jpg"), 11L);

    @Test
    void postResponse_listsImagesInOrderWithTheirUrls() {
        PostResponse response = PostMapper.toResponse(post);

        assertThat(response.images()).containsExactly(
                new PostImageResponse(10L, "/api/posts/3/images/10"),
                new PostImageResponse(11L, "/api/posts/3/images/11"));
    }

    @Test
    void postResponse_withoutImages_hasEmptyList() {
        PostResponse response = PostMapper.toResponse(withId(new Post("Plain", 1L, "luka", 7L), 4L));

        assertThat(response.images()).isEmpty();
    }

    @Test
    void getImage_returnsTheFileOfThatImage() {
        when(posts.findByIdAndRemovedAtIsNull(3L)).thenReturn(Optional.of(post));
        StoredImage file = new StoredImage(PNG, "image/png");
        when(imageStorage.load("posts/b.jpg")).thenReturn(Optional.of(file));

        assertThat(service.getImage(3L, 11L)).isSameAs(file);
        verify(imageStorage).load("posts/b.jpg");
    }

    @Test
    void getImage_ofMissingOrRemovedPost_isPostNotFound() {
        when(posts.findByIdAndRemovedAtIsNull(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getImage(3L, 10L)).isInstanceOf(PostNotFoundException.class);
        verifyNoInteractions(imageStorage);
    }

    @Test
    void getImage_thatBelongsToAnotherPost_isImageNotFound() {
        when(posts.findByIdAndRemovedAtIsNull(3L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.getImage(3L, 99L))
                .isInstanceOf(ImageNotFoundException.class)
                .hasMessage("Post 3 has no image 99");
        verifyNoInteractions(imageStorage);
    }

    @Test
    void getImage_whoseFileIsMissingFromStorage_isImageNotFound() {
        when(posts.findByIdAndRemovedAtIsNull(3L)).thenReturn(Optional.of(post));
        when(imageStorage.load(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getImage(3L, 10L)).isInstanceOf(ImageNotFoundException.class);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
