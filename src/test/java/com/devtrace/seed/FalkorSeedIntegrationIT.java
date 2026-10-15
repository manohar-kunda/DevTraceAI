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
    @DisplayName("Act 2 Blast Radius: Verified upstream callers of payment-service with exact set equality & serviceType absence")
    void testAct2BlastRadius() {
        Graph graph = driver.graph(TEST_GRAPH);

        ResultSet rs = graph.query("""
            MATCH path = (d:Service)-[:DEPENDS_ON*1..4]->(t:Service {id: 'service-payment'})
            RETURN d.id AS id, d.serviceType AS serviceType, length(path) AS hops
            ORDER BY hops ASC, d.id ASC
        """);

        List<String> callerIds = new ArrayList<>();
        List<Long> hopCounts = new ArrayList<>();
        java.util.Set<String> returnedServiceTypes = new java.util.HashSet<>();

        for (Record rec : rs) {
            callerIds.add(rec.getString("id"));
            returnedServiceTypes.add(rec.getString("serviceType"));
            hopCounts.add(((Number) rec.getValue("hops")).longValue());
        }

        // 1. EXACT set equality for returned caller IDs
        java.util.Set<String> expectedCallers = java.util.Set.of("service-order", "service-checkout", "service-gateway");
        assertEquals(expectedCallers, new java.util.HashSet<>(callerIds), "Returned callers must exactly match the documented set");
        assertEquals(List.of(1L, 2L, 3L), hopCounts, "Hops must correspond to direct (1), 2-hop, and 3-hop dependents");

        // 2. Explicit absence check by serviceType (DATABASE, CACHE)
        assertFalse(returnedServiceTypes.contains("DATABASE"), "DATABASE nodes must never appear in service-level blast radius callers");
        assertFalse(returnedServiceTypes.contains("CACHE"), "CACHE nodes must never appear in service-level blast radius callers");

        // 3. Independent cypher check confirming zero DATABASE or CACHE nodes depend on payment-service
        ResultSet nonServiceRs = graph.query("""
            MATCH (d:Service)-[:DEPENDS_ON*1..4]->(t:Service {id: 'service-payment'})
            WHERE d.serviceType IN ['DATABASE', 'CACHE']
            RETURN count(d) AS cnt
        """);
        long nonServiceCount = ((Number) nonServiceRs.iterator().next().getValue("cnt")).longValue();
        assertEquals(0L, nonServiceCount, "Zero DATABASE or CACHE nodes should be upstream callers of payment-service");
    }

    @Test
    @Order(6)
    @DisplayName("Date integrity: Seeded timestamps match the date embedded in their IDs")
    void testTimestampDateIntegrity() {
        Graph graph = driver.graph(TEST_GRAPH);

        // Check Incidents
        ResultSet incRs = graph.query("MATCH (i:Incident) RETURN i.id AS id, i.reportedAt AS ts");
        for (Record rec : incRs) {
            String id = rec.getString("id"); // e.g. inc-2026-08-12-pay
            long ts = ((Number) rec.getValue("ts")).longValue();
            String dateInId = id.substring(4, 14); // 2026-08-12
            String actualDate = java.time.Instant.ofEpochMilli(ts)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .toString();
            assertEquals(dateInId, actualDate, "Incident timestamp must fall on the exact calendar date embedded in ID: " + id);
        }

        // Check Resolutions
        ResultSet resRs = graph.query("MATCH (r:Resolution)-[:RESOLVES]->(i:Incident) RETURN r.id AS resId, r.confirmedAt AS rTs, i.reportedAt AS iTs");
        for (Record rec : resRs) {
            long rTs = ((Number) rec.getValue("rTs")).longValue();
            long iTs = ((Number) rec.getValue("iTs")).longValue();
            assertTrue(rTs > iTs, "Resolution confirmedAt must be strictly after incident reportedAt");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Safety check: reset is forbidden on non-whitelisted graphs")
    void testResetSafetyRestriction() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            seedService.seed("arbitrary_production_graph", true);
        });
        assertTrue(ex.getMessage().contains("Safety violation"), "Must refuse reset on non-whitelisted graphs");
    }
}
