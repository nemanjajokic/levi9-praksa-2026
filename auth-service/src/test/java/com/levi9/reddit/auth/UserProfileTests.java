package com.levi9.reddit.auth;

import com.levi9.reddit.auth.core.model.User;
import com.levi9.reddit.auth.infrastructure.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileTests {
    private static final String PROFILE = "/api/users/me";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    private User other;
    private User user;
    private RequestPostProcessor caller;

    @BeforeEach
    void setUp() {
        other = users.save(new User("profile-other", "other@example.test", "hash", "Other", "User"));
        user = users.save(new User("profile-reader", "reader@example.test", passwords.encode("secret"), "First", "Last"));
        caller = jwt().jwt(token -> token.subject(user.getId().toString()));
    }

    @AfterEach
    void cleanUp() {
        users.deleteAllById(List.of(user.getId(), other.getId()));
    }

    @Test
    void returnsTheSavedProfileForTheTokenSubject() throws Exception {
        update(caller, """
                {"firstName":" Ana ", "id":1, "username":"someone-else"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value("profile-reader"))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Last"))
                .andExpect(jsonPath("$.email").value("reader@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get(PROFILE).with(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"));
    }

    @Test
    void updatesEmailAndNameWithTheCurrentPassword() throws Exception {
        update(caller, """
                {"email":"reader-new@example.test","lastName":"New","currentPassword":"secret"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("reader-new@example.test"))
                .andExpect(jsonPath("$.lastName").value("New"))
                .andExpect(jsonPath("$.currentPassword").doesNotExist());
    }

    @Test
    void requiresTheCurrentPasswordOnlyWhenEmailChanges() throws Exception {
        update(caller, """
                {"email":"reader@example.test"}
                """).andExpect(status().isOk());
        update(caller, """
                {"email":"reader-new@example.test"}
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorType").value("INVALID_CREDENTIALS"));
        update(caller, """
                {"email":"reader-new@example.test","currentPassword":"wrong"}
                """).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCaseInsensitiveEmailConflictsAndRollsBackNameChanges() throws Exception {
        update(caller, """
                {"email":"OTHER@example.test","firstName":"Changed","currentPassword":"secret"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorType").value("EMAIL_TAKEN"));

        mvc.perform(get(PROFILE).with(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("First"))
                .andExpect(jsonPath("$.email").value("reader@example.test"));
    }

    @Test
    void rejectsInvalidProfileFields() throws Exception {
        update(caller, "{}").andExpect(status().isBadRequest());
        update(caller, "{\"firstName\":null}").andExpect(status().isBadRequest());
        update(caller, "{\"lastName\":\" \"}").andExpect(status().isBadRequest());
        update(caller, "{\"email\":\"invalid\"}").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingOrInvalidUserIdentity() throws Exception {
        mvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        update(jwt().jwt(token -> token.subject("invalid")), "{\"firstName\":\"Ana\"}")
                .andExpect(status().isUnauthorized());
    }

    private ResultActions update(RequestPostProcessor authentication, String json) throws Exception {
        return mvc.perform(patch(PROFILE)
                .with(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
