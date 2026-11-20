package com.levi9.reddit.post.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.levi9.reddit.post.contracts.CreatePostRequest;
import com.levi9.reddit.post.contracts.EditPostRequest;
import com.levi9.reddit.post.core.exception.UnsupportedImageTypeException;
import org.mockito.ArgumentCaptor;
import com.levi9.reddit.post.contracts.PostResponse;
import com.levi9.reddit.post.core.exception.CommunityUnavailableException;
import com.levi9.reddit.post.core.exception.NotCommunityMemberException;
import com.levi9.reddit.post.core.exception.NotPostAuthorException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.core.exception.PostRemovalForbiddenException;
import com.levi9.reddit.post.core.model.Actor;
import com.levi9.reddit.post.infrastructure.PostCommandService;
import com.levi9.reddit.post.infrastructure.PostQueryService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Create, edit and delete through the real {@link SecurityConfig}, with the mock caller off (as in production). */
@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.mock-auth.enabled=false")
class PostCommandControllerTest {

    private static final Instant CREATED = Instant.parse("2026-11-19T10:00:00Z");
    private static final Actor MARKO = new Actor(42, "marko", false);
    private static final Actor ADMIN = new Actor(1, "boss", true);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostQueryService postQueryService;

    @MockitoBean
    private PostCommandService postCommandService;

    private static JwtRequestPostProcessor as(Actor actor) {
        List<String> roles = actor.admin() ? List.of("USER", "ADMIN") : List.of("USER");
        return jwt().jwt(j -> j.subject(String.valueOf(actor.id()))
                .claim("username", actor.username())
                .claim("roles", roles));
    }

    private static PostResponse response(long id, String title, Instant updatedAt) {
        return new PostResponse(id, title, "Body", MARKO.id(), MARKO.username(), 7L, 0, 0, CREATED, updatedAt, List.of());
    }

    @Nested
    class Create {

        private static MockMultipartFile postPart(String json) {
            return new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE,
                    json.getBytes(StandardCharsets.UTF_8));
        }

        private final MockMultipartFile validPart =
                postPart("{\"title\":\"Hello\",\"content\":\"Body\",\"communityId\":7}");

        @Test
        void withoutToken_isUnauthorized() throws Exception {
            mockMvc.perform(multipart("/api/posts").file(validPart))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(postCommandService);
        }

