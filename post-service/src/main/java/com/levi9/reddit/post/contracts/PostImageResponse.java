package com.levi9.reddit.post.contracts;

/**
 * One image of a post.
 *
 * @param url relative URL of the image file ({@code /api/posts/{postId}/images/{id}}), served by post-service
 *            itself so the bucket stays private; use it directly as an {@code <img src>}
 */
public record PostImageResponse(Long id, String url) {
}
