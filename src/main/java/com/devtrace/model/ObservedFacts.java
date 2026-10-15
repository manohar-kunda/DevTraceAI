package com.devtrace.model;

import java.util.List;

/**
 * Encapsulates facts retrieved from the graph:
 * - Deployments found within the reference window
 * - Historical incidents retrieved via structured graph matching
 * - List of missing evidence items (when insufficient evidence is encountered)
 */
public record ObservedFacts(
        List<DeploymentRecord> deploymentsInWindow,
        List<SimilarIncidentRecord> matchedHistoricalIncidents,
        List<String> missingEvidence
) {}
