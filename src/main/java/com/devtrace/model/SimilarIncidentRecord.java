package com.devtrace.model;

import java.util.List;

/**
 * Represents a historical incident retrieved via deterministic structured graph matching.
 * Matching requires: (1) same ErrorSignature, (2) same affected service, (3) service inside candidate neighborhood.
 * Free-text similarity or embeddings are strictly prohibited.
 */
public record SimilarIncidentRecord(
        String incidentId,
        String title,
        String errorSignatureId,
        List<String> affectedServiceIds,
        String status,
        List<String> matchReasons,
        ResolutionRecord confirmedResolution
) {
    public boolean hasConfirmedResolution() {
        return confirmedResolution != null;
    }
}
