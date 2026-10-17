package com.devtrace.repository;

import com.devtrace.model.*;
import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * FalkorDB graph repository implementation for DevTrace AI.
 * Communicates with FalkorDB over the binary Redis protocol via Jedis / JFalkorDB.
 * Enforces parameterization on all dynamic values to prevent injection.
 */
@Repository
public class FalkorGraphRepository implements GraphRepository {

    private final Driver driver;

    public FalkorGraphRepository(Driver driver) {
        this.driver = driver;
    }

    @Override
    public List<ServiceNode> loadServices(String graphName) {
        Graph graph = driver.graph(graphName);
        String cypher = """
            MATCH (s:Service)
            RETURN s.id AS id, s.name AS name, s.team AS team, s.serviceType AS serviceType, s.criticalTier AS tier, s.aliases AS aliases
            ORDER BY s.id ASC
        """;

        ResultSet rs = graph.readOnlyQuery(cypher);
        List<ServiceNode> services = new ArrayList<>();

        for (Record rec : rs) {
            String id = rec.getString("id");
            String name = rec.getString("name");
            String team = rec.getString("team");
            String serviceType = rec.getString("serviceType");
            int tier = ((Number) rec.getValue("tier")).intValue();
            String aliasesRaw = rec.getString("aliases");
            List<String> aliases = parseCommaSeparated(aliasesRaw);

            services.add(new ServiceNode(id, name, team, serviceType, tier, aliases));
        }

        return Collections.unmodifiableList(services);
    }

    @Override
    public List<ErrorSignatureNode> loadErrorSignatures(String graphName) {
        Graph graph = driver.graph(graphName);
        String cypher = """
            MATCH (e:ErrorSignature)
            OPTIONAL MATCH (i:Incident)-[:EXHIBITS]->(e)
            OPTIONAL MATCH (i)-[:AFFECTS]->(s:Service)
            RETURN e.id AS id, e.pattern AS pattern, e.errorType AS errorType, e.keywords AS keywords, collect(DISTINCT s.id) AS associatedServices
            ORDER BY e.id ASC
        """;

        ResultSet rs = graph.readOnlyQuery(cypher);
        List<ErrorSignatureNode> signatures = new ArrayList<>();

        for (Record rec : rs) {
            String id = rec.getString("id");
            String pattern = rec.getString("pattern");
            String errorType = rec.getString("errorType");
            String keywordsRaw = rec.getString("keywords");
            List<String> keywords = parseCommaSeparated(keywordsRaw);

            List<String> associatedServices = extractStringList(rec.getValue("associatedServices"));
            signatures.add(new ErrorSignatureNode(id, pattern, errorType, keywords, associatedServices));
        }

        return Collections.unmodifiableList(signatures);
    }

    @Override
    public DependentsResult findDependents(String graphName, String serviceId, int maxDepth, int limit) {
        int boundedDepth = clamp(maxDepth, 1, 10);
        int boundedLimit = clamp(limit, 1, 100);

        Graph graph = driver.graph(graphName);
        String cypher = String.format("""
            MATCH path = (caller:Service)-[:DEPENDS_ON*1..%d]->(target:Service {id: $serviceId})
            WITH caller, min(length(path)) AS minHops
            ORDER BY minHops ASC, caller.id ASC
            LIMIT $limit
            RETURN caller.id AS id, caller.name AS name, caller.serviceType AS serviceType, minHops AS hops
        """, boundedDepth);

        Map<String, Object> params = Map.of(
                "serviceId", serviceId,
                "limit", boundedLimit
        );

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        List<ServiceHop> direct = new ArrayList<>();
        List<ServiceHop> indirect = new ArrayList<>();

        for (Record rec : rs) {
            String id = rec.getString("id");
            String name = rec.getString("name");
            String serviceType = rec.getString("serviceType");
            int hops = ((Number) rec.getValue("hops")).intValue();

            ServiceHop hop = new ServiceHop(id, name, serviceType, hops);
            if (hops == 1) {
                direct.add(hop);
            } else {
                indirect.add(hop);
            }
        }

        return new DependentsResult(serviceId, Collections.unmodifiableList(direct), Collections.unmodifiableList(indirect));
    }

