package com.devtrace.model;

import java.util.List;

/**
 * Represents a ranked candidate root-cause hypothesis.
 */
public record CandidateHypothesis(
        String serviceId,
        String serviceName,
        RankingTier tier,
        int rank,
        int hopDistance,
        Long mostRecentChangeTimestamp,
        List<EvidenceItem> evidence,
        String plainLanguageReason,
        boolean hasConfirmedResolution,
        ResolutionRecord confirmedResolution
) {}
