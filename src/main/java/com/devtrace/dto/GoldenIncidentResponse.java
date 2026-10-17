package com.devtrace.dto;

public record GoldenIncidentResponse(
        boolean demo,
        String incidentId,
        String description,
        String serviceHint,
        String errorHint,
        long startedAt
) {}
