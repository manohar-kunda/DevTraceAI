package com.devtrace.model;

public record ServiceHop(
        String serviceId,
        String serviceName,
        String serviceType,
        int hops
) {}
