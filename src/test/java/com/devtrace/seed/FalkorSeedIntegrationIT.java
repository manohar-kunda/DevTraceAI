package com.devtrace.seed;

import com.devtrace.config.EnvLoader;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FalkorSeedIntegrationIT {

    private static final String TEST_GRAPH = "devtrace_test";
    private static Driver driver;
    private static FalkorSchemaBootstrap schemaBootstrap;
    private static FalkorSeedService seedService;

    @BeforeAll
    static void setUp() {
        EnvLoader.loadLocalEnv();
        String host = System.getProperty("FALKORDB_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("FALKORDB_PORT", "6379"));
        String password = System.getProperty("FALKORDB_PASSWORD");

        driver = FalkorDB.builder()
                .host(host)
                .port(port)
                .credentials("default", password)
                .build();

        schemaBootstrap = new FalkorSchemaBootstrap(driver);
        seedService = new FalkorSeedService(driver, schemaBootstrap);
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            try {
                driver.graph(TEST_GRAPH).deleteGraph();
            } catch (Exception ignored) {
            }
            try {
                driver.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Initial seed creates exact expected node and relationship counts")
    void testInitialSeed() {
        SeedResult result = seedService.seed(TEST_GRAPH, true);

        assertEquals(24L, result.totalNodes(), "Total nodes should be 24 (10 services + 5 deployments + 3 signatures + 3 incidents + 3 resolutions)");
        assertEquals(26L, result.totalRelationships(), "Total relationships should be 26");

        Map<String, Long> byLabel = result.nodeCountsByLabel();
        assertEquals(10L, byLabel.get("Service"));
        assertEquals(5L, byLabel.get("Deployment"));
        assertEquals(3L, byLabel.get("ErrorSignature"));
        assertEquals(3L, byLabel.get("Incident"));
        assertEquals(3L, byLabel.get("Resolution"));
    }

    @Test
    @Order(2)
    @DisplayName("Idempotency: Re-running seed on populated graph produces zero duplicates")
    void testSeedIdempotency() {
        // Run seed a second time with reset = false
        SeedResult result2 = seedService.seed(TEST_GRAPH, false);

        assertEquals(24L, result2.totalNodes(), "Total nodes must remain exactly 24 after re-seeding");
        assertEquals(26L, result2.totalRelationships(), "Total relationships must remain exactly 26 after re-seeding");
    }

    @Test
    @Order(3)
    @DisplayName("ON MATCH SET updates modified properties to canonical state")
    void testOnMatchSetUpdates() {
        Graph graph = driver.graph(TEST_GRAPH);

        // Mutate team property
        graph.query("MATCH (s:Service {id: 'service-payment'}) SET s.team = 'Temporary Corrupted Team'");
        ResultSet rs = graph.query("MATCH (s:Service {id: 'service-payment'}) RETURN s.team AS team");
        assertEquals("Temporary Corrupted Team", rs.iterator().next().getString("team"));

        // Re-run seed with reset = false
        seedService.seed(TEST_GRAPH, false);

        // Verify ON MATCH SET restored the canonical team property
        ResultSet rsAfter = graph.query("MATCH (s:Service {id: 'service-payment'}) RETURN s.team AS team");
        assertEquals("Payments Core", rsAfter.iterator().next().getString("team"), "ON MATCH SET should restore canonical team name");
    }

    @Test
    @Order(4)
    @DisplayName("Constraint enforcement: Duplicate node ID is rejected by FalkorDB")
    void testUniqueConstraintRejection() {
        Graph graph = driver.graph(TEST_GRAPH);

        Exception exception = assertThrows(Exception.class, () -> {
            graph.query("CREATE (s:Service {id: 'service-payment', name: 'Duplicate Service'})");
        });

        String msg = exception.getMessage().toLowerCase();
        assertTrue(msg.contains("unique constraint") || msg.contains("violation"),
                "Exception must indicate a unique constraint violation, got: " + exception.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("Act 2 Blast Radius: Verified upstream callers of payment-service")
    void testAct2BlastRadius() {
        Graph graph = driver.graph(TEST_GRAPH);

        ResultSet rs = graph.query("""
            MATCH path = (d:Service)-[:DEPENDS_ON*1..4]->(t:Service {id: 'service-payment'})
            RETURN d.id AS id, length(path) AS hops
            ORDER BY hops ASC, d.id ASC
        """);

        List<String> callerIds = new ArrayList<>();
        List<Long> hopCounts = new ArrayList<>();

        for (Record rec : rs) {
            callerIds.add(rec.getString("id"));
            hopCounts.add(((Number) rec.getValue("hops")).longValue());
        }

        // Expected callers in blast radius
        assertEquals(List.of("service-order", "service-checkout", "service-gateway"), callerIds);
        assertEquals(List.of(1L, 2L, 3L), hopCounts);

        // Verify excluded nodes
        assertFalse(callerIds.contains("service-postgres-payments"), "postgres-payments is a callee, not an upstream caller");
        assertFalse(callerIds.contains("service-redis-cache"), "redis-cache is a callee, not an upstream caller");
        assertFalse(callerIds.contains("service-auth"), "auth-service is an independent branch");
        assertFalse(callerIds.contains("service-inventory"), "inventory-service is a sibling branch");
        assertFalse(callerIds.contains("service-notification"), "notification-service is an independent branch");
    }

    @Test
    @Order(6)
    @DisplayName("Safety check: reset is forbidden on non-whitelisted graphs")
    void testResetSafetyRestriction() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            seedService.seed("arbitrary_production_graph", true);
        });
        assertTrue(ex.getMessage().contains("Safety violation"), "Must refuse reset on non-whitelisted graphs");
    }
}
