package com.devtrace.model;

public record InvestigationRequest(
        String description,
        String serviceHint,
        String errorHint,
        Long startedAt
) {
    public InvestigationRequest(String description) {
        this(description, null, null, null);
    }

    public InvestigationRequest(String description, String serviceHint, String errorHint) {
        this(description, serviceHint, errorHint, null);
    }
}
