package com.devtrace.model;

/**
 * Domain record representing an incident node stored in FalkorDB.
 */
public record IncidentRecord(
        String id,
        String title,
        String status, // "OPEN" or "RESOLVED"
        long reportedAt,
        String severity, // "P1", "P2", "P3", etc.
        String symptomServiceId,
        String errorSignatureId
) {
    public IncidentRecord(String id, String title, String status, long reportedAt, String severity) {
        this(id, title, status, reportedAt, severity, null, null);
    }
}
