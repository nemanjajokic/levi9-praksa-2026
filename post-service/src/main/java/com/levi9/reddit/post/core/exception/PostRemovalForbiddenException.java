package com.levi9.reddit.post.core.exception;

/** Only the author or an admin may remove a post. */
public final class PostRemovalForbiddenException extends ForbiddenException {
    public PostRemovalForbiddenException(Long postId) {
        super(ErrorType.POST_REMOVAL_FORBIDDEN, "Only the author or an admin can remove post " + postId);
    }
}
