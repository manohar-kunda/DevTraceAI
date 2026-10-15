package com.devtrace.seed;

import java.util.Map;

public record SeedResult(
        String graphName,
        long totalNodes,
        long totalRelationships,
        Map<String, Long> nodeCountsByLabel,
        long executionTimeMs
) {}
