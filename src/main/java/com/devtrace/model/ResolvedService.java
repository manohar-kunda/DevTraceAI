package com.devtrace.model;

public record ResolvedService(
        String serviceId,
        String serviceName,
        String serviceType,
        MatchedEvidence evidence
) {
    public ResolvedService(String serviceId, String serviceName, String serviceType) {
        this(serviceId, serviceName, serviceType, new MatchedEvidence(serviceName, "ALIAS", "Matched " + serviceName));
    }
}
