package com.devtrace.model;

/**
 * Represents an evidence-backed next step.
 * Must be clearly distinguished as derived from a confirmed historical resolution vs generic diagnostic suggestion.
 */
public record SuggestedNextStep(
        String stepType, // "FROM_CONFIRMED_RESOLUTION" or "GENERIC_DIAGNOSTIC"
        String label,
        String description,
        String sourceResolutionId
) {
    public static SuggestedNextStep fromConfirmedResolution(ResolutionRecord res) {
        return new SuggestedNextStep(
                "FROM_CONFIRMED_RESOLUTION",
                "Consider the previously confirmed remediation " + res.id() + " (from a different incident)",
                res.actionTaken(),
                res.id()
        );
    }

    public static SuggestedNextStep genericDiagnostic(String label, String description) {
        return new SuggestedNextStep(
                "GENERIC_DIAGNOSTIC",
                label,
                description,
                null
        );
    }
}