    @Override
    public DependenciesResult findDependencies(String graphName, String serviceId, int maxDepth, int limit) {
        int boundedDepth = clamp(maxDepth, 1, 10);
        int boundedLimit = clamp(limit, 1, 100);

        Graph graph = driver.graph(graphName);
        String cypher = String.format("""
            MATCH path = (source:Service {id: $serviceId})-[:DEPENDS_ON*1..%d]->(callee:Service)
            WITH callee, min(length(path)) AS minHops
            ORDER BY minHops ASC, callee.id ASC
            LIMIT $limit
            RETURN callee.id AS id, callee.name AS name, callee.serviceType AS serviceType, minHops AS hops
        """, boundedDepth);

        Map<String, Object> params = Map.of(
                "serviceId", serviceId,
                "limit", boundedLimit
        );

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        List<ServiceHop> direct = new ArrayList<>();
        List<ServiceHop> indirect = new ArrayList<>();

        for (Record rec : rs) {
            String id = rec.getString("id");
            String name = rec.getString("name");
            String serviceType = rec.getString("serviceType");
            int hops = ((Number) rec.getValue("hops")).intValue();

            ServiceHop hop = new ServiceHop(id, name, serviceType, hops);
            if (hops == 1) {
                direct.add(hop);
            } else {
                indirect.add(hop);
            }
        }

        return new DependenciesResult(serviceId, Collections.unmodifiableList(direct), Collections.unmodifiableList(indirect));
    }

    @Override
    public Optional<DependencyPath> findDependencyPath(String graphName, String fromServiceId, String toServiceId, int maxDepth) {
        int boundedDepth = clamp(maxDepth, 1, 10);

        Graph graph = driver.graph(graphName);
        String cypher = String.format("""
            MATCH (src:Service {id: $fromId}), (tgt:Service {id: $toId})
            WITH shortestPath((src)-[:DEPENDS_ON*1..%d]->(tgt)) AS p
            WHERE p IS NOT NULL
            RETURN [n in nodes(p) | n.id] AS path, length(p) AS hopCount
        """, boundedDepth);

        Map<String, Object> params = Map.of(
                "fromId", fromServiceId,
                "toId", toServiceId
        );

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        Iterator<Record> it = rs.iterator();
        if (!it.hasNext()) {
            return Optional.empty();
        }

        Record rec = it.next();
        List<String> path = extractStringList(rec.getValue("path"));
        int hopCount = ((Number) rec.getValue("hopCount")).intValue();

        return Optional.of(new DependencyPath(fromServiceId, toServiceId, Collections.unmodifiableList(path), hopCount));
    }

    @Override
    public List<SimilarIncidentRecord> findSimilarIncidents(String graphName, String errorSignatureId, Set<String> candidateServiceIds) {
        return findSimilarIncidents(graphName, errorSignatureId, candidateServiceIds, null);
    }

    @Override
    public List<SimilarIncidentRecord> findSimilarIncidents(String graphName, String errorSignatureId, Set<String> candidateServiceIds, String excludeIncidentId) {
        if (errorSignatureId == null || errorSignatureId.isBlank() || candidateServiceIds == null || candidateServiceIds.isEmpty()) {
            return List.of();
        }

        Graph graph = driver.graph(graphName);
        String cypher = """
            MATCH (i:Incident {status: 'RESOLVED'})-[:EXHIBITS]->(e:ErrorSignature {id: $sigId})
            MATCH (i)-[:AFFECTS]->(aff:Service)
            MATCH (r:Resolution)-[:RESOLVES]->(i)
            MATCH (r)-[:ADDRESSES]->(s:Service)
            WHERE (s.id IN $serviceIds OR aff.id IN $serviceIds)
              AND ($excludeIncidentId = '' OR i.id <> $excludeIncidentId)
            RETURN i.id AS incidentId, i.title AS title, i.status AS status, e.id AS errorSignatureId,
                   collect(DISTINCT aff.id) AS affectedServiceIds,
                   s.id AS addressedServiceId,
                   r.id AS resId, r.title AS resTitle, r.remediationType AS resRemediationType,
                   r.actionTaken AS resActionTaken, r.confirmedAt AS resConfirmedAt, r.confirmedBy AS resConfirmedBy,
                   r.source AS resSource
            ORDER BY r.confirmedAt DESC, i.id ASC
        """;

        Map<String, Object> params = new HashMap<>();
        params.put("sigId", errorSignatureId);
        params.put("serviceIds", new ArrayList<>(candidateServiceIds));
        params.put("excludeIncidentId", excludeIncidentId != null ? excludeIncidentId : "");

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        List<SimilarIncidentRecord> records = new ArrayList<>();

        for (Record rec : rs) {
            String incidentId = rec.getString("incidentId");
            String title = rec.getString("title");
            String status = rec.getString("status");
            String sigId = rec.getString("errorSignatureId");
            List<String> affectedServices = new ArrayList<>(extractStringList(rec.getValue("affectedServiceIds")));
            String addressedServiceId = rec.getString("addressedServiceId");
            if (addressedServiceId != null && !affectedServices.contains(addressedServiceId)) {
                affectedServices.add(addressedServiceId);
            }

            List<String> matchReasons = List.of(
                    "Matches ErrorSignature: " + sigId,
                    "Affects candidate service(s): " + String.join(", ", affectedServices),
                    "Confirmed resolution addresses root cause service: " + (addressedServiceId != null ? addressedServiceId : "unknown")
            );

            ResolutionRecord resRecord = null;
            Object resIdObj = rec.getValue("resId");
            if (resIdObj != null && !resIdObj.toString().isBlank()) {
                String resId = resIdObj.toString();
                String resTitle = rec.getString("resTitle");
                String resRemediationType = rec.getString("resRemediationType");
                String resActionTaken = rec.getString("resActionTaken");
                long resConfirmedAt = ((Number) rec.getValue("resConfirmedAt")).longValue();
                String resConfirmedBy = rec.getString("resConfirmedBy");
                String resSource = rec.getString("resSource");

                resRecord = new ResolutionRecord(
                        resId,
                        resTitle,
                        resRemediationType,
                        resActionTaken,
                        resConfirmedAt,
                        resConfirmedBy,
                        resSource != null ? resSource : "synthetic"
                );
            }

            records.add(new SimilarIncidentRecord(
                    incidentId,
                    title,
                    sigId,
                    Collections.unmodifiableList(affectedServices),
                    status,
                    matchReasons,
                    resRecord
            ));
        }

        return Collections.unmodifiableList(records);
    }

