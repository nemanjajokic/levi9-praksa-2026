package com.levi9.reddit.auth;

import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.core.model.UserRole;
import com.levi9.reddit.auth.infrastructure.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthServiceApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void persistsAndReloadsServiceModels() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        User admin = userRepository.save(new User("admin", "admin@example.test", "hash", "Admin", "User"));
        User user = userRepository.save(new User("reader", "reader@example.test", "hash", "First", "Last"));
        user.ban(admin.getId(), "reason", now);

        entityManager.flush();
        entityManager.clear();

        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertThat(stored.getUsername()).isEqualTo("reader");
        assertThat(stored.getEmail()).isEqualTo("reader@example.test");
        assertThat(stored.getPasswordHash()).isEqualTo("hash");
        assertThat(stored.getFirstName()).isEqualTo("First");
        assertThat(stored.getLastName()).isEqualTo("Last");
        assertThat(stored.getRole()).isEqualTo(UserRole.USER);
        assertThat(stored.isDeleted()).isFalse();
        assertThat(stored.isBanned()).isTrue();
        assertThat(stored.getBanReason()).isEqualTo("reason");
        assertThat(stored.getBannedAt()).isEqualTo(now);
        assertThat(stored.getBannedBy()).isEqualTo(admin.getId());

        stored.unban();
        entityManager.flush();
        entityManager.clear();
        User unbanned = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unbanned.isBanned()).isFalse();
        assertThat(unbanned.getBannedBy()).isNull();
    }
    @Test
    @Transactional
    void deletionPersistsAndRevokesOnlyTheUsersAuthorizations() {
        User user = userRepository.save(new User("delete-me", "delete@example.test", passwordEncoder.encode("secret"), "First", "Last"));
        User other = userRepository.save(new User("other", "other@example.test", "hash", "Other", "User"));
        entityManager.flush();
        insertAuthorization("a1", user.getId());
        insertAuthorization("a2", user.getId());
        insertAuthorization("b1", other.getId());

        userService.delete(user.getId(), "secret");
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(authorizationIds()).containsExactly("b1");
    }

    @Test
    @Transactional
    void passwordChangePersistsAndRevokesOnlyTheUsersAuthorizations() {
        User user = userRepository.save(new User("reader", "reader@example.test",
                passwordEncoder.encode("old secret"), "First", "Last"));
        User other = userRepository.save(new User("other", "other@example.test", "hash", "Other", "User"));
        entityManager.flush();
        entityManager.clear();
        insertAuthorization("a1", user.getId());
        insertAuthorization("b1", other.getId());

        userService.changePassword(user.getId(), "old secret", "new secret");
        entityManager.flush();
        entityManager.clear();

        String hash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches("new secret", hash)).isTrue();
        assertThat(passwordEncoder.matches("old secret", hash)).isFalse();
        assertThat(authorizationIds()).containsExactly("b1");
    }

    /** The minimum Spring's schema requires. The principal name is the user id, as the login stores it. */
    private void insertAuthorization(String id, long userId) {
        jdbc.update("""
                INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type)
                VALUES (?, 'client', ?, 'authorization_code')""", id, String.valueOf(userId));
    }

    private List<String> authorizationIds() {
        return jdbc.queryForList("SELECT id FROM oauth2_authorization ORDER BY id", String.class);
    }
}
