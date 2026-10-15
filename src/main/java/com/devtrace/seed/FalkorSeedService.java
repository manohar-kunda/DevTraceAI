package com.devtrace.seed;

import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FalkorSeedService {

    private static final Logger log = LoggerFactory.getLogger(FalkorSeedService.class);
    private static final Set<String> ALLOWED_RESET_GRAPHS = Set.of("devtrace", "devtrace_test");

    private final Driver driver;
    private final FalkorSchemaBootstrap schemaBootstrap;

    @Value("${devtrace.seed.enabled:false}")
    private boolean seedOnStartup;

    @Value("${devtrace.seed.reset:false}")
    private boolean resetOnStartup;

    @Value("${falkordb.graph:devtrace}")
    private String defaultGraph;

    public FalkorSeedService(Driver driver, FalkorSchemaBootstrap schemaBootstrap) {
        this.driver = driver;
        this.schemaBootstrap = schemaBootstrap;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (seedOnStartup) {
            log.info("devtrace.seed.enabled is true. Seeding default graph: {}", defaultGraph);
            seed(defaultGraph, resetOnStartup);
        }
    }

    /**
     * Seeds the specified graph with the golden scenario dataset.
     *
     * @param graphName Name of the graph to seed.
     * @param reset     If true, deletes the graph before bootstrapping and seeding.
     * @return SeedResult containing entity and relationship counts.
     * @throws IllegalArgumentException if reset is requested on a non-whitelisted graph.
     */
    public SeedResult seed(String graphName, boolean reset) {
        if (reset && !ALLOWED_RESET_GRAPHS.contains(graphName)) {
            throw new IllegalArgumentException(
                    "Safety violation: Graph reset is only permitted on " + ALLOWED_RESET_GRAPHS + ", but received: " + graphName
            );
        }

        long startTime = System.currentTimeMillis();
        Graph graph = driver.graph(graphName);

        if (reset) {
            try {
                graph.deleteGraph();
                log.info("Reset graph: {}", graphName);
            } catch (Exception e) {
                log.debug("Graph {} was not present prior to reset.", graphName);
            }
        }

        // 1. Bootstrap schema (indexes + constraints)
        schemaBootstrap.bootstrapSchema(graphName);

        // 2. Load and parse seed Cypher statements
        List<String> statements = loadSeedStatements("seed/devtrace-golden-scenario.cypher");
        log.info("Executing {} seed statements against graph: {}", statements.size(), graphName);

        for (String stmt : statements) {
            try {
                graph.query(stmt);
            } catch (Exception e) {
                log.error("Failed executing seed statement:\n{}\nError: {}", stmt, e.getMessage());
                throw new IllegalStateException("Seed statement execution failed: " + e.getMessage(), e);
            }
        }

        // 3. Collect statistics
        long totalNodes = queryCount(graph, "MATCH (n) RETURN count(n) AS cnt");
        long totalRelationships = queryCount(graph, "MATCH ()-[r]->() RETURN count(r) AS cnt");

        Map<String, Long> nodeCounts = new LinkedHashMap<>();
        for (String label : List.of("Service", "Deployment", "ErrorSignature", "Incident", "Resolution")) {
            nodeCounts.put(label, queryCount(graph, "MATCH (n:" + label + ") RETURN count(n) AS cnt"));
        }

        long duration = System.currentTimeMillis() - startTime;
        SeedResult result = new SeedResult(graphName, totalNodes, totalRelationships, nodeCounts, duration);

        log.info("Seeding complete for {} in {}ms: {} nodes, {} relationships. Detail: {}",
                graphName, duration, totalNodes, totalRelationships, nodeCounts);

        return result;
    }

    public List<String> loadSeedStatements(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("Seed resource not found: " + resourcePath);
        }

        List<String> statements = new ArrayList<>();
        StringBuilder currentStmt = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                currentStmt.append(line).append(" ");
                if (line.endsWith(";")) {
                    String stmt = currentStmt.toString().trim();
                    // Remove trailing semicolon for Cypher execution
                    if (stmt.endsWith(";")) {
                        stmt = stmt.substring(0, stmt.length() - 1).trim();
                    }
                    if (!stmt.isEmpty()) {
                        statements.add(stmt);
                    }
                    currentStmt.setLength(0);
                }
            }
            if (!currentStmt.isEmpty()) {
                String stmt = currentStmt.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading seed resource: " + resourcePath, e);
        }

        return statements;
    }

    private long queryCount(Graph graph, String cypher) {
        ResultSet rs = graph.query(cypher);
        for (Record rec : rs) {
            Object val = rec.getValue("cnt");
            if (val instanceof Number num) {
                return num.longValue();
            }
        }
        return 0L;
    }
}
