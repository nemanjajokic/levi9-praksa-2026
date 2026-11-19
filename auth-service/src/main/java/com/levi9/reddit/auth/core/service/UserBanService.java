package com.levi9.reddit.auth.core.service;

import com.levi9.reddit.auth.core.exception.ConflictException;
import com.levi9.reddit.auth.core.exception.ErrorType;
import com.levi9.reddit.auth.core.exception.ForbiddenException;
import com.levi9.reddit.auth.core.exception.NotFoundException;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.core.model.UserRole;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBanService {
    private final UserRepository users;

    public UserBanService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public void ban(String username, Long adminId, String reason) {
        User user = findActive(username);
        if (user.getRole() == UserRole.ADMIN) {
            throw new ForbiddenException(ErrorType.ADMIN_BAN_FORBIDDEN, "Admins cannot be banned");
        }
        if (user.isBanned()) {
            throw new ConflictException(ErrorType.USER_ALREADY_BANNED, "User is already banned");
        }
        user.ban(adminId, reason.strip(), Instant.now());
        // TODO(auth): revoke the user's sessions here once OAuth is in (remove their authorizations),
        // and make the refresh grant reject banned users. Until then a ban takes effect when tokens expire.
    }

    @Transactional
    public void unban(String username) {
        User user = findActive(username);
        if (!user.isBanned()) {
            throw new ConflictException(ErrorType.USER_NOT_BANNED, "User is not banned");
        }
        user.unban();
    }

    private User findActive(String username) {
        return users.findByUsernameIgnoringCase(username)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorType.USER_NOT_FOUND, "User not found"));
    }
}
