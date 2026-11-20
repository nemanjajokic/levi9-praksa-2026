package com.levi9.reddit.post.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.levi9.reddit.post.core.CommunityDirectory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StubCommunityDirectoryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(CommunityStubConfig.class));

    @Test
    void byDefault_everyCommunityIsActiveAndEveryoneIsAMember() {
        var stub = new StubCommunityDirectory(new CommunityStubProperties(true, null, null));

        assertThat(stub.isActive(1)).isTrue();
        assertThat(stub.isMember(1, 42)).isTrue();
    }

    @Test
    void unavailableCommunity_isInactiveAndHasNoMembers() {
        var stub = new StubCommunityDirectory(new CommunityStubProperties(true, Set.of(999L), Set.of()));

        assertThat(stub.isActive(999)).isFalse();
        assertThat(stub.isMember(999, 42)).isFalse();
        assertThat(stub.isActive(1)).isTrue();
    }

    @Test
    void nonMemberUser_isMemberOfNothing() {
        var stub = new StubCommunityDirectory(new CommunityStubProperties(true, Set.of(), Set.of(999L)));

        assertThat(stub.isMember(1, 999)).isFalse();
        assertThat(stub.isMember(2, 999)).isFalse();
        assertThat(stub.isMember(1, 42)).isTrue();
    }

    @Test
    void flagOff_registersNoDirectory() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(CommunityDirectory.class));
        contextRunner.withPropertyValues("app.community-stub.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CommunityDirectory.class));
    }

    @Test
    void flagOn_registersStubWithCommaSeparatedIdsBound() {
        contextRunner.withPropertyValues(
                        "app.community-stub.enabled=true",
                        "app.community-stub.unavailable-community-ids=999,5",
                        "app.community-stub.non-member-user-ids=999")
                .run(context -> {
                    CommunityDirectory directory = context.getBean(CommunityDirectory.class);
                    assertThat(directory).isInstanceOf(StubCommunityDirectory.class);
                    assertThat(directory.isActive(5)).isFalse();
                    assertThat(directory.isActive(999)).isFalse();
                    assertThat(directory.isMember(1, 999)).isFalse();
                    assertThat(directory.isMember(1, 2)).isTrue();
                });
    }
}
