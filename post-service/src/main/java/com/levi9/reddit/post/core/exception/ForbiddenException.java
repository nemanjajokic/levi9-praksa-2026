package com.levi9.reddit.post.core.exception;

/** 403: the caller is known but not allowed to do this. */
public sealed class ForbiddenException extends DomainException
        permits NotPostAuthorException, PostRemovalForbiddenException, NotCommunityMemberException {
    public ForbiddenException(ErrorType errorType, String detail) {
        super(errorType, detail);
    }
}
