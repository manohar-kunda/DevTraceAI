package com.devtrace.model;

/**
 * Domain record representing an incident node stored in FalkorDB.
 */
public record IncidentRecord(
        String id,
        String title,
        String status, // "OPEN" or "RESOLVED"
        long reportedAt,
        String severity // "P1", "P2", "P3", etc.
) {}
