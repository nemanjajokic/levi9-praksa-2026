package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.contracts.RegisterRequest;
import com.levi9.reddit.auth.contracts.UserProfileResponse;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.core.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** Registration. Public (see {@link SecurityConfig}). Login is Spring Security's form login, not a controller. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** {@code 201} with the new user and {@code Location} pointing at their public profile. No tokens. */
    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = auth.register(request);
        // Relative on purpose: behind the gateway, this service's own host name is not what the client sees.
        URI profile = UriComponentsBuilder.fromPath("/api/users/{username}")
                .buildAndExpand(user.getUsername()).encode().toUri();
        return ResponseEntity.created(profile).body(new UserProfileResponse(user.getId(), user.getUsername(),
                user.getEmail(), user.getFirstName(), user.getLastName()));
    }
}
