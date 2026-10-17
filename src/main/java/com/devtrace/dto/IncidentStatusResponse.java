package com.devtrace.dto;

import com.devtrace.model.ResolutionRecord;

public record IncidentStatusResponse(
        String incidentId,
        String title,
        String status,
        long reportedAt,
        String severity,
        ResolutionRecord confirmedResolution
) {}
