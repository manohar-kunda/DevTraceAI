package com.devtrace.model;

public record ResolvedService(
        String serviceId,
        String serviceName,
        String serviceType,
        MatchedEvidence evidence
) {}
