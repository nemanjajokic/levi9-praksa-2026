package com.levi9.reddit.auth.core.service;

import com.levi9.reddit.auth.contracts.RegisterRequest;
import com.levi9.reddit.auth.core.exception.ConflictException;
import com.levi9.reddit.auth.core.exception.ErrorType;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration. Rules: workspace DECISIONS.md (2026-11-18). Errors never reveal whether an email is registered;
 * a taken username may be named, because usernames are public. Login is Spring Security's form login now.
 */
@Service
public class AuthService {
    // Unique indexes from V1__create_auth_schema.sql. They catch the race the checks below can't.
    private static final String USERNAME_INDEX = "ux_users_username_lower";
    private static final String EMAIL_INDEX = "ux_users_email_lower";

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    /**
     * Creates a USER account. It does not log the user in: the frontend logs in right after, which starts
     * the OAuth flow. The username is checked first, so when both are taken the answer is the harmless
     * {@code USERNAME_TAKEN}. The cheap checks run before BCrypt, so a rejected request costs no hashing.
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (users.existsByUsernameIgnoringCase(request.username())) {
            throw usernameTaken();
        }
        if (users.existsByEmailIgnoringCase(request.email())) {
            throw registrationFailed();
        }

        User user = new User(request.username(), request.email(), passwords.encode(request.password()),
                request.firstName(), request.lastName());
        try {
            // Flush now so a duplicate from a simultaneous registration fails here, where we can name it.
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            String index = violatedConstraint(e);
            if (USERNAME_INDEX.equals(index)) {
                throw usernameTaken();
            }
            if (EMAIL_INDEX.equals(index)) {
                throw registrationFailed();
            }
            throw e; // any other constraint is a bug, not a client error
        }
        return user;
    }

    private static ConflictException usernameTaken() {
        return new ConflictException(ErrorType.USERNAME_TAKEN, "Username is already taken");
    }

    private static ConflictException registrationFailed() {
        return new ConflictException(ErrorType.REGISTRATION_FAILED, "Registration failed, please check your details");
    }

    private static String violatedConstraint(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
