package com.levi9.reddit.post.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/posts/{id}}. Title and content are the only editable fields (community, author and
 * images stay as they are), and both are sent every time: a null {@code content} clears the body text.
 */
public record EditPostRequest(
        @NotBlank @Size(max = PostLimits.TITLE_MAX) String title,
        @Size(max = PostLimits.CONTENT_MAX) String content) {
}
