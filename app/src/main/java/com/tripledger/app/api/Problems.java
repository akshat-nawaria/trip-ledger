package com.tripledger.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Builds every error the API sends, so they all look the same (RFC 7807 problem+json):
 *
 *   { "type": "about:blank", "title": "Trip not found", "status": 404,
 *     "detail": "No trip 'T-1' found", "instance": "/v1/trips/T-1/distance",
 *     "code": "TRIP_NOT_FOUND", "traceId": "3f9a1c0b7d2e4a61" }
 *
 * "code" is for programs (stable, never reworded), "title"/"detail" are for people.
 * What never goes in: stack traces, SQL, class names, other people's data.
 */
@Component
public class Problems {

    private final ObjectMapper json;

    public Problems(ObjectMapper json) {
        this.json = json;
    }

    public ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("traceId", TraceIds.current());
        return problem;
    }

    // 401 and 403 happen inside the security filters, before any controller runs, so the
    // @RestControllerAdvice never sees them. These two write the same JSON straight to the response.

    public void unauthorized(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, 401, "UNAUTHORIZED", "Not signed in",
                "Send a valid, unexpired bearer token in the Authorization header.");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(request, response, 403, "FORBIDDEN", "Not allowed", "Your role is not allowed to do this.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status, String code,
            String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code);
        body.put("traceId", TraceIds.current());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }
}
