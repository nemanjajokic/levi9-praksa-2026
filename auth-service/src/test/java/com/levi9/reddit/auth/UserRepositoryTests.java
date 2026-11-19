package com.levi9.reddit.auth;

import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Case-insensitive lookups against the real schema. Not @Transactional: every save commits, so the
 * lookups read what the database actually holds, including its unique indexes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserRepositoryTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("TRUNCATE oauth2_authorization, users RESTART IDENTITY CASCADE");
    }

    @Test
    void findsAUserByEmailIgnoringCase() {
        users.save(user("Marko", "Marko@Example.test"));

        assertThat(users.findByEmailIgnoringCase("marko@example.TEST"))
                .hasValueSatisfying(found -> assertThat(found.getUsername()).isEqualTo("Marko"));
        assertThat(users.findByEmailIgnoringCase("ana@example.test")).isEmpty();
    }

    @Test
    void findsAUserByUsernameIgnoringCase() {
        users.save(user("Marko", "marko@example.test"));

        assertThat(users.findByUsernameIgnoringCase("mARKO"))
                .hasValueSatisfying(found -> assertThat(found.getUsername()).isEqualTo("Marko"));
    }

    @Test
    void findsNothingForAnUnknownUsername() {
        users.save(user("Marko", "marko@example.test"));

        assertThat(users.findByUsernameIgnoringCase("ana")).isEmpty();
    }

    @Test
    void reportsATakenUsernameIgnoringCase() {
        users.save(user("Marko", "marko@example.test"));

        assertThat(users.existsByUsernameIgnoringCase("MARKO")).isTrue();
        assertThat(users.existsByUsernameIgnoringCase("ana")).isFalse();
    }

    @Test
    void reportsATakenEmailIgnoringCase() {
        users.save(user("Marko", "Marko@Example.test"));

        assertThat(users.existsByEmailIgnoringCase("marko@example.TEST")).isTrue();
        assertThat(users.existsByEmailIgnoringCase("ana@example.test")).isFalse();
    }

    @Test
    void keepsTheUsernameAndEmailOfADeletedUserTaken() {
        User deleted = user("Marko", "marko@example.test");
        deleted.delete();
        users.save(deleted);

        assertThat(users.existsByUsernameIgnoringCase("marko")).isTrue();
        assertThat(users.existsByEmailIgnoringCase("marko@example.test")).isTrue();
        assertThat(users.findByUsernameIgnoringCase("marko"))
                .hasValueSatisfying(found -> assertThat(found.isDeleted()).isTrue());
        assertThat(users.findByEmailIgnoringCase("marko@example.test"))
                .hasValueSatisfying(found -> assertThat(found.isDeleted()).isTrue());
    }

    @Test
    void databaseRejectsAUsernameThatDiffersOnlyInCase() {
        // The backstop for two registrations racing past the "is it taken?" check at the same moment.
        users.save(user("Marko", "marko@example.test"));

        assertThatThrownBy(() -> users.saveAndFlush(user("marko", "other@example.test")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static User user(String username, String email) {
        return new User(username, email, "hash", "First", "Last");
    }
}
