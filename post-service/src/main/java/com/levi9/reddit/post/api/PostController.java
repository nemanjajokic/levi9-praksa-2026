package com.levi9.reddit.post.api;

import com.levi9.reddit.post.contracts.CommentResponse;
import com.levi9.reddit.post.contracts.CreatePostRequest;
import com.levi9.reddit.post.contracts.EditPostRequest;
import com.levi9.reddit.post.contracts.PageResponse;
import com.levi9.reddit.post.contracts.PostResponse;
import com.levi9.reddit.post.contracts.TopPeriod;
import com.levi9.reddit.post.infrastructure.PostCommandService;
import com.levi9.reddit.post.infrastructure.PostQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.UncheckedIOException;
import com.levi9.reddit.post.core.ImageStorage.StoredImage;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
@Tag(name = "Posts")
public class PostController {
    private static final Duration IMAGE_CACHE = Duration.ofDays(1);

    private final PostQueryService postQueryService;
    private final PostCommandService postCommandService;

    public PostController(PostQueryService postQueryService, PostCommandService postCommandService) {
        this.postQueryService = postQueryService;
        this.postCommandService = postCommandService;
    }

    @GetMapping
    @Operation(summary = "Latest posts, optionally filtered by community and/or searched by title")
    public PageResponse<PostResponse> findPosts(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) @Size(max = 300) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return postQueryService.findPosts(communityId, q, page, size);
    }

    @GetMapping("/top")
    @Operation(summary = "Top 10 posts by score: of all time, or only posts created in the last 7 days")
    public List<PostResponse> findTop(@RequestParam(defaultValue = "ALL") TopPeriod period) {
        return postQueryService.findTop(period);
    }

    @GetMapping("/{id}")
    @Operation(summary = "A single post; 404 if it doesn't exist or was removed")
    public PostResponse getPost(@PathVariable Long id) {
        return postQueryService.getPost(id);
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Comments of a post, newest first")
    public PageResponse<CommentResponse> findComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return postQueryService.findComments(id, page, size);
    }

    // The bucket is private: image files are served through here, which also hides them with their post.
    // A file under a key never changes, so browsers may cache it. One day (not "forever") so that removing a post
    // also takes its images out of caches reasonably soon.
    @GetMapping("/{id}/images/{imageId}")
    @Operation(summary = "The file of one image of a post; 404 if the post is missing or removed, or has no such image")
    public ResponseEntity<byte[]> getImage(@PathVariable long id, @PathVariable long imageId) {
        StoredImage image = postQueryService.getImage(id, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(IMAGE_CACHE).cachePublic())
                .header("X-Content-Type-Options", "nosniff") // never let a browser guess another type
                .body(image.content());
    }

    // Multipart, as the endpoints doc specifies: the "post" part is JSON (Content-Type: application/json), and
    // "images" may repeat up to 3 times. Empty file parts (an untouched file input) are ignored.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a post (member of the community). Multipart: a JSON part named \"post\" and up to "
            + "3 \"images\" (JPEG or PNG, 5 MB each); 201 with Location. 403 if not a member, "
            + "422 if the community is missing or shut down or an image is rejected, 413 if a file is too large")
    public ResponseEntity<PostResponse> create(
            @Valid @RequestPart("post") CreatePostRequest request,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal Jwt jwt) {
        PostResponse created = postCommandService.create(request, bytesOf(images), Actors.from(jwt));
        return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit title and content (author only). 403 if not the author, 404 if missing or removed")
    public PostResponse edit(
            @PathVariable long id,
            @Valid @RequestBody EditPostRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return postCommandService.edit(id, request, Actors.from(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft remove (author or ADMIN). 204, also if already removed. 403 otherwise, 404 if missing")
    public void delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        postCommandService.delete(id, Actors.from(jwt));
    }

    private static List<byte[]> bytesOf(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> !file.isEmpty())
                .map(PostController::bytesOf)
                .toList();
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }
}
