package com.levi9.reddit.post.infrastructure;

import com.levi9.reddit.post.core.CommunityDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link StubCommunityDirectory} only when {@code app.community-stub.enabled=true}. Off by default, so
 * production can never silently treat everyone as a member: without the stub or a real client there is no
 * {@link CommunityDirectory} bean and the app fails to start.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.community-stub.enabled", havingValue = "true")
@EnableConfigurationProperties(CommunityStubProperties.class)
class CommunityStubConfig {
    private static final Logger log = LoggerFactory.getLogger(CommunityStubConfig.class);

    @Bean
    CommunityDirectory stubCommunityDirectory(CommunityStubProperties properties) {
        log.warn("COMMUNITY STUB ENABLED: communities and memberships are faked, not read from community-service "
                + "(unavailable communities: {}, non-member users: {}). Never enable app.community-stub in production.",
                properties.unavailableCommunityIds(), properties.nonMemberUserIds());
        return new StubCommunityDirectory(properties);
    }
}
