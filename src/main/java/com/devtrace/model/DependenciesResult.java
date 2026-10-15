package com.devtrace.model;

import java.util.List;

public record DependenciesResult(
        String sourceServiceId,
        List<ServiceHop> directDependencies,
        List<ServiceHop> indirectDependencies
) {
    public List<ServiceHop> allDependencies() {
        var all = new java.util.ArrayList<>(directDependencies);
        all.addAll(indirectDependencies);
        return List.copyOf(all);
    }
}
