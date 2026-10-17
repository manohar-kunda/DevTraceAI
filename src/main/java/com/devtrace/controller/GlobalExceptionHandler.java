package com.devtrace.controller;

import com.devtrace.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 400 Bad Request - Validation Errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed for one or more request fields."
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://devtrace.ai/errors/bad-request"));

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler({InvalidResolutionInputException.class, IllegalArgumentException.class})
    public ProblemDetail handleInvalidInputException(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://devtrace.ai/errors/bad-request"));
        return problem;
    }

    // 404 Not Found
    @ExceptionHandler({IncidentNotFoundException.class, ServiceNotFoundException.class, DeploymentNotFoundException.class})
    public ProblemDetail handleNotFoundException(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        problem.setType(URI.create("https://devtrace.ai/errors/not-found"));
        return problem;
    }

    // 409 Conflict
    @ExceptionHandler({IncidentConflictException.class, ResolutionConflictException.class, IncidentAlreadyResolvedException.class})
    public ProblemDetail handleConflictException(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://devtrace.ai/errors/conflict"));
        return problem;
    }

    // 401 Unauthorized
    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorizedException(UnauthorizedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("https://devtrace.ai/errors/unauthorized"));
        return problem;
    }

    // 503 Service Unavailable (Database connectivity failure)
    @ExceptionHandler({DatabaseUnavailableException.class, JedisConnectionException.class})
    public ProblemDetail handleDatabaseUnavailableException(Exception ex) {
        log.warn("FalkorDB database connection failure: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Database service is temporarily unavailable. Please verify FalkorDB is running and accessible."
        );
        problem.setTitle("Service Unavailable");
        problem.setType(URI.create("https://devtrace.ai/errors/database-unavailable"));
        return problem;
    }

    // 500 Internal Server Error (Unexpected errors, correlation ID logged)
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected server exception [correlationId={}]: {}", correlationId, ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal server error occurred. Reference correlation ID: " + correlationId
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://devtrace.ai/errors/internal-server-error"));
        problem.setProperty("correlationId", correlationId);
        return problem;
    }
}
