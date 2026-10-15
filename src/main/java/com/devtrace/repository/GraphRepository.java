package com.devtrace.repository;

import com.devtrace.model.*;

import java.util.List;
import java.util.Optional;

public interface GraphRepository {

    /**
     * Loads all services with their configured aliases and metadata.
     */
    List<ServiceNode> loadServices(String graphName);

    /**
     * Loads all error signatures with their keywords and historically affected service IDs.
     */
    List<ErrorSignatureNode> loadErrorSignatures(String graphName);

    /**
     * Finds all upstream services that depend on the given service (incoming DEPENDS_ON callers).
     * Guaranteed to return each service exactly once with its minimum hop distance.
     *
     * @param graphName Target graph.
     * @param serviceId Target service ID.
     * @param maxDepth  Maximum traversal depth (clamped 1..10).
     * @param limit     Maximum number of results to return (clamped 1..100).
     * @return DependentsResult partitioned into direct (1 hop) and indirect (2+ hops).
     */
    DependentsResult findDependents(String graphName, String serviceId, int maxDepth, int limit);

    /**
     * Finds all downstream services that the given service depends on (outgoing DEPENDS_ON callees).
     * Guaranteed to return each service exactly once with its minimum hop distance.
     *
     * @param graphName Target graph.
     * @param serviceId Source service ID.
     * @param maxDepth  Maximum traversal depth (clamped 1..10).
     * @param limit     Maximum number of results to return (clamped 1..100).
     * @return DependenciesResult partitioned into direct (1 hop) and indirect (2+ hops).
     */
    DependenciesResult findDependencies(String graphName, String serviceId, int maxDepth, int limit);

    /**
     * Finds the shortest directed dependency path from a caller service to a callee service.
     * Note: Failure propagates in the reverse direction of this path.
     *
     * @param graphName     Target graph.
     * @param fromServiceId Caller service ID.
     * @param toServiceId   Callee service ID.
     * @param maxDepth      Maximum path length (clamped 1..10).
     * @return Optional containing the DependencyPath if a directed path exists.
     */
    Optional<DependencyPath> findDependencyPath(String graphName, String fromServiceId, String toServiceId, int maxDepth);
}
