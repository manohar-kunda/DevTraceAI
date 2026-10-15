package com.devtrace.model;

/**
 * Represents a deployment linked to a service node in FalkorDB.
 */
public record DeploymentRecord(
        String deploymentId,
        String serviceId,
        String version,
        long deployedAt,
        String environment,
        String deployedBy
) {}
