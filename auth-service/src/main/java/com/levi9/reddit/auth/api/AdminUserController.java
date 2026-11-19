package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.contracts.BanUserRequest;
import com.levi9.reddit.auth.core.service.UserBanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// TODO(auth): only works once auth-service is a resource server for /api/users/** (see SecurityConfig).
// Check that the OAuth access token still carries the user id in sub and the roles claim.
@RestController
@RequestMapping("/api/users/{username}/ban")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserBanService bans;

    public AdminUserController(UserBanService bans) {
        this.bans = bans;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(@PathVariable String username,
                    @Valid @RequestBody BanUserRequest request,
                    @AuthenticationPrincipal Jwt jwt) {
        bans.ban(username, Long.valueOf(jwt.getSubject()), request.reason());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unban(@PathVariable String username) {
        bans.unban(username);
    }
}
