package com.tripledger.app.api;

import com.tripledger.app.service.ConfigConflictException;
import com.tripledger.app.service.EvidenceConflictException;
import com.tripledger.app.service.InvalidRequestException;
import com.tripledger.app.service.TripNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every error that comes out of a controller passes through here and leaves as problem+json
 * with a stable "code" and the request's "traceId".
 *
 * Three groups:
 *   1. our own exceptions (trip not found, conflicts, bad input) -> clear 4xx with a helpful detail
 *   2. Spring's own (validation, broken JSON, missing header, wrong method...) -> handled by the
 *      parent class; we only add code + traceId to what it builds
 *   3. anything unexpected -> 500 with a deliberately boring message. The real exception goes to
 *      the log under the traceId; the client never sees a stack trace, SQL or a class name.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Problems problems;

    public ApiExceptionHandler(Problems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ProblemDetail tripNotFound(TripNotFoundException e) {
        return problems.of(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip not found", e.getMessage());
    }

    @ExceptionHandler(EvidenceConflictException.class)
    public ProblemDetail evidenceConflict(EvidenceConflictException e) {
        return problems.of(HttpStatus.CONFLICT, "EVIDENCE_CONFLICT", "Upload conflicts with earlier data", e.getMessage());
    }

    @ExceptionHandler(ConfigConflictException.class)
    public ProblemDetail configConflict(ConfigConflictException e) {
        return problems.of(HttpStatus.CONFLICT, "CONFIG_CONFLICT", "Settings changed at the same time", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail invalid(InvalidRequestException e) {
        return problems.of(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error, traceId={}", TraceIds.current(), e);
        return problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong",
                "Something went wrong on our side. Please quote the traceId if you report this.");
    }

    // the parent class funnels all of Spring's own errors through here - we just stamp them
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        Object problemBody = body;
        if (problemBody == null && ex instanceof ErrorResponse errorResponse) {
            problemBody = errorResponse.getBody();
        }
        if (problemBody instanceof ProblemDetail problem) {
            problem.setProperty("code", codeFor(ex, statusCode));
            problem.setProperty("traceId", TraceIds.current());
        }
        return super.handleExceptionInternal(ex, problemBody, headers, statusCode, request);
    }

    private static String codeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return "VALIDATION_FAILED";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "MALFORMED_BODY";
        }
        if (ex instanceof MissingRequestHeaderException) {
            return "MISSING_HEADER";
        }
        if (ex instanceof TypeMismatchException) {
            return "INVALID_PARAMETER";
        }
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return "NOT_FOUND";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return "METHOD_NOT_ALLOWED";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return "UNSUPPORTED_MEDIA_TYPE";
        }
        return "HTTP_" + status.value();
    }
}
