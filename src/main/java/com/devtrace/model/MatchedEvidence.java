package com.devtrace.model;

public record MatchedEvidence(
        String matchedToken,
        String matchSource,
        String explanation
) {}
