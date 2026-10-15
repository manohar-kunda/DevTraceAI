package com.devtrace.model;

/**
 * Input DTO for confirming an incident resolution.
 * Validated by ResolutionService before persisting to FalkorDB.
 * Never includes confirmedAt or source (server-enforced).
 */
public record ResolutionInput(
        String rootCauseServiceId,
        String title,
        String remediationType, // e.g. "CONFIG_CHANGE", "RESTART", "ROLLBACK", "CODE_FIX"
        String actionTaken,
        String confirmedBy,
        String triggeringDeploymentId // Optional: if provided, links (r)-[:TRIGGERED_BY]->(d)
) {
    public ResolutionInput(
            String rootCauseServiceId,
            String title,
            String remediationType,
            String actionTaken,
            String confirmedBy
    ) {
        this(rootCauseServiceId, title, remediationType, actionTaken, confirmedBy, null);
    }

    public String summary() {
        return title;
    }
}
