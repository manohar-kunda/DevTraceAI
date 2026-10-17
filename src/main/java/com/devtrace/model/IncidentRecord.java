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
        String errorSignatureId,
        String serviceHint,
        String errorHint
) {
    public IncidentRecord(String id, String title, String status, long reportedAt, String severity, String symptomServiceId, String errorSignatureId) {
        this(id, title, status, reportedAt, severity, symptomServiceId, errorSignatureId, null, null);
    }

    public IncidentRecord(String id, String title, String status, long reportedAt, String severity) {
        this(id, title, status, reportedAt, severity, null, null, null, null);
    }
}
