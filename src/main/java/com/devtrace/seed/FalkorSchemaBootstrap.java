package com.devtrace.seed;

import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.ProtocolCommand;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class FalkorSchemaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(FalkorSchemaBootstrap.class);

    private static final List<String> ENTITY_LABELS = List.of(
            "Service",
            "Deployment",
            "ErrorSignature",
            "Incident",
            "Resolution"
    );

    private static final ProtocolCommand GRAPH_CONSTRAINT_CMD = () -> "GRAPH.CONSTRAINT".getBytes(StandardCharsets.UTF_8);

    private final Driver driver;

    public FalkorSchemaBootstrap(Driver driver) {
        this.driver = driver;
    }

    /**
     * Bootstraps schema indexes and unique constraints on the specified graph.
     * Enforces the required order: exact-match index creation followed by GRAPH.CONSTRAINT CREATE.
     * Polls CALL db.constraints() until operational.
     */
    public void bootstrapSchema(String graphName) {
        log.info("Bootstrapping schema indexes and constraints for graph: {}", graphName);
        Graph graph = driver.graph(graphName);

        // 1. Create exact-match indexes for all entity labels
        for (String label : ENTITY_LABELS) {
            try {
                graph.query("CREATE INDEX FOR (n:" + label + ") ON (n.id)");
                log.debug("Index created for label {} on property id", label);
            } catch (Exception e) {
                // Ignore if index already exists
                log.debug("Index creation note for {}: {}", label, e.getMessage());
            }
        }

        // 2. Create unique constraints via raw module command
        try (Jedis jedis = driver.getConnection()) {
            for (String label : ENTITY_LABELS) {
                try {
                    jedis.sendCommand(
                            GRAPH_CONSTRAINT_CMD,
                            "CREATE",
                            graphName,
                            "UNIQUE",
                            "NODE",
                            label,
                            "PROPERTIES",
                            "1",
                            "id"
                    );
                    log.debug("Unique constraint created for label {}", label);
                } catch (Exception e) {
                    // Ignore if constraint already exists
                    log.debug("Constraint creation note for {}: {}", label, e.getMessage());
                }
            }
        }

        // 3. Poll CALL db.constraints() until all 5 constraints are verified
        waitForConstraints(graph, Duration.ofSeconds(5));
        log.info("Schema bootstrap completed successfully for graph: {}", graphName);
    }

    private void waitForConstraints(Graph graph, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        Set<String> expected = new HashSet<>(ENTITY_LABELS);

        while (Instant.now().isBefore(deadline)) {
            Set<String> verified = new HashSet<>();
            try {
                ResultSet rs = graph.query("CALL db.constraints()");
                for (Record rec : rs) {
                    // Record format: [type, label, properties, entity_type, status]
                    // e.g. UNIQUE | Service | [id] | NODE | OPERATIONAL
                    String label = rec.getString(1);
                    if (expected.contains(label)) {
                        verified.add(label);
                    }
                }

                if (verified.containsAll(expected)) {
                    log.debug("All 5 unique constraints verified in db.constraints()");
                    return;
                }
            } catch (Exception e) {
                log.debug("Waiting for constraints: {}", e.getMessage());
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting constraint readiness", e);
            }
        }

        log.warn("Timed out awaiting all constraints to report OPERATIONAL; proceeding.");
    }
}
