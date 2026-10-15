package com.devtrace.model;

import java.util.List;

public record ServiceNode(
        String id,
        String name,
        String team,
        String serviceType,
        int criticalTier,
        List<String> aliases
) {}
