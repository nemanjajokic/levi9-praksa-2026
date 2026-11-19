package com.levi9.reddit.auth.api;

import com.levi9.reddit.auth.core.exception.ErrorType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the same RFC 9457 problem detail {@link GlobalExceptionHandler} produces, for answers given by security
 * filters and handlers, which run before any controller and so never reach that handler.
 */
final class ProblemResponses {
    private ProblemResponses() {
    }

    static void write(HttpServletResponse response, JsonMapper json, HttpStatus status, ErrorType errorType,
                      String detail) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("errorType", errorType.name());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
