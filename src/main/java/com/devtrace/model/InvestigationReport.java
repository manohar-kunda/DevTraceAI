package com.devtrace.model;

import java.util.List;

/**
 * Root domain report model representing the output of an incident investigation.
 * Section names align with docs/GOLDEN_SCENARIO.md.
 * No LLM or generative hallucination is used in assembling this report.
 */
public record InvestigationReport(
        String status, // "RESOLVED_HYPOTHESIS" or "INSUFFICIENT_EVIDENCE"
        InvestigationRequest requestEcho,
        EntryPointsResult entryPoints,
        ObservedFacts observedFacts,
        GraphRelationships graphRelationships,
        List<CandidateHypothesis> hypotheses,
        List<IgnoredChange> ignoredChanges,
        List<SuggestedNextStep> suggestedNextSteps,
        List<String> limitations,
        List<String> assumptions
) {}
