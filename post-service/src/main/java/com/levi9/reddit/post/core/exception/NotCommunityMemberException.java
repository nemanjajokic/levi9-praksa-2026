package com.levi9.reddit.post.core.exception;

/** Creating a post (or comment) requires membership in the community. */
public final class NotCommunityMemberException extends ForbiddenException {
    public NotCommunityMemberException(Long communityId) {
        super(ErrorType.NOT_COMMUNITY_MEMBER, "Join community " + communityId + " to post in it");
    }
}
