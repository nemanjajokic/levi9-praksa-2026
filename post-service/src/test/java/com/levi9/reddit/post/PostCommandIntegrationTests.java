package com.levi9.reddit.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.levi9.reddit.post.core.model.Post;
import com.levi9.reddit.post.core.model.PostImage;
import com.levi9.reddit.post.infrastructure.S3Properties;
import com.levi9.reddit.post.infrastructure.repository.PostRepository;
import software.amazon.awssdk.services.s3.S3Client;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create, edit and delete end to end: HTTP, security, controller, service, community stub and real Postgres.
 *
 * <p>MockMvc runs in the test's thread, so each request joins the test transaction (rolled back afterwards).
 * {@link #reloadFromDatabase} flushes and clears the persistence context, so assertions read what was really
 * written instead of objects Hibernate still holds in memory.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostCommandIntegrationTests {

    private static final long COMMUNITY = 100L;
    private static final long UNAVAILABLE_COMMUNITY = 999L; // see src/test/resources/application.properties
    private static final long NON_MEMBER = 999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private S3Client s3;

    @Autowired
    private S3Properties s3Properties;

    // --- the whole life of a post ---

    @Test
    void createEditDeleteLifecycle() throws Exception {
        // create
        long id = create(as(42, "marko"), "Hello", "First body", COMMUNITY);
        Post created = reloadFromDatabase(id);
        assertThat(created.getTitle()).isEqualTo("Hello");
        assertThat(created.getContent()).isEqualTo("First body");
        assertThat(created.getAuthorId()).isEqualTo(42L);
        assertThat(created.getAuthorUsername()).isEqualTo("marko");
        assertThat(created.getCommunityId()).isEqualTo(COMMUNITY);
        assertThat(created.getUpdatedAt()).isNull();

        mockMvc.perform(get("/api/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello"))
                .andExpect(jsonPath("$.authorUsername").value("marko"));

        // edit by the author
        mockMvc.perform(edit(id, "{\"title\":\"Hello again\",\"content\":\"Second body\"}").with(as(42, "marko")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello again"));

        Post edited = reloadFromDatabase(id);
        assertThat(edited.getTitle()).isEqualTo("Hello again");
        assertThat(edited.getContent()).isEqualTo("Second body");
        assertThat(edited.getUpdatedAt()).isNotNull();

        mockMvc.perform(get("/api/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello again"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        // delete by the author: soft remove, the row stays
        mockMvc.perform(delete("/api/posts/{id}", id).with(as(42, "marko")))
                .andExpect(status().isNoContent());

        Post removed = reloadFromDatabase(id);
        assertThat(removed.getRemovedBy()).isEqualTo(42L);
        assertThat(removed.getRemovedAt()).isNotNull();

        mockMvc.perform(get("/api/posts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").value("POST_NOT_FOUND"));

        // deleting again succeeds and keeps the first removal
        mockMvc.perform(delete("/api/posts/{id}", id).with(asAdmin(1, "boss")))
                .andExpect(status().isNoContent());

        Post removedTwice = reloadFromDatabase(id);
        assertThat(removedTwice.getRemovedBy()).isEqualTo(42L);
        assertThat(removedTwice.getRemovedAt()).isEqualTo(removed.getRemovedAt());
    }

    // --- who may do what, against the real database ---

    @Test
    void strangerCannotEdit_andNothingChanges() throws Exception {
        long id = create(as(42, "marko"), "Mine", null, COMMUNITY);

        mockMvc.perform(edit(id, "{\"title\":\"Hacked\"}").with(as(43, "ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorType").value("NOT_POST_AUTHOR"));

        Post stored = reloadFromDatabase(id);
        assertThat(stored.getTitle()).isEqualTo("Mine");
        assertThat(stored.getUpdatedAt()).isNull();
    }

    @Test
    void adminCannotEditSomeoneElsesPost() throws Exception {
        long id = create(as(42, "marko"), "Mine", null, COMMUNITY);

        mockMvc.perform(edit(id, "{\"title\":\"Admin title\"}").with(asAdmin(1, "boss")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorType").value("NOT_POST_AUTHOR"));

        assertThat(reloadFromDatabase(id).getTitle()).isEqualTo("Mine");
    }

    @Test
    void strangerCannotDelete_andThePostStays() throws Exception {
        long id = create(as(42, "marko"), "Mine", null, COMMUNITY);

        mockMvc.perform(delete("/api/posts/{id}", id).with(as(43, "ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorType").value("POST_REMOVAL_FORBIDDEN"));

        assertThat(reloadFromDatabase(id).isRemoved()).isFalse();
    }

    @Test
    void adminDeletesSomeoneElsesPost_andIsRecordedAsRemover() throws Exception {
        long id = create(as(42, "marko"), "Mine", null, COMMUNITY);

        mockMvc.perform(delete("/api/posts/{id}", id).with(asAdmin(1, "boss")))
                .andExpect(status().isNoContent());

        assertThat(reloadFromDatabase(id).getRemovedBy()).isEqualTo(1L);
    }

    @Test
    void editingARemovedPost_isNotFound() throws Exception {
        long id = create(as(42, "marko"), "Mine", null, COMMUNITY);
        mockMvc.perform(delete("/api/posts/{id}", id).with(as(42, "marko"))).andExpect(status().isNoContent());

        mockMvc.perform(edit(id, "{\"title\":\"Too late\"}").with(as(42, "marko")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").value("POST_NOT_FOUND"));
    }

    @Test
    void missingPost_isNotFoundForEditAndDelete() throws Exception {
        mockMvc.perform(edit(Long.MAX_VALUE, "{\"title\":\"Ghost\"}").with(as(42, "marko")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/posts/{id}", Long.MAX_VALUE).with(as(42, "marko")))
                .andExpect(status().isNotFound());
    }

    // --- community checks (stub) and validation: nothing is written ---

    @Test
    void nonMember_isForbidden_andNothingIsSaved() throws Exception {
        long before = postRepository.count();

        mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"Hello\",\"communityId\":" + COMMUNITY + "}"))
                        .with(as(NON_MEMBER, "outsider")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorType").value("NOT_COMMUNITY_MEMBER"));

        assertThat(postRepository.count()).isEqualTo(before);
    }

    @Test
    void unavailableCommunity_isUnprocessable_andNothingIsSaved() throws Exception {
        long before = postRepository.count();

        mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"Hello\",\"communityId\":" + UNAVAILABLE_COMMUNITY + "}"))
                        .with(as(42, "marko")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorType").value("COMMUNITY_UNAVAILABLE"));

        assertThat(postRepository.count()).isEqualTo(before);
    }

    @Test
    void invalidBody_isBadRequestWithFieldErrors_andNothingIsSaved() throws Exception {
        long before = postRepository.count();

        mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"\",\"communityId\":" + COMMUNITY + "}"))
                        .with(as(42, "marko")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        assertThat(postRepository.count()).isEqualTo(before);
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"Hello\",\"communityId\":" + COMMUNITY + "}")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
    }

    // --- images: real upload to S3 (Moto) ---

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D, 'I', 'H'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};

    @Test
    void createWithImages_storesFilesInS3AndKeysInTheDatabase() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"With pictures\",\"communityId\":" + COMMUNITY + "}"))
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .file(new MockMultipartFile("images", "b.jpg", "image/jpeg", JPEG))
                        .with(as(42, "marko")))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        List<PostImage> images = reloadFromDatabase(id).getImages();
        assertThat(images).hasSize(2);
        assertThat(images.get(0).getS3Key()).endsWith(".png");
        assertThat(images.get(1).getS3Key()).endsWith(".jpg");

        var png = s3.getObjectAsBytes(b -> b.bucket(s3Properties.bucket()).key(images.get(0).getS3Key()));
        assertThat(png.asByteArray()).isEqualTo(PNG);
        assertThat(png.response().contentType()).isEqualTo("image/png");
        var jpeg = s3.getObjectAsBytes(b -> b.bucket(s3Properties.bucket()).key(images.get(1).getS3Key()));
        assertThat(jpeg.response().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void rejectedCreate_uploadsNothing() throws Exception {
        int before = storedImageCount();

        // not a member
        mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"Hello\",\"communityId\":" + COMMUNITY + "}"))
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .with(as(NON_MEMBER, "outsider")))
                .andExpect(status().isForbidden());

        // one file is not an image: the valid one before it is not uploaded either
        mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"Hello\",\"communityId\":" + COMMUNITY + "}"))
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .file(new MockMultipartFile("images", "evil.png", "image/png",
                                "<script>".getBytes(StandardCharsets.UTF_8)))
                        .with(as(42, "marko")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorType").value("UNSUPPORTED_IMAGE_TYPE"));

        // four images
        var fourImages = multipart("/api/posts")
                .file(postPart("{\"title\":\"Hello\",\"communityId\":" + COMMUNITY + "}"));
        for (int i = 0; i < 4; i++) {
            fourImages.file(new MockMultipartFile("images", i + ".png", "image/png", PNG));
        }
        mockMvc.perform(fourImages.with(as(42, "marko")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorType").value("TOO_MANY_IMAGES"));

        assertThat(storedImageCount()).isEqualTo(before);
    }

    @Test
    void imagesAreListedOnThePostAndServedUntilThePostIsRemoved() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/posts")
                        .file(postPart("{\"title\":\"Gallery\",\"communityId\":" + COMMUNITY + "}"))
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .file(new MockMultipartFile("images", "b.jpg", "image/jpeg", JPEG))
                        .with(as(42, "marko")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        String pngUrl = JsonPath.read(body, "$.images[0].url");
        String jpegUrl = JsonPath.read(body, "$.images[1].url");
        assertThat(pngUrl).matches("/api/posts/" + id + "/images/\\d+");
        entityManager.flush();
        entityManager.clear();

        // single post and the list both show the images, in upload order
        mockMvc.perform(get("/api/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].url").value(pngUrl))
                .andExpect(jsonPath("$.images[1].url").value(jpegUrl));
        mockMvc.perform(get("/api/posts").param("communityId", String.valueOf(COMMUNITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Gallery"))
                .andExpect(jsonPath("$.content[0].images.length()").value(2));

        // the files come back from S3 through the public image endpoint, without a token
        mockMvc.perform(get(pngUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get(jpegUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));

        // another post's id in the URL does not reach this image
        long otherId = create(as(42, "marko"), "Other", null, COMMUNITY);
        String imageId = pngUrl.substring(pngUrl.lastIndexOf('/') + 1);
        mockMvc.perform(get("/api/posts/{id}/images/{imageId}", otherId, imageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").value("IMAGE_NOT_FOUND"));

        // removing the post hides its images too
        mockMvc.perform(delete("/api/posts/{id}", id).with(as(42, "marko"))).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get(pngUrl))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").value("POST_NOT_FOUND"));
    }

    private int storedImageCount() {
        return s3.listObjectsV2(b -> b.bucket(s3Properties.bucket()).prefix("posts/")).keyCount();
    }

    // --- helpers ---

    private long create(JwtRequestPostProcessor caller, String title, String content, long communityId)
            throws Exception {
        String json = "{\"title\":\"" + title + "\","
                + (content == null ? "" : "\"content\":\"" + content + "\",")
                + "\"communityId\":" + communityId + "}";
        MvcResult result = mockMvc.perform(multipart("/api/posts").file(postPart(json)).with(caller))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/posts/" + id);
        return id;
    }

    private static MockHttpServletRequestBuilder edit(long id, String json) {
        return patch("/api/posts/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockMultipartFile postPart(String json) {
        return new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static JwtRequestPostProcessor as(long userId, String username) {
        return caller(userId, username, List.of("USER"));
    }

    private static JwtRequestPostProcessor asAdmin(long userId, String username) {
        return caller(userId, username, List.of("USER", "ADMIN"));
    }

    private static JwtRequestPostProcessor caller(long userId, String username, List<String> roles) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)).claim("username", username).claim("roles", roles));
    }

    /** Writes pending changes and forgets cached entities, so the next read comes from Postgres. */
    private Post reloadFromDatabase(long id) {
        entityManager.flush();
        entityManager.clear();
        return postRepository.findById(id).orElseThrow();
    }
}
