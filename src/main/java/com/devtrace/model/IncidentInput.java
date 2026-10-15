package com.devtrace.model;

/**
 * Input DTO for creating or ensuring an incident node exists in FalkorDB.
 */
public record IncidentInput(
        String id,
        String title,
        String status, // Defaults to "OPEN" if null
        long reportedAt,
        String severity, // "P1", "P2", "P3", etc.
        String symptomServiceId, // Optional: if provided, links (i)-[:AFFECTS]->(service)
        String errorSignatureId // Optional: if provided, links (i)-[:EXHIBITS]->(signature)
) {
    public IncidentInput(String id, String title, long reportedAt, String severity) {
        this(id, title, "OPEN", reportedAt, severity, null, null);
    }

    public IncidentInput(String id, String title, long reportedAt, String severity, String symptomServiceId, String errorSignatureId) {
        this(id, title, "OPEN", reportedAt, severity, symptomServiceId, errorSignatureId);
    }
}