    @Override
    public List<DeploymentRecord> findRecentDeployments(String graphName, Set<String> serviceIds, long windowStart, long windowEnd) {
        if (serviceIds == null || serviceIds.isEmpty() || windowStart > windowEnd) {
            return List.of();
        }

        Graph graph = driver.graph(graphName);
        String cypher = """
            MATCH (d:Deployment)-[:DEPLOYED_TO]->(s:Service)
            WHERE s.id IN $serviceIds AND d.deployedAt >= $windowStart AND d.deployedAt <= $windowEnd
            RETURN d.id AS deploymentId, s.id AS serviceId, d.version AS version,
                   d.deployedAt AS deployedAt, d.environment AS environment, d.deployedBy AS deployedBy,
                   d.changeSummary AS changeSummary
            ORDER BY d.deployedAt DESC
        """;

        Map<String, Object> params = Map.of(
                "serviceIds", new ArrayList<>(serviceIds),
                "windowStart", windowStart,
                "windowEnd", windowEnd
        );

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        List<DeploymentRecord> deployments = new ArrayList<>();

        for (Record rec : rs) {
            String deploymentId = rec.getString("deploymentId");
            String serviceId = rec.getString("serviceId");
            String version = rec.getString("version");
            long deployedAt = ((Number) rec.getValue("deployedAt")).longValue();
            String env = rec.getString("environment");
            String deployedBy = rec.getString("deployedBy");
            String changeSummary = rec.getString("changeSummary");

            deployments.add(new DeploymentRecord(
                    deploymentId,
                    serviceId,
                    version,
                    deployedAt,
                    env,
                    deployedBy,
                    changeSummary != null ? changeSummary : ""
            ));
        }

        return Collections.unmodifiableList(deployments);
    }

    @Override
    public List<DeploymentRecord> findAllDeploymentsInWindow(String graphName, long windowStart, long windowEnd) {
        if (windowStart > windowEnd) {
            return List.of();
        }

        Graph graph = driver.graph(graphName);
        String cypher = """
            MATCH (d:Deployment)-[:DEPLOYED_TO]->(s:Service)
            WHERE d.deployedAt >= $windowStart AND d.deployedAt <= $windowEnd
            RETURN d.id AS deploymentId, s.id AS serviceId, d.version AS version,
                   d.deployedAt AS deployedAt, d.environment AS environment, d.deployedBy AS deployedBy,
                   d.changeSummary AS changeSummary
            ORDER BY d.deployedAt DESC
        """;

        Map<String, Object> params = Map.of(
                "windowStart", windowStart,
                "windowEnd", windowEnd
        );

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        List<DeploymentRecord> deployments = new ArrayList<>();

        for (Record rec : rs) {
            String deploymentId = rec.getString("deploymentId");
            String serviceId = rec.getString("serviceId");
            String version = rec.getString("version");
            long deployedAt = ((Number) rec.getValue("deployedAt")).longValue();
            String env = rec.getString("environment");
            String deployedBy = rec.getString("deployedBy");
            String changeSummary = rec.getString("changeSummary");

            deployments.add(new DeploymentRecord(
                    deploymentId,
                    serviceId,
                    version,
                    deployedAt,
                    env,
                    deployedBy,
                    changeSummary != null ? changeSummary : ""
            ));
        }

        return Collections.unmodifiableList(deployments);
    }

