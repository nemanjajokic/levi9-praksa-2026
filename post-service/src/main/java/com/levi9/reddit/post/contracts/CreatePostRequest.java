package com.levi9.reddit.post.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The JSON part of {@code POST /api/posts}; up to three images travel as separate multipart parts.
 * The author is never part of the request: it comes from the caller's token.
 *
 * @param content optional body text; a post may be just a title (and images)
 */
public record CreatePostRequest(
        @NotBlank @Size(max = PostLimits.TITLE_MAX) String title,
        @Size(max = PostLimits.CONTENT_MAX) String content,
        @NotNull @Positive Long communityId) {
}
