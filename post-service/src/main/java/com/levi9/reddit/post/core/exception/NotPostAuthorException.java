package com.levi9.reddit.post.core.exception;

/** Only the author may edit a post; admins included. */
public final class NotPostAuthorException extends ForbiddenException {
    public NotPostAuthorException(Long postId) {
        super(ErrorType.NOT_POST_AUTHOR, "Only the author can edit post " + postId);
    }
}
