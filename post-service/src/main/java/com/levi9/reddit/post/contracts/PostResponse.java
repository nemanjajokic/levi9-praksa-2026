package com.levi9.reddit.post.contracts;

import java.time.Instant;
import java.util.List;

/** @param images up to three, in upload order; empty (never null) for a post without images */
public record PostResponse(
        Long id,
        String title,
        String content,
        Long authorId,
        String authorUsername,
        Long communityId,
        int score,
        int commentCount,
        Instant createdAt,
        Instant updatedAt,
        List<PostImageResponse> images) {

    public PostResponse {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
