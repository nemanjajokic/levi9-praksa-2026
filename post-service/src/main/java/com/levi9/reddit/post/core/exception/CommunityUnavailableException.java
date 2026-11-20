package com.levi9.reddit.post.core.exception;

/** The communityId in the request names a community that doesn't exist or was shut down. */
public final class CommunityUnavailableException extends ValidationException {
    public CommunityUnavailableException(Long communityId) {
        super(ErrorType.COMMUNITY_UNAVAILABLE, "Community " + communityId + " does not exist or is not active");
    }
}
