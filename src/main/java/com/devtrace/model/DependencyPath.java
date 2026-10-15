package com.devtrace.model;

import java.util.List;

/**
 * Represents the shortest directed dependency path from a caller service to a callee service.
 *
 * Failure Propagation Directionality:
 * Graph edges point in the operational invocation direction: {@code (caller)-[:DEPENDS_ON]->(callee)}.
 * In contrast, failure propagation travels in the EXACT REVERSE direction:
 * When a downstream callee (e.g. {@code service-payment}) degrades or times out, the cascading failure,
 * latency spikes, and socket exhaustion propagate upstream along the inverse path
 * back to the caller (e.g. {@code service-order} and {@code service-checkout}).
 *
 * Therefore, tracing the {@code DEPENDS_ON} path from symptom to candidate root cause discovers the
 * invocation chain, and the candidate's failure propagates back along that same path in reverse.
 */
public record DependencyPath(
        String fromServiceId,
        String toServiceId,
        List<String> serviceIds,
        int hopCount
) {}
