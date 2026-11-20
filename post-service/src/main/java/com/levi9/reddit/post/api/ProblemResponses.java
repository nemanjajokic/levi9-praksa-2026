package com.levi9.reddit.post.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes problem details from code that runs before any controller (security filters), where
 * {@link ApiExceptionHandler} can't help. Same shape as the handler, without {@code errorType}: these errors come
 * from the framework (see docs/error-convention.md and security-convention.md, "Errors from the security layer").
 */
final class ProblemResponses {
    // The mixin flattens custom properties and leaves out empty fields, as Spring MVC does for ProblemDetail.
    private static final JsonMapper JSON = JsonMapper.builder()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .build();

    private ProblemResponses() {}

    /** 401: no caller, or the token is missing, expired or invalid. */
    static AuthenticationEntryPoint unauthorized() {
        return (request, response, failure) ->
                write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    /** 403: the caller is known but a URL or method-security rule refused them. */
    static AccessDeniedHandler forbidden() {
        return (request, response, failure) ->
                write(request, response, HttpStatus.FORBIDDEN, "Access is denied");
    }

    static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JSON.writeValue(response.getOutputStream(), problem);
    }
}
