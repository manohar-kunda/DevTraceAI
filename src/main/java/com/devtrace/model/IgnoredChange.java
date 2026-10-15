package com.devtrace.model;

/**
 * Represents a recent infrastructure or deployment change occurring outside the topological candidate neighborhood.
 * These changes are explicitly excluded from cause ranking and recorded under "recent changes outside the dependency neighborhood, not considered".
 */
public record IgnoredChange(
        String changeId,
        String serviceId,
        String serviceName,
        String changeType,
        long timestamp,
        String reason
) {}
