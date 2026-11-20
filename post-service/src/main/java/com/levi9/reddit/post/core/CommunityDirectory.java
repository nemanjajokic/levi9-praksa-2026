package com.levi9.reddit.post.core;

/**
 * What post-service needs to know from community-service. Communities and memberships live there; post-service
 * only asks, it never reads that data itself.
 *
 * <p>The real implementation will call community-service's {@code GET /internal/communities/{id}} and
 * {@code GET /internal/communities/{id}/members/{userId}}. Until those endpoints exist, a configurable stub stands in
 * for local runs ({@code app.community-stub.enabled}).
 */
public interface CommunityDirectory {

    /** The community exists and has not been shut down. */
    boolean isActive(long communityId);

    /** The user has joined the community. */
    boolean isMember(long communityId, long userId);
}
