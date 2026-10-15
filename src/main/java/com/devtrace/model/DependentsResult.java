package com.devtrace.model;

import java.util.List;

public record DependentsResult(
        String targetServiceId,
        List<ServiceHop> directDependents,
        List<ServiceHop> indirectDependents
) {
    public List<ServiceHop> allDependents() {
        var all = new java.util.ArrayList<>(directDependents);
        all.addAll(indirectDependents);
        return List.copyOf(all);
    }
}
