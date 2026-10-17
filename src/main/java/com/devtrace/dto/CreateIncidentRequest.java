package com.devtrace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
        @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description cannot exceed 2000 characters")
        String description,

        @Size(max = 200, message = "serviceHint cannot exceed 200 characters")
        String serviceHint,

        @Size(max = 200, message = "errorHint cannot exceed 200 characters")
        String errorHint,

        Long startedAt,

        @Size(max = 100, message = "incidentId cannot exceed 100 characters")
        String incidentId
) {
    public CreateIncidentRequest(String description) {
        this(description, null, null, null, null);
    }
}
