package com.devtrace.model;

import java.util.Optional;

public record EntryPointsResult(
        Optional<ResolvedService> service,
        Optional<ResolvedErrorSignature> errorSignature
) {
    public boolean hasConfidentMatches() {
        return service.isPresent() || errorSignature.isPresent();
    }

    public boolean hasBoth() {
        return service.isPresent() && errorSignature.isPresent();
    }

    public Optional<ResolvedService> resolvedService() {
        return service;
    }

    public Optional<ResolvedErrorSignature> resolvedSignature() {
        return errorSignature;
    }
}
