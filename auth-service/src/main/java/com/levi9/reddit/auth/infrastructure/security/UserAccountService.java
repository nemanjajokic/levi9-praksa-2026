package com.levi9.reddit.auth.infrastructure.security;

import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the account for a login. The user types an email, but the returned {@code UserDetails} is named by the
 * user id: that name becomes the principal Spring Authorization Server stores ({@code principal_name}) and the
 * tokens' {@code sub}. So changing an email keeps every session, and revoking by user is one delete.
 * <p>
 * A deleted account is "not found". A banned one is locked; the login failure handler gives both the same
 * {@code 401} as a wrong password, so a login never reveals whether an email is registered.
 */
@Service
public class UserAccountService implements UserDetailsService {
    private final UserRepository users;

    public UserAccountService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmailIgnoringCase(email)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new UsernameNotFoundException("No active account for this email"));
        return org.springframework.security.core.userdetails.User.withUsername(String.valueOf(user.getId()))
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .accountLocked(user.isBanned())
                .build();
    }
}
