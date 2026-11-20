package com.levi9.reddit.post.infrastructure;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for {@link StubCommunityDirectory}. LOCAL ONLY.
 *
 * @param enabled                 registers the stub; without it (and without a real client) the app does not start
 * @param unavailableCommunityIds communities that behave as missing or shut down; every other id is active
 * @param nonMemberUserIds        users that are members of no community; every other user is a member of all
 */
@ConfigurationProperties(prefix = "app.community-stub")
record CommunityStubProperties(boolean enabled, Set<Long> unavailableCommunityIds, Set<Long> nonMemberUserIds) {

    CommunityStubProperties {
        unavailableCommunityIds = unavailableCommunityIds == null ? Set.of() : Set.copyOf(unavailableCommunityIds);
        nonMemberUserIds = nonMemberUserIds == null ? Set.of() : Set.copyOf(nonMemberUserIds);
    }
}
