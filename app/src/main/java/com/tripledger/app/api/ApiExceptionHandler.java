package com.tripledger.app.api;

import com.tripledger.app.service.EvidenceConflictException;
import com.tripledger.app.service.InvalidRequestException;
import com.tripledger.app.service.TripNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns our own exceptions into RFC 7807 problem+json responses.
 *
 * Validation errors, bad JSON, a missing header etc. are already handled by Spring itself
 * (spring.mvc.problemdetails.enabled in application.yml), in the same format.
 * S6 adds an error code and trace id to every problem, and a catch-all for the unexpected.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TripNotFoundException.class)
    public ProblemDetail tripNotFound(TripNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Trip not found", e.getMessage());
    }

    @ExceptionHandler(EvidenceConflictException.class)
    public ProblemDetail conflict(EvidenceConflictException e) {
        return problem(HttpStatus.CONFLICT, "Upload conflicts with earlier data", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail invalid(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
