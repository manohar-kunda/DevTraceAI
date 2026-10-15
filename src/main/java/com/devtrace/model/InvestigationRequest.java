package com.devtrace.model;

public record InvestigationRequest(
        String description,
        String serviceHint,
        String errorHint
) {
    public InvestigationRequest(String description) {
        this(description, null, null);
    }
}
