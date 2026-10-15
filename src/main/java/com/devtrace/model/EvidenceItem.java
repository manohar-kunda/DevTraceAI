package com.devtrace.model;

/**
 * Represents a verifiable evidence item supporting a candidate cause hypothesis.
 * Must carry the exact FalkorDB record ID and the name of the repository query that produced it.
 */
public record EvidenceItem(
        String queryName,
        String recordId,
        String evidenceType,
        String description
) {}
