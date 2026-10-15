package com.devtrace.exception;

public class IncidentAlreadyResolvedException extends RuntimeException {
    public IncidentAlreadyResolvedException(String message) {
        super(message);
    }
}
