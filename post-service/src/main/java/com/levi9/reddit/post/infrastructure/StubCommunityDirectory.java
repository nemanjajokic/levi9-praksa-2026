package com.levi9.reddit.post.infrastructure;

import com.levi9.reddit.post.core.CommunityDirectory;

/**
 * Temporary stand-in for community-service's {@code /internal} endpoints, which don't exist yet. LOCAL ONLY.
 *
 * <p>Every community is active and every user is a member of every community, except the ids listed in
 * {@link CommunityStubProperties}. Those lists let you try the 422 (community unavailable) and 403 (not a member)
 * paths by hand, e.g. together with the {@code X-Mock-User-Id} header of the mock auth filter.
 */
final class StubCommunityDirectory implements CommunityDirectory {
    private final CommunityStubProperties properties;

    StubCommunityDirectory(CommunityStubProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isActive(long communityId) {
        return !properties.unavailableCommunityIds().contains(communityId);
    }

    @Override
    public boolean isMember(long communityId, long userId) {
        return isActive(communityId) && !properties.nonMemberUserIds().contains(userId);
    }
}
