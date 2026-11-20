package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.contracts.AuthResponse;
import com.levi9.reddit.auth.contracts.LoginRequest;
import com.levi9.reddit.auth.contracts.RefreshRequest;
import com.levi9.reddit.auth.contracts.RegisterRequest;
import com.levi9.reddit.auth.core.exception.ConflictException;
import com.levi9.reddit.auth.core.exception.ErrorType;
import com.levi9.reddit.auth.core.exception.UnauthorizedException;
import com.levi9.reddit.auth.core.exception.UserBannedException;
import com.levi9.reddit.auth.core.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of register, login and refresh: status codes, the response and problem-detail shapes, and
 * that the routes are reachable without a token. Runs through the real security filter chain. The rules behind
 * each answer are tested against Postgres in the AuthService*Tests; here the service only produces the outcome.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {
    private static final AuthResponse TOKENS = new AuthResponse("access.jwt", "refresh-token", 900);
    private static final String REGISTER_BODY = """
            {"username":"marko","email":"marko@example.test","password":"correct horse battery",
             "firstName":"Marko","lastName":"Markovic"}""";
    private static final String LOGIN_BODY = """
            {"email":"marko@example.test","password":"correct horse battery"}""";
    private static final String REFRESH_BODY = """
            {"refreshToken":"refresh-token"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    // --- Success, with no token sent ---

    @Test
    void registerAnswers201WithTheProfileLocationAndTheTokens() throws Exception {
        when(auth.register(any(RegisterRequest.class))).thenReturn(TOKENS);

        postJson("/api/auth/register", REGISTER_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/marko"))
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginAnswers200WithTheTokens() throws Exception {
        when(auth.login(new LoginRequest("marko@example.test", "correct horse battery"))).thenReturn(TOKENS);

        postJson("/api/auth/login", LOGIN_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void refreshAnswers200WithTheNewTokens() throws Exception {
        when(auth.refresh(new RefreshRequest("refresh-token"))).thenReturn(TOKENS);

        postJson("/api/auth/refresh", REFRESH_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    // --- Only those three are open ---

    @Test
    void otherRoutesAndMethodsStillNeedAToken() throws Exception {
        // No login mechanism is wired into this service yet, so Spring answers 403; branch 3 makes it 401.
        mvc.perform(get("/api/auth/login")).andExpect(status().isForbidden());
        postJson("/api/auth/logout", "{}").andExpect(status().isForbidden());
    }

    // --- Bad input: 400 before the service runs ---

    @ParameterizedTest
    @ValueSource(strings = {"m", "me", "Me", "ME"})
    void usernamesShorterThanThreeCharactersAreRejectedBeforeRegistration(String username) throws Exception {
        postJson("/api/auth/register", REGISTER_BODY.replace("\"marko\"", "\"" + username + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("username")));

        verifyNoInteractions(auth);
    }

    @Test
    void aThreeCharacterUsernameCanBeRegistered() throws Exception {
        when(auth.register(any(RegisterRequest.class))).thenReturn(TOKENS);

        postJson("/api/auth/register", REGISTER_BODY.replace("\"marko\"", "\"mem\""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/mem"));
    }

    @Test
    void anInvalidBodyIs400NamingTheFieldButNeverEchoingThePassword() throws Exception {
        postJson("/api/auth/register", """
                {"username":"marko","email":"marko@example.test","password":"too-short",
                 "firstName":"Marko","lastName":"Markovic"}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
                .andExpect(content().string(not(containsString("too-short"))));

        verifyNoInteractions(auth);
    }

    @Test
    void missingOrMalformedBodiesAre400() throws Exception {
        postJson("/api/auth/login", "{}").andExpect(status().isBadRequest());
        postJson("/api/auth/refresh", "{\"refreshToken\":\"\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/login", "{not json").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login")).andExpect(status().is4xxClientError());

        verifyNoInteractions(auth);
    }

    // --- Domain failures become problem details ---

    @Test
    void wrongCredentialsAre401() throws Exception {
        doThrow(new UnauthorizedException(ErrorType.INVALID_CREDENTIALS, "Invalid email or password"))
                .when(auth).login(any());

        expectProblem(postJson("/api/auth/login", LOGIN_BODY), 401, "INVALID_CREDENTIALS")
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void aBannedUserGets403WithTheReason() throws Exception {
        doThrow(new UserBannedException("Spam"))
                .when(auth).login(any());

        expectProblem(postJson("/api/auth/login", LOGIN_BODY), 403, "USER_BANNED")
                .andExpect(jsonPath("$.banReason").value("Spam"));
    }

    @Test
    void takenUsernameAndFailedRegistrationAre409() throws Exception {
        doThrow(new ConflictException(ErrorType.USERNAME_TAKEN, "Username is already taken"))
                .when(auth).register(any());
        expectProblem(postJson("/api/auth/register", REGISTER_BODY), 409, "USERNAME_TAKEN");

        doThrow(new ConflictException(ErrorType.REGISTRATION_FAILED, "Registration failed, please check your details"))
                .when(auth).register(any());
        expectProblem(postJson("/api/auth/register", REGISTER_BODY), 409, "REGISTRATION_FAILED")
                .andExpect(content().string(not(containsString("marko@example.test"))));
    }

    @Test
    void anUnusableRefreshTokenIs401AndARaceIs409() throws Exception {
        doThrow(new UnauthorizedException(ErrorType.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired"))
                .when(auth).refresh(any());
        expectProblem(postJson("/api/auth/refresh", REFRESH_BODY), 401, "INVALID_REFRESH_TOKEN");

        doThrow(new ConflictException(ErrorType.REFRESH_IN_PROGRESS, "This refresh token was just used by another request"))
                .when(auth).refresh(any());
        expectProblem(postJson("/api/auth/refresh", REFRESH_BODY), 409, "REFRESH_IN_PROGRESS");
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String errorType) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.errorType").value(errorType));
    }
}
