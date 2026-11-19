package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.contracts.ChangePasswordRequest;
import com.levi9.reddit.auth.contracts.DeleteUserRequest;
import com.levi9.reddit.auth.contracts.UpdateUserRequest;
import com.levi9.reddit.auth.contracts.UserProfileResponse;
import com.levi9.reddit.auth.core.exception.ErrorType;
import com.levi9.reddit.auth.core.exception.UnauthorizedException;
import com.levi9.reddit.auth.infrastructure.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(authenticatedUserId(jwt));
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateProfile(authenticatedUserId(jwt), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteUserRequest request) {
        userService.delete(authenticatedUserId(jwt), request.password());
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authenticatedUserId(jwt), request.currentPassword(), request.newPassword());
    }

    private static long authenticatedUserId(Jwt jwt) {
        if (jwt != null) {
            try {
                long userId = Long.parseLong(jwt.getSubject());
                if (userId > 0) {
                    return userId;
                }
            } catch (NumberFormatException ignored) {
                // A subject that cannot identify a user is invalid.
            }
        }
        throw new UnauthorizedException(ErrorType.INVALID_CREDENTIALS, "Invalid user identity");
    }
}
