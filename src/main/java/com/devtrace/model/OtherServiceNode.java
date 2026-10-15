package com.devtrace.model;

/**
 * Compact representation of a reachable service node in the dependency neighborhood
 * that does not meet STRONG or MODERATE ranking criteria (WEAK candidate).
 */
public record OtherServiceNode(
        String id,
        String name,
        int hops
) {}