    @Override
    public boolean recordExists(String graphName, String recordId) {
        if (recordId == null || recordId.isBlank()) {
            return false;
        }

        Graph graph = driver.graph(graphName);
        String cypher = "MATCH (n {id: $recordId}) RETURN count(n) > 0 AS exists";
        Map<String, Object> params = Map.of("recordId", recordId);

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        Iterator<Record> it = rs.iterator();
        if (it.hasNext()) {
            return Boolean.TRUE.equals(it.next().getValue("exists"));
        }
        return false;
    }

    @Override
    public boolean serviceExists(String graphName, String serviceId) {
        if (serviceId == null || serviceId.isBlank()) return false;
        Graph graph = driver.graph(graphName);
        ResultSet rs = graph.readOnlyQuery("MATCH (s:Service {id: $serviceId}) RETURN count(s) > 0 AS exists", Map.of("serviceId", serviceId));
        Iterator<Record> it = rs.iterator();
        return it.hasNext() && Boolean.TRUE.equals(it.next().getValue("exists"));
    }

    @Override
    public boolean deploymentExists(String graphName, String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) return false;
        Graph graph = driver.graph(graphName);
        ResultSet rs = graph.readOnlyQuery("MATCH (d:Deployment {id: $deploymentId}) RETURN count(d) > 0 AS exists", Map.of("deploymentId", deploymentId));
        Iterator<Record> it = rs.iterator();
        return it.hasNext() && Boolean.TRUE.equals(it.next().getValue("exists"));
    }

    @Override
    public Optional<IncidentRecord> findIncidentById(String graphName, String incidentId) {
        if (incidentId == null || incidentId.isBlank()) return Optional.empty();
        Graph graph = driver.graph(graphName);
        ResultSet rs = graph.readOnlyQuery(
                """
                MATCH (i:Incident {id: $incidentId})
                OPTIONAL MATCH (i)-[:AFFECTS]->(s:Service)
                OPTIONAL MATCH (i)-[:EXHIBITS]->(e:ErrorSignature)
                RETURN i.id AS id, i.title AS title, i.status AS status, i.reportedAt AS reportedAt, i.severity AS severity,
                       s.id AS symptomServiceId, e.id AS errorSignatureId
                """,
                Map.of("incidentId", incidentId)
        );
        Iterator<Record> it = rs.iterator();
        if (it.hasNext()) {
            Record rec = it.next();
            return Optional.of(new IncidentRecord(
                    rec.getString("id"),
                    rec.getString("title"),
                    rec.getString("status"),
                    ((Number) rec.getValue("reportedAt")).longValue(),
                    rec.getString("severity"),
                    rec.getString("symptomServiceId"),
                    rec.getString("errorSignatureId")
            ));
        }
        return Optional.empty();
    }

    @Override
    public IncidentRecord createOrEnsureIncident(String graphName, IncidentInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        Graph graph = driver.graph(graphName);

        String status = input.status() != null && !input.status().isBlank() ? input.status() : "OPEN";

        String cypher = """
            MERGE (i:Incident {id: $id})
            ON CREATE SET i.title = $title, i.status = $status, i.reportedAt = $reportedAt, i.severity = $severity, i.source = 'runtime'
            ON MATCH SET i.title = $title, i.severity = $severity
            RETURN i.id AS id, i.title AS title, i.status AS status, i.reportedAt AS reportedAt, i.severity AS severity
        """;

        Map<String, Object> params = Map.of(
                "id", input.id(),
                "title", input.title(),
                "status", status,
                "reportedAt", input.reportedAt(),
                "severity", input.severity()
        );

        ResultSet rs = graph.query(cypher, params);
        Iterator<Record> it = rs.iterator();
        if (!it.hasNext()) {
            throw new IllegalStateException("Failed to create or ensure incident node for ID: " + input.id());
        }
        Record rec = it.next();

        String resolvedServiceId = null;
        if (input.symptomServiceId() != null && !input.symptomServiceId().isBlank()) {
            ResultSet sRs = graph.query(
                    """
                    MATCH (i:Incident {id: $id}), (s:Service)
                    WHERE s.id = $sId OR s.name = $sId OR s.aliases CONTAINS $sId
                    MERGE (i)-[r:AFFECTS]->(s)
                    ON CREATE SET r.source = 'runtime'
                    RETURN s.id AS resolvedServiceId
                    LIMIT 1
                    """,
                    Map.of("id", input.id(), "sId", input.symptomServiceId())
            );
            Iterator<Record> sIt = sRs.iterator();
            if (sIt.hasNext()) {
                resolvedServiceId = sIt.next().getString("resolvedServiceId");
            }
        }

        String resolvedErrorSigId = null;
        if (input.errorSignatureId() != null && !input.errorSignatureId().isBlank()) {
            ResultSet eRs = graph.query(
                    """
                    MATCH (i:Incident {id: $id}), (e:ErrorSignature)
                    WHERE e.id = $eId OR e.errorType = $eId OR e.pattern CONTAINS $eId
                    MERGE (i)-[r:EXHIBITS]->(e)
                    ON CREATE SET r.source = 'runtime'
                    RETURN e.id AS resolvedErrorSigId
                    LIMIT 1
                    """,
                    Map.of("id", input.id(), "eId", input.errorSignatureId())
            );
            Iterator<Record> eIt = eRs.iterator();
            if (eIt.hasNext()) {
                resolvedErrorSigId = eIt.next().getString("resolvedErrorSigId");
            }
        }

        return new IncidentRecord(
                rec.getString("id"),
                rec.getString("title"),
                rec.getString("status"),
                ((Number) rec.getValue("reportedAt")).longValue(),
                rec.getString("severity"),
                resolvedServiceId != null ? resolvedServiceId : input.symptomServiceId(),
                resolvedErrorSigId != null ? resolvedErrorSigId : input.errorSignatureId()
        );
    }

    @Override
    public void linkIncidentRelationships(String graphName, String incidentId, String serviceId, String errorSignatureId) {
        if (incidentId == null || incidentId.isBlank()) return;
        Graph graph = driver.graph(graphName);

        if (serviceId != null && !serviceId.isBlank()) {
            graph.query(
                    """
                    MATCH (i:Incident {id: $id}), (s:Service)
                    WHERE s.id = $sId OR s.name = $sId OR s.aliases CONTAINS $sId
                    MERGE (i)-[r:AFFECTS]->(s)
                    ON CREATE SET r.source = 'runtime'
                    """,
                    Map.of("id", incidentId, "sId", serviceId)
            );
        }

        if (errorSignatureId != null && !errorSignatureId.isBlank()) {
            graph.query(
                    """
                    MATCH (i:Incident {id: $id}), (e:ErrorSignature)
                    WHERE e.id = $eId OR e.errorType = $eId OR e.pattern CONTAINS $eId
                    MERGE (i)-[r:EXHIBITS]->(e)
                    ON CREATE SET r.source = 'runtime'
                    """,
                    Map.of("id", incidentId, "eId", errorSignatureId)
            );
        }
    }

    @Override
    public Optional<ResolutionRecord> findResolutionByIncidentId(String graphName, String incidentId) {
        if (incidentId == null || incidentId.isBlank()) return Optional.empty();
        Graph graph = driver.graph(graphName);
        ResultSet rs = graph.readOnlyQuery(
                "MATCH (r:Resolution)-[:RESOLVES]->(i:Incident {id: $incidentId}) " +
                "RETURN r.id AS id, r.title AS title, r.remediationType AS remediationType, " +
                "       r.actionTaken AS actionTaken, r.confirmedAt AS confirmedAt, " +
                "       r.confirmedBy AS confirmedBy, r.source AS source",
                Map.of("incidentId", incidentId)
        );
        Iterator<Record> it = rs.iterator();
        if (it.hasNext()) {
            Record rec = it.next();
            return Optional.of(new ResolutionRecord(
                    rec.getString("id"),
                    rec.getString("title"),
                    rec.getString("remediationType"),
                    rec.getString("actionTaken"),
                    ((Number) rec.getValue("confirmedAt")).longValue(),
                    rec.getString("confirmedBy"),
                    rec.getString("source")
            ));
        }
        return Optional.empty();
    }

    @Override
    public ResolutionRecord confirmResolution(String graphName, String incidentId, ResolutionInput input, long confirmedAt) {
        Objects.requireNonNull(input, "input cannot be null");
        Graph graph = driver.graph(graphName);

        String resId = "res-" + incidentId;
        boolean hasDeployment = input.triggeringDeploymentId() != null && !input.triggeringDeploymentId().isBlank();

        String cypher;
        Map<String, Object> params;

        if (hasDeployment) {
            cypher = """
                MATCH (i:Incident {id: $incidentId})
                MATCH (s:Service {id: $serviceId})
                MATCH (d:Deployment {id: $deploymentId})
                SET i.status = 'RESOLVED'
                MERGE (r:Resolution {id: $resId})
                ON CREATE SET r.title = $title,
                              r.remediationType = $remediationType,
                              r.actionTaken = $actionTaken,
                              r.confirmedAt = $confirmedAt,
                              r.confirmedBy = $confirmedBy,
                              r.source = 'user-confirmed'
                ON MATCH SET r.title = $title,
                             r.remediationType = $remediationType,
                             r.actionTaken = $actionTaken,
                             r.confirmedAt = $confirmedAt,
                             r.confirmedBy = $confirmedBy,
                             r.source = 'user-confirmed'
                MERGE (r)-[r1:RESOLVES]->(i)
                ON CREATE SET r1.source = 'user-confirmed'
                MERGE (r)-[r2:ADDRESSES]->(s)
                ON CREATE SET r2.source = 'user-confirmed'
                MERGE (r)-[r3:TRIGGERED_BY]->(d)
                ON CREATE SET r3.source = 'user-confirmed'
                RETURN r.id AS id, r.title AS title, r.remediationType AS remediationType,
                       r.actionTaken AS actionTaken, r.confirmedAt AS confirmedAt,
                       r.confirmedBy AS confirmedBy, r.source AS source
            """;
            params = Map.of(
                    "incidentId", incidentId,
                    "serviceId", input.rootCauseServiceId(),
                    "deploymentId", input.triggeringDeploymentId(),
                    "resId", resId,
                    "title", input.title(),
                    "remediationType", input.remediationType(),
                    "actionTaken", input.actionTaken(),
                    "confirmedAt", confirmedAt,
                    "confirmedBy", input.confirmedBy()
            );
        } else {
            cypher = """
                MATCH (i:Incident {id: $incidentId})
                MATCH (s:Service {id: $serviceId})
                SET i.status = 'RESOLVED'
                MERGE (r:Resolution {id: $resId})
                ON CREATE SET r.title = $title,
                              r.remediationType = $remediationType,
                              r.actionTaken = $actionTaken,
                              r.confirmedAt = $confirmedAt,
                              r.confirmedBy = $confirmedBy,
                              r.source = 'user-confirmed'
                ON MATCH SET r.title = $title,
                             r.remediationType = $remediationType,
                             r.actionTaken = $actionTaken,
                             r.confirmedAt = $confirmedAt,
                             r.confirmedBy = $confirmedBy,
                             r.source = 'user-confirmed'
                MERGE (r)-[r1:RESOLVES]->(i)
                ON CREATE SET r1.source = 'user-confirmed'
                MERGE (r)-[r2:ADDRESSES]->(s)
                ON CREATE SET r2.source = 'user-confirmed'
                RETURN r.id AS id, r.title AS title, r.remediationType AS remediationType,
                       r.actionTaken AS actionTaken, r.confirmedAt AS confirmedAt,
                       r.confirmedBy AS confirmedBy, r.source AS source
            """;
            params = Map.of(
                    "incidentId", incidentId,
                    "serviceId", input.rootCauseServiceId(),
                    "resId", resId,
                    "title", input.title(),
                    "remediationType", input.remediationType(),
                    "actionTaken", input.actionTaken(),
                    "confirmedAt", confirmedAt,
                    "confirmedBy", input.confirmedBy()
            );
        }

        ResultSet rs = graph.query(cypher, params);
        Iterator<Record> it = rs.iterator();
        if (!it.hasNext()) {
            throw new IllegalStateException("Failed to atomically confirm resolution for incident: " + incidentId);
        }
        Record rec = it.next();
        return new ResolutionRecord(
                rec.getString("id"),
                rec.getString("title"),
                rec.getString("remediationType"),
                rec.getString("actionTaken"),
                ((Number) rec.getValue("confirmedAt")).longValue(),
                rec.getString("confirmedBy"),
                rec.getString("source")
        );
    }

    private List<String> parseCommaSeparated(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
