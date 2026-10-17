package com.devtrace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmResolutionRequest(
        @NotBlank(message = "rootCauseServiceId is required")
        @Size(max = 100, message = "rootCauseServiceId cannot exceed 100 characters")
        String rootCauseServiceId,

        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title cannot exceed 200 characters")
        String title,

        @NotBlank(message = "remediationType is required")
        @Size(max = 50, message = "remediationType cannot exceed 50 characters")
        String remediationType,

        @NotBlank(message = "actionTaken is required")
        @Size(max = 2000, message = "actionTaken cannot exceed 2000 characters")
        String actionTaken,

        @NotBlank(message = "confirmedBy is required")
        @Size(max = 100, message = "confirmedBy cannot exceed 100 characters")
        String confirmedBy,

        @Size(max = 100, message = "triggeringDeploymentId cannot exceed 100 characters")
        String triggeringDeploymentId
) {
    public ConfirmResolutionRequest(
            String rootCauseServiceId,
            String title,
            String remediationType,
            String actionTaken,
            String confirmedBy
    ) {
        this(rootCauseServiceId, title, remediationType, actionTaken, confirmedBy, null);
    }
}
