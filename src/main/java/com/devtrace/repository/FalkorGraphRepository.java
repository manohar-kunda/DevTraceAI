package com.devtrace.repository;

import com.devtrace.model.*;
import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class FalkorGraphRepository implements GraphRepository {

    private static final Logger log = LoggerFactory.getLogger(FalkorGraphRepository.class);

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
        if (errorSignatureId == null || errorSignatureId.isBlank() || candidateServiceIds == null || candidateServiceIds.isEmpty()) {
            return List.of();
        }

        Graph graph = driver.graph(graphName);
        String cypher = """
            MATCH (i:Incident)-[:EXHIBITS]->(e:ErrorSignature {id: $sigId})
            MATCH (i)-[:AFFECTS]->(s:Service)
            WHERE s.id IN $serviceIds
            OPTIONAL MATCH (r:Resolution)-[:RESOLVES]->(i)
            RETURN i.id AS incidentId, i.title AS title, i.status AS status, e.id AS errorSignatureId,
                   collect(DISTINCT s.id) AS affectedServiceIds,
                   r.id AS resId, r.title AS resTitle, r.remediationType AS resRemediationType,
                   r.actionTaken AS resActionTaken, r.confirmedAt AS resConfirmedAt, r.confirmedBy AS resConfirmedBy
            ORDER BY i.reportedAt DESC
        """;

        Map<String, Object> params = Map.of(
                "sigId", errorSignatureId,
                "serviceIds", new ArrayList<>(candidateServiceIds)
        );

        ResultSet rs = graph.readOnlyQuery(cypher, params);
        List<SimilarIncidentRecord> records = new ArrayList<>();

        for (Record rec : rs) {
            String incidentId = rec.getString("incidentId");
            String title = rec.getString("title");
            String status = rec.getString("status");
            String sigId = rec.getString("errorSignatureId");
            List<String> affectedServices = extractStringList(rec.getValue("affectedServiceIds"));

            List<String> matchReasons = List.of(
                    "Matches ErrorSignature: " + sigId,
                    "Affects candidate service(s): " + String.join(", ", affectedServices),
                    "Service is within topological candidate neighborhood"
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

                resRecord = new ResolutionRecord(resId, resTitle, resRemediationType, resActionTaken, resConfirmedAt, resConfirmedBy);
            }

            records.add(new SimilarIncidentRecord(incidentId, title, sigId, affectedServices, status, matchReasons, resRecord));
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
                   d.deployedAt AS deployedAt, d.environment AS environment, d.deployedBy AS deployedBy
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

            deployments.add(new DeploymentRecord(deploymentId, serviceId, version, deployedAt, env, deployedBy));
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
                   d.deployedAt AS deployedAt, d.environment AS environment, d.deployedBy AS deployedBy
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

            deployments.add(new DeploymentRecord(deploymentId, serviceId, version, deployedAt, env, deployedBy));
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
