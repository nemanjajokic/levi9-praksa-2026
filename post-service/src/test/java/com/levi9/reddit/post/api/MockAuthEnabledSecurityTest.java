package com.levi9.reddit.post.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The mock filter wired through the real {@link SecurityConfig}, as docker-compose runs it locally. */
@WebMvcTest(WhoAmIController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.mock-auth.enabled=true")
class MockAuthEnabledSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedRoute_withoutHeaders_seesDefaultCallerAsJwtPrincipal() throws Exception {
        mockMvc.perform(get(WhoAmIController.PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("mockuser"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void protectedRoute_withHeaders_seesThatCaller() throws Exception {
        mockMvc.perform(get(WhoAmIController.PATH)
                        .header(MockAuthenticationFilter.USER_ID_HEADER, "42")
                        .header(MockAuthenticationFilter.USERNAME_HEADER, "marko")
                        .header(MockAuthenticationFilter.ROLES_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("marko"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void protectedRoute_withInvalidUserId_returnsBadRequest() throws Exception {
        mockMvc.perform(get(WhoAmIController.PATH).header(MockAuthenticationFilter.USER_ID_HEADER, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("X-Mock-User-Id must be a positive number"));
    }

    @Test
    void testJwt_winsOverMockCaller() throws Exception {
        mockMvc.perform(get(WhoAmIController.PATH)
                        .with(jwt().jwt(j -> j.subject("7").claim("username", "ana").claim("roles", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("ana"));
    }
}