        @Test
        void member_createsPost_201WithLocation_authorFromToken() throws Exception {
            when(postCommandService.create(any(), any(), any())).thenReturn(response(3, "Hello", null));

            mockMvc.perform(multipart("/api/posts").file(validPart).with(as(MARKO)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/posts/3"))
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.authorUsername").value("marko"));

            verify(postCommandService).create(new CreatePostRequest("Hello", "Body", 7L), List.of(), MARKO);
        }

        // What Swagger UI sends: the JSON text field without a Content-Type (or as application/octet-stream).
        @Test
        void postPartWithoutJsonContentType_isStillRead() throws Exception {
            when(postCommandService.create(any(), any(), any())).thenReturn(response(3, "Hello", null));
            byte[] json = "{\"title\":\"Hello\",\"content\":\"Body\",\"communityId\":7}".getBytes(StandardCharsets.UTF_8);

            mockMvc.perform(multipart("/api/posts")
                            .file(new MockMultipartFile("post", "", null, json))
                            .with(as(MARKO)))
                    .andExpect(status().isCreated());
            mockMvc.perform(multipart("/api/posts")
                            .file(new MockMultipartFile("post", "", MediaType.APPLICATION_OCTET_STREAM_VALUE, json))
                            .with(as(MARKO)))
                    .andExpect(status().isCreated());

            verify(postCommandService, times(2)).create(new CreatePostRequest("Hello", "Body", 7L), List.of(), MARKO);
        }

        @Test
        void postPartWithoutContentType_stillValidated() throws Exception {
            mockMvc.perform(multipart("/api/posts")
                            .file(new MockMultipartFile("post", "", null,
                                    "{\"title\":\"\",\"communityId\":7}".getBytes(StandardCharsets.UTF_8)))
                            .with(as(MARKO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"));
            verifyNoInteractions(postCommandService);
        }

        @Test
        void authorIdInBody_isIgnored() throws Exception {
            when(postCommandService.create(any(), any(), any())).thenReturn(response(3, "Hello", null));
            MockMultipartFile sneaky = postPart(
                    "{\"title\":\"Hello\",\"content\":\"Body\",\"communityId\":7,\"authorId\":1,\"authorUsername\":\"boss\"}");

            mockMvc.perform(multipart("/api/posts").file(sneaky).with(as(MARKO)))
                    .andExpect(status().isCreated());

            verify(postCommandService).create(new CreatePostRequest("Hello", "Body", 7L), List.of(), MARKO);
        }

        @Test
        void blankTitle_isBadRequest() throws Exception {
            mockMvc.perform(multipart("/api/posts")
                            .file(postPart("{\"title\":\" \",\"communityId\":7}"))
                            .with(as(MARKO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Request validation failed"))
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
            verifyNoInteractions(postCommandService);
        }

        @Test
        void missingCommunityId_isBadRequest() throws Exception {
            mockMvc.perform(multipart("/api/posts").file(postPart("{\"title\":\"Hello\"}")).with(as(MARKO)))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(postCommandService);
        }

        @Test
        void plainJsonBody_isUnsupportedMediaType() throws Exception {
            mockMvc.perform(post("/api/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Hello\",\"communityId\":7}")
                            .with(as(MARKO)))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void nonMember_isForbidden() throws Exception {
            when(postCommandService.create(any(), any(), any())).thenThrow(new NotCommunityMemberException(7L));

            mockMvc.perform(multipart("/api/posts").file(validPart).with(as(MARKO)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorType").value("NOT_COMMUNITY_MEMBER"));
        }

        @Test
        void unavailableCommunity_isUnprocessable() throws Exception {
            when(postCommandService.create(any(), any(), any())).thenThrow(new CommunityUnavailableException(7L));

            mockMvc.perform(multipart("/api/posts").file(validPart).with(as(MARKO)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorType").value("COMMUNITY_UNAVAILABLE"));
        }

        @Test
        void images_arePassedToTheServiceInOrder_emptyFilePartsSkipped() throws Exception {
            when(postCommandService.create(any(), any(), any())).thenReturn(response(3, "Hello", null));
            byte[] first = {1, 2, 3};
            byte[] second = {4, 5};

            mockMvc.perform(multipart("/api/posts")
                            .file(validPart)
                            .file(new MockMultipartFile("images", "a.png", "image/png", first))
                            .file(new MockMultipartFile("images", "", "application/octet-stream", new byte[0]))
                            .file(new MockMultipartFile("images", "b.jpg", "image/jpeg", second))
                            .with(as(MARKO)))
                    .andExpect(status().isCreated());

            ArgumentCaptor<List<byte[]>> images = ArgumentCaptor.captor();
            verify(postCommandService).create(eq(new CreatePostRequest("Hello", "Body", 7L)), images.capture(), eq(MARKO));
            assertThat(images.getValue()).containsExactly(first, second);
        }

        @Test
        void rejectedImage_isUnprocessable() throws Exception {
            when(postCommandService.create(any(), any(), any())).thenThrow(new UnsupportedImageTypeException(1));

            mockMvc.perform(multipart("/api/posts")
                            .file(validPart)
                            .file(new MockMultipartFile("images", "evil.png", "image/png", new byte[] {1}))
                            .with(as(MARKO)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorType").value("UNSUPPORTED_IMAGE_TYPE"));
        }
    }

    @Nested
    class Edit {
        private static final String BODY = "{\"title\":\"New title\",\"content\":\"New body\"}";

        @Test
        void withoutToken_isUnauthorized() throws Exception {
            mockMvc.perform(patch("/api/posts/3").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(postCommandService);
        }

        @Test
        void author_editsPost_200() throws Exception {
            when(postCommandService.edit(eq(3L), any(), any())).thenReturn(response(3, "New title", CREATED));

            mockMvc.perform(patch("/api/posts/3")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY).with(as(MARKO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("New title"))
                    .andExpect(jsonPath("$.updatedAt").exists());

            verify(postCommandService).edit(3L, new EditPostRequest("New title", "New body"), MARKO);
        }

        @Test
        void tooLongTitle_isBadRequest() throws Exception {
            String body = "{\"title\":\"" + "x".repeat(301) + "\"}";

            mockMvc.perform(patch("/api/posts/3")
                            .contentType(MediaType.APPLICATION_JSON).content(body).with(as(MARKO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"));
            verifyNoInteractions(postCommandService);
        }

        @Test
        void notAuthor_isForbidden() throws Exception {
            when(postCommandService.edit(anyLong(), any(), any())).thenThrow(new NotPostAuthorException(3L));

            mockMvc.perform(patch("/api/posts/3")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY).with(as(ADMIN)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorType").value("NOT_POST_AUTHOR"));
        }

        @Test
        void missingOrRemovedPost_isNotFound() throws Exception {
            when(postCommandService.edit(anyLong(), any(), any())).thenThrow(new PostNotFoundException(3L));

            mockMvc.perform(patch("/api/posts/3")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY).with(as(MARKO)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorType").value("POST_NOT_FOUND"));
        }
    }

    @Nested
    class Delete {

        @Test
        void withoutToken_isUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/posts/3")).andExpect(status().isUnauthorized());
            verifyNoInteractions(postCommandService);
        }

        @Test
        void author_removes_204() throws Exception {
            mockMvc.perform(delete("/api/posts/3").with(as(MARKO)))
                    .andExpect(status().isNoContent());

            verify(postCommandService).delete(3L, MARKO);
        }

        @Test
        void adminRoleFromToken_reachesService() throws Exception {
            mockMvc.perform(delete("/api/posts/3").with(as(ADMIN)))
                    .andExpect(status().isNoContent());

            verify(postCommandService).delete(3L, ADMIN);
        }

        @Test
        void stranger_isForbidden() throws Exception {
            doThrow(new PostRemovalForbiddenException(3L)).when(postCommandService).delete(anyLong(), any());

            mockMvc.perform(delete("/api/posts/3").with(as(MARKO)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorType").value("POST_REMOVAL_FORBIDDEN"));
        }

        @Test
        void missingPost_isNotFound() throws Exception {
            doThrow(new PostNotFoundException(3L)).when(postCommandService).delete(anyLong(), any());

            mockMvc.perform(delete("/api/posts/3").with(as(MARKO)))
                    .andExpect(status().isNotFound());
        }
    }
}
