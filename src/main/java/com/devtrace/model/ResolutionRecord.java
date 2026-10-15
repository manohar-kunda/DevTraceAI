package com.devtrace.model;

/**
 * Represents a confirmed historical resolution attached to an incident.
 * Only a confirmed Resolution record in FalkorDB may be referred to as a "known resolution".
 * Exposes provenance: source ('synthetic' vs 'user-confirmed'), confirmedBy, confirmedAt.
 */
public record ResolutionRecord(
        String id,
        String title,
        String remediationType,
        String actionTaken,
        long confirmedAt,
        String confirmedBy,
        String source
) {
    public ResolutionRecord(
            String id,
            String title,
            String remediationType,
            String actionTaken,
            long confirmedAt,
            String confirmedBy
    ) {
        this(id, title, remediationType, actionTaken, confirmedAt, confirmedBy, "synthetic");
    }
}
