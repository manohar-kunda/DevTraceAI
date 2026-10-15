package com.devtrace.model;

import java.util.Map;

/**
 * Encapsulates graph traversal relationships discovered during investigation:
 * - Directed dependency path from symptom service to each candidate
 * - Upstream caller blast radius for the top suspect service
 */
public record GraphRelationships(
        Map<String, DependencyPath> pathsToCandidates,
        DependentsResult blastRadius
) {}
