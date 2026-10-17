package com.devtrace.dto;

public record CreateIncidentResponse(
        String incidentId,
        String title,
        String status,
        long reportedAt,
        String severity,
        boolean created
) {}
