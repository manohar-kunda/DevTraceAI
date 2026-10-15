package com.devtrace.model;

public record ResolvedErrorSignature(
        String signatureId,
        String pattern,
        String errorType,
        MatchedEvidence evidence
) {}
