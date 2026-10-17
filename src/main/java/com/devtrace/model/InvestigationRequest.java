package com.devtrace.model;

public record InvestigationRequest(
        String description,
        String serviceHint,
        String errorHint,
        Long startedAt,
        String incidentId
) {
    public InvestigationRequest(String description) {
        this(description, null, null, null, null);
    }

    public InvestigationRequest(String description, String serviceHint, String errorHint) {
        this(description, serviceHint, errorHint, null, null);
    }

    public InvestigationRequest(String description, String serviceHint, String errorHint, Long startedAt) {
        this(description, serviceHint, errorHint, startedAt, null);
    }
}
