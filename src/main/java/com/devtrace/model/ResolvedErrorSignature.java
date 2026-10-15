package com.devtrace.model;

public record ResolvedErrorSignature(
        String signatureId,
        String pattern,
        String errorType,
        MatchedEvidence evidence
) {
    public ResolvedErrorSignature(String signatureId, String pattern, String errorType) {
        this(signatureId, pattern, errorType, new MatchedEvidence(pattern, "KEYWORD", "Matched " + pattern));
    }
}
