package com.levi9.reddit.post.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ProblemResponsesTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/posts/3");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void unauthorized_writesProblemDetailWithoutErrorType() throws Exception {
        ProblemResponses.unauthorized().commence(request, response, new InsufficientAuthenticationException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"title\":\"Unauthorized\"")
                .contains("\"detail\":\"Authentication is required\"")
                .contains("\"instance\":\"/api/posts/3\"")
                .doesNotContain("errorType")
                .doesNotContain("properties");
    }

    @Test
    void forbidden_writesProblemDetailWithoutErrorType() throws Exception {
        ProblemResponses.forbidden().handle(request, response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("\"detail\":\"Access is denied\"")
                .doesNotContain("errorType");
    }
}
