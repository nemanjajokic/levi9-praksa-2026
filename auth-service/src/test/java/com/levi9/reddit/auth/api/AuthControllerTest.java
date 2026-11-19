package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.contracts.RegisterRequest;
import com.levi9.reddit.auth.core.exception.ConflictException;
import com.levi9.reddit.auth.core.exception.ErrorType;
import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.core.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of register: status codes, the response and problem-detail shapes, and that the route is
 * reachable without a token. Runs through the real security filter chain. The rules behind
 * each answer are tested against Postgres in the AuthService*Tests; here the service only produces the outcome.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {
    private static final String REGISTER_BODY = """
            {"username":"marko","email":"marko@example.test","password":"correct horse battery",
             "firstName":"Marko","lastName":"Markovic"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    // --- Success, with no token sent ---

    @Test
    void registerAnswers201WithTheProfileLocationAndTheUserButNoTokens() throws Exception {
        User marko = new User("marko", "marko@example.test", "hash", "Marko", "Markovic");
        ReflectionTestUtils.setField(marko, "id", 42L);
        when(auth.register(any(RegisterRequest.class))).thenReturn(marko);

        postJson("/api/auth/register", REGISTER_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/marko"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("marko"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // --- Only register is open, and only with the CSRF token ---

    @Test
    void otherRoutesAndMethodsNeedALogin() throws Exception {
        mvc.perform(get("/api/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerWithoutTheCsrfTokenIs403() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auth);
    }

    // --- Bad input: 400 before the service runs ---

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
        postJson("/api/auth/register", "{}").andExpect(status().isBadRequest());
        postJson("/api/auth/register", "{not json").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf())).andExpect(status().is4xxClientError());

        verifyNoInteractions(auth);
    }

    // --- Domain failures become problem details ---

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

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String errorType) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.errorType").value(errorType));
    }
}
