package com.devtrace.repository;

import com.devtrace.model.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Finds historical incidents via deterministic structured graph matching.
     * Traverses (r:Resolution)-[:ADDRESSES]->(s:Service) and requires i.status = 'RESOLVED'.
     *
     * @param graphName          Target graph.
     * @param errorSignatureId   ErrorSignature ID matched from entry points.
     * @param candidateServiceIds Candidate service IDs in the topological neighborhood.
     * @return List of SimilarIncidentRecord instances with confirmed resolutions and provenance.
     */
    List<SimilarIncidentRecord> findSimilarIncidents(String graphName, String errorSignatureId, Set<String> candidateServiceIds);

    /**
     * Finds deployments on candidate services within a time window.
     *
     * @param graphName   Target graph.
     * @param serviceIds  Set of candidate service IDs.
     * @param windowStart Epoch ms start.
     * @param windowEnd   Epoch ms end (reference startedAt).
     * @return List of DeploymentRecord instances ordered by deployedAt DESC.
     */
    List<DeploymentRecord> findRecentDeployments(String graphName, Set<String> serviceIds, long windowStart, long windowEnd);

    /**
     * Finds all deployments in the time window across the entire graph.
     *
     * @param graphName   Target graph.
     * @param windowStart Epoch ms start.
     * @param windowEnd   Epoch ms end (reference startedAt).
     * @return List of DeploymentRecord instances ordered by deployedAt DESC.
     */
    List<DeploymentRecord> findAllDeploymentsInWindow(String graphName, long windowStart, long windowEnd);

    /**
     * Checks whether a record with the given ID exists in the graph.
     *
     * @param graphName Target graph.
     * @param recordId  Unique entity ID.
     * @return true if an entity with this ID exists.
     */
    boolean recordExists(String graphName, String recordId);

    /**
     * Checks if a Service node exists with the given ID.
     */
    boolean serviceExists(String graphName, String serviceId);

    /**
     * Checks if a Deployment node exists with the given ID.
     */
    boolean deploymentExists(String graphName, String deploymentId);

    /**
     * Finds an Incident node by its ID.
     */
    Optional<IncidentRecord> findIncidentById(String graphName, String incidentId);

    /**
     * Creates or idempotently ensures an Incident node exists in FalkorDB.
     */
    IncidentRecord createOrEnsureIncident(String graphName, IncidentInput input);

    /**
     * Finds a confirmed Resolution linked to the given Incident ID.
     */
    Optional<ResolutionRecord> findResolutionByIncidentId(String graphName, String incidentId);

    /**
     * Atomically marks an incident as RESOLVED and creates a Resolution node with source 'user-confirmed',
     * linking [:RESOLVES] to the incident, [:ADDRESSES] to rootCauseService, and optional [:TRIGGERED_BY] to deployment.
     */
    ResolutionRecord confirmResolution(String graphName, String incidentId, ResolutionInput input, long confirmedAt);
}
