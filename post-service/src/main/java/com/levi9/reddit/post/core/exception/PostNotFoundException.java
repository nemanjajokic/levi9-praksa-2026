package com.levi9.reddit.post.core.exception;

/** The post does not exist or was removed. Both look the same to a caller. */
public final class PostNotFoundException extends NotFoundException {

    public PostNotFoundException(Long postId) {
        super(ErrorType.POST_NOT_FOUND, "Post not found: " + postId);
    }
}
