package com.levi9.reddit.post.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.levi9.reddit.post.contracts.CommentResponse;
import com.levi9.reddit.post.contracts.PageResponse;
import com.levi9.reddit.post.contracts.PostImageResponse;
import com.levi9.reddit.post.contracts.PostResponse;
import com.levi9.reddit.post.contracts.TopPeriod;
import com.levi9.reddit.post.core.ImageStorage.StoredImage;
import com.levi9.reddit.post.core.exception.ImageNotFoundException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.infrastructure.PostCommandService;
import com.levi9.reddit.post.infrastructure.PostQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostQueryService postQueryService;

    @MockitoBean
    private PostCommandService postCommandService;

    @Test
    void findPosts_isPublicAndUsesDefaultPaging() throws Exception {
        PostResponse post = new PostResponse(1L, "Hello", "Body", 2L, "luka", 7L, 3, 0, CREATED, null, List.of());
        when(postQueryService.findPosts(null, null, 0, 20)).thenReturn(new PageResponse<>(List.of(post), 0, 20, 1, 1));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Hello"))
                .andExpect(jsonPath("$.content[0].authorUsername").value("luka"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getPost_listsImagesWithUrls() throws Exception {
        when(postQueryService.getPost(1L)).thenReturn(new PostResponse(1L, "Hello", "Body", 2L, "luka", 7L, 3, 0,
                CREATED, null, List.of(new PostImageResponse(10L, "/api/posts/1/images/10"))));

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].id").value(10))
                .andExpect(jsonPath("$.images[0].url").value("/api/posts/1/images/10"));
    }

    @Test
    void getPost_withoutImages_hasEmptyArray() throws Exception {
        when(postQueryService.getPost(1L))
                .thenReturn(new PostResponse(1L, "Hello", "Body", 2L, "luka", 7L, 3, 0, CREATED, null, null));

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images").isEmpty());
    }

    @Test
    void getImage_isPublic_servesBytesWithTypeAndCacheHeaders() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        when(postQueryService.getImage(1L, 10L)).thenReturn(new StoredImage(png, "image/png"));

        mockMvc.perform(get("/api/posts/1/images/10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void getImage_missing_isNotFound() throws Exception {
        when(postQueryService.getImage(1L, 99L)).thenThrow(new ImageNotFoundException(1L, 99L));

        mockMvc.perform(get("/api/posts/1/images/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").value("IMAGE_NOT_FOUND"));
    }

    @Test
    void findPosts_passesFiltersAndPaging() throws Exception {
        when(postQueryService.findPosts(7L, "spring", 2, 10)).thenReturn(new PageResponse<>(List.of(), 2, 10, 0, 0));

        mockMvc.perform(get("/api/posts").param("communityId", "7").param("q", "spring")
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void findPosts_withTooLargePageSize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/posts").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        verifyNoInteractions(postQueryService);
    }

    @Test
    void findPosts_withNonNumericPage_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/posts").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findTop_withoutPeriod_isPublicAndDefaultsToAllTime() throws Exception {
        PostResponse post = new PostResponse(1L, "Hello", "Body", 2L, "luka", 7L, 42, 0, CREATED, null, List.of());
        when(postQueryService.findTop(TopPeriod.ALL)).thenReturn(List.of(post));

        mockMvc.perform(get("/api/posts/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Hello"))
                .andExpect(jsonPath("$[0].score").value(42));
    }

    @Test
    void findTop_withWeekPeriod_passesWeek() throws Exception {
        PostResponse post = new PostResponse(1L, "Fresh", "Body", 2L, "luka", 7L, 5, 0, CREATED, null, List.of());
        when(postQueryService.findTop(TopPeriod.WEEK)).thenReturn(List.of(post));

        mockMvc.perform(get("/api/posts/top").param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Fresh"));
    }

    @Test
    void findTop_withUnknownPeriod_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/posts/top").param("period", "YEAR"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postQueryService);
    }

    @Test
    void getPost_returnsPost() throws Exception {
        when(postQueryService.getPost(1L))
                .thenReturn(new PostResponse(1L, "Hello", "Body", 2L, "luka", 7L, 3, 0, CREATED, null, List.of()));

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("Body"));
    }

    @Test
    void getPost_notFound_returnsProblemDetail() throws Exception {
        when(postQueryService.getPost(99L)).thenThrow(new PostNotFoundException(99L));

        mockMvc.perform(get("/api/posts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorType").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Post not found: 99"));
    }

    @Test
    void findComments_returnsComments() throws Exception {
        CommentResponse comment = new CommentResponse(10L, 1L, 2L, "ana", "Nice", 0, CREATED);
        when(postQueryService.findComments(1L, 0, 20)).thenReturn(new PageResponse<>(List.of(comment), 0, 20, 1, 1));

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Nice"));
    }

    @Test
    void findComments_ofMissingPost_returnsNotFound() throws Exception {
        when(postQueryService.findComments(any(), anyInt(), anyInt())).thenThrow(new PostNotFoundException(99L));

        mockMvc.perform(get("/api/posts/99/comments"))
                .andExpect(status().isNotFound());
    }
}
