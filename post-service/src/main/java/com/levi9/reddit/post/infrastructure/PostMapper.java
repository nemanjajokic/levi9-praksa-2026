package com.levi9.reddit.post.infrastructure;

import com.levi9.reddit.post.contracts.PostImageResponse;
import com.levi9.reddit.post.contracts.PostResponse;
import com.levi9.reddit.post.core.model.Post;

/**
 * Entity to contract mapping shared by the query and command services. Reading {@link Post#getImages()} loads
 * the lazy collection; {@code hibernate.default_batch_fetch_size} in application.yml loads it for a whole page of
 * posts in one query instead of one query per post.
 */
final class PostMapper {

    private PostMapper() {}

    static PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorId(),
                post.getAuthorUsername(),
                post.getCommunityId(),
                post.getScore(),
                post.getCommentCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getImages().stream()
                        .map(image -> new PostImageResponse(image.getId(), imageUrl(post.getId(), image.getId())))
                        .toList());
    }

    static String imageUrl(Long postId, Long imageId) {
        return "/api/posts/" + postId + "/images/" + imageId;
    }
}
