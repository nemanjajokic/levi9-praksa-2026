package com.levi9.reddit.auth.core.service;

import com.levi9.reddit.auth.core.exception.ConflictException;
import com.levi9.reddit.auth.core.exception.DomainException;
import com.levi9.reddit.auth.core.exception.ErrorType;
import com.levi9.reddit.auth.core.exception.ForbiddenException;
import com.levi9.reddit.auth.core.exception.NotFoundException;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.core.model.UserRole;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserBanServiceTest {
    private static final Long ADMIN_ID = 7L;

    private final UserRepository users = mock(UserRepository.class);
    private final UserBanService service = new UserBanService(users);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("reader", "reader@example.test", "hash", "First", "Last");
        when(users.findByUsernameIgnoringCase("reader")).thenReturn(Optional.of(user));
        when(users.findByUsernameIgnoringCase("nobody")).thenReturn(Optional.empty());
    }

    @Test
    void banSetsAllBanFieldsAndStripsTheReason() {
        Instant before = Instant.now();

        service.ban("reader", ADMIN_ID, "  spam  ");

        assertThat(user.isBanned()).isTrue();
        assertThat(user.getBanReason()).isEqualTo("spam");
        assertThat(user.getBannedBy()).isEqualTo(ADMIN_ID);
        assertThat(user.getBannedAt()).isBetween(before, Instant.now());
    }

    @Test
    void unbanClearsAllBanFields() {
        user.ban(ADMIN_ID, "spam", Instant.now());

        service.unban("reader");

        assertThat(user.isBanned()).isFalse();
        assertThat(user.getBanReason()).isNull();
        assertThat(user.getBannedBy()).isNull();
        assertThat(user.getBannedAt()).isNull();
    }

    @Test
    void unknownUserIsNotFound() {
        assertErrorType(() -> service.ban("nobody", ADMIN_ID, "spam"), NotFoundException.class, ErrorType.USER_NOT_FOUND);
        assertErrorType(() -> service.unban("nobody"), NotFoundException.class, ErrorType.USER_NOT_FOUND);
    }

    @Test
    void deletedUserIsNotFound() {
        user.delete();

        assertErrorType(() -> service.ban("reader", ADMIN_ID, "spam"), NotFoundException.class, ErrorType.USER_NOT_FOUND);
        assertErrorType(() -> service.unban("reader"), NotFoundException.class, ErrorType.USER_NOT_FOUND);
        assertThat(user.isBanned()).isFalse();
    }

    @Test
    void adminCannotBeBanned() {
        ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);

        assertErrorType(() -> service.ban("reader", ADMIN_ID, "spam"), ForbiddenException.class, ErrorType.ADMIN_BAN_FORBIDDEN);
        assertThat(user.isBanned()).isFalse();
    }

    @Test
    void banningABannedUserConflictsAndKeepsTheOriginalBan() {
        Instant originalAt = Instant.parse("2026-11-01T10:00:00Z");
        user.ban(1L, "original", originalAt);

        assertErrorType(() -> service.ban("reader", ADMIN_ID, "new"), ConflictException.class, ErrorType.USER_ALREADY_BANNED);
        assertThat(user.getBanReason()).isEqualTo("original");
        assertThat(user.getBannedBy()).isEqualTo(1L);
        assertThat(user.getBannedAt()).isEqualTo(originalAt);
    }

    @Test
    void unbanningAUserWhoIsNotBannedConflicts() {
        assertErrorType(() -> service.unban("reader"), ConflictException.class, ErrorType.USER_NOT_BANNED);
    }

    @Test
    void usernameLookupIgnoresCaseThroughTheRepository() {
        when(users.findByUsernameIgnoringCase(anyString())).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoringCase("READER")).thenReturn(Optional.of(user));

        service.ban("READER", ADMIN_ID, "spam");

        assertThat(user.isBanned()).isTrue();
    }

    private static void assertErrorType(Runnable call, Class<? extends DomainException> type, ErrorType errorType) {
        assertThatThrownBy(call::run)
                .isInstanceOf(type)
                .extracting(e -> ((DomainException) e).getErrorType())
                .isEqualTo(errorType);
    }
}
