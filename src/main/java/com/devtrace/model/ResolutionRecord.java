package com.devtrace.model;

/**
 * Represents a confirmed historical resolution attached to an incident.
 * Only a confirmed Resolution record in FalkorDB may be referred to as a "known resolution".
 */
public record ResolutionRecord(
        String id,
        String title,
        String remediationType,
        String actionTaken,
        long confirmedAt,
        String confirmedBy
) {}
