package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.core.exception.ErrorType;
import com.levi9.reddit.auth.core.exception.ForbiddenException;
import com.levi9.reddit.auth.core.exception.NotFoundException;
import com.levi9.reddit.auth.core.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import com.levi9.reddit.auth.infrastructure.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @AfterEach
    void clearAuthentication() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void changesPasswordForAuthenticatedUser() throws Exception {
        authenticateAs("42");
        mvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old secret","newPassword":"new secret phrase"}
                                """))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(42L, "old secret", "new secret phrase");
    }

    @Test
    void rejectsPasswordChangeWithoutAuthenticatedUserId() throws Exception {
        mvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old secret","newPassword":"new secret phrase"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(userService);
    }

    @Test
    void rejectsPasswordChangeWithInvalidJwtSubject() throws Exception {
        authenticateAs("reader");
        mvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old secret","newPassword":"new secret phrase"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(userService);
    }

    @Test
    void rejectsNewPasswordBelowMinimumLength() throws Exception {
        authenticateAs("42");
        mvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old secret","newPassword":"12345678901"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("New password must be at least 12 characters"));

        verifyNoInteractions(userService);
    }

    @Test
    void acceptsNewPasswordAtMinimumLengthWithShortCurrentPassword() throws Exception {
        authenticateAs("42");
        mvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old secret","newPassword":"123456789012"}
                                """))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(42L, "old secret", "123456789012");
    }

    @Test
    void rejectsNewPasswordBeyondBcryptByteLimit() throws Exception {
        authenticateAs("42");
        mvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old secret","newPassword":"%s"}
                                """.formatted("é".repeat(37))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("New password must be at most 72 UTF-8 bytes"));

        verifyNoInteractions(userService);
    }

    private static void authenticateAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(subject).build();
        TestSecurityContextHolder.setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void deletesUser() throws Exception {
        authenticateAs("42");
        mvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret\"}"))
                .andExpect(status().isNoContent());

        verify(userService).delete(42L, "secret");
    }

    @Test
    void rejectsInvalidRequestBodies() throws Exception {
        for (String body : new String[] {"{}", "{\"password\":\"   \"}", "{invalid"}) {
            mvc.perform(delete("/api/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void returnsUnauthorizedForWrongPassword() throws Exception {
        authenticateAs("42");
        doThrow(new UnauthorizedException(ErrorType.INVALID_CREDENTIALS, "Invalid password"))
                .when(userService).delete(42L, "wrong");

        mvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("INVALID_CREDENTIALS"));
    }

    @Test
    void returnsProblemDetailsForMissingUserAndForbiddenDeletion() throws Exception {
        authenticateAs("42");
        doThrow(new NotFoundException(ErrorType.USER_NOT_FOUND, "User not found"))
                .when(userService).delete(42L, "missing");
        doThrow(new ForbiddenException(ErrorType.ACCOUNT_DELETION_FORBIDDEN, "This account cannot be deleted"))
                .when(userService).delete(42L, "forbidden");

        mvc.perform(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").value("USER_NOT_FOUND"));
        mvc.perform(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"forbidden\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorType").value("ACCOUNT_DELETION_FORBIDDEN"));
    }
}
