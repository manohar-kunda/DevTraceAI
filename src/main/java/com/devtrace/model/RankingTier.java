package com.devtrace.model;

/**
 * Deterministic rule-based ranking tiers:
 * - STRONG: signature evidence AND a recent deployment on the candidate.
 * - MODERATE: signature evidence only OR a recent deployment only.
 * - WEAK: in the candidate neighborhood but with no direct evidence.
 *
 * Tiering is strictly rule-based; confidence percentages and artificial numeric scores are forbidden.
 */
public enum RankingTier {
    STRONG,
    MODERATE,
    WEAK
}
