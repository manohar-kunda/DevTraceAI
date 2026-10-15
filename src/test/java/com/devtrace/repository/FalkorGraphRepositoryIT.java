package com.devtrace.repository;

import com.devtrace.config.EnvLoader;
import com.devtrace.model.*;
import com.devtrace.seed.FalkorSchemaBootstrap;
import com.devtrace.seed.FalkorSeedService;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FalkorGraphRepositoryIT {

    private static final String TEST_GRAPH = "devtrace_test";
    private static Driver driver;
    private static FalkorGraphRepository repository;

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

        FalkorSchemaBootstrap bootstrap = new FalkorSchemaBootstrap(driver);
        FalkorSeedService seedService = new FalkorSeedService(driver, bootstrap);
        seedService.seed(TEST_GRAPH, true);

        repository = new FalkorGraphRepository(driver);
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
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    @DisplayName("loadServices loads all 10 services with parsed aliases and metadata")
    void testLoadServices() {
        List<ServiceNode> services = repository.loadServices(TEST_GRAPH);
        assertEquals(10, services.size(), "Must load all 10 services from golden scenario");

        ServiceNode payment = services.stream()
                .filter(s -> s.id().equals("service-payment"))
                .findFirst()
                .orElseThrow();

        assertEquals("payment-service", payment.name());
        assertEquals("Payments Core", payment.team());
        assertEquals("MICROSERVICE", payment.serviceType());
        assertEquals(1, payment.criticalTier());
        assertTrue(payment.aliases().contains("payment"));
        assertTrue(payment.aliases().contains("checkout-payment"));
    }

    @Test
    @DisplayName("loadErrorSignatures loads all signatures with associated service IDs")
    void testLoadErrorSignatures() {
        List<ErrorSignatureNode> signatures = repository.loadErrorSignatures(TEST_GRAPH);
        assertEquals(3, signatures.size(), "Must load all 3 error signatures");

        ErrorSignatureNode poolTimeout = signatures.stream()
                .filter(e -> e.id().equals("err-sig-payment-pool-timeout"))
                .findFirst()
                .orElseThrow();

        assertEquals("TIMEOUT", poolTimeout.errorType());
        assertTrue(poolTimeout.keywords().contains("payment timeout"));
        assertEquals(List.of("service-payment"), poolTimeout.associatedServiceIds(),
                "Associated services must resolve to service-payment via historical incident AFFECTS link");
    }

    @Test
    @DisplayName("findDependents on payment-service returns exactly documented callers with correct hop counts")
    void testFindDependentsPaymentService() {
        DependentsResult result = repository.findDependents(TEST_GRAPH, "service-payment", 4, 20);

        assertEquals("service-payment", result.targetServiceId());

        // Direct (1 hop)
        assertEquals(1, result.directDependents().size());
        ServiceHop direct = result.directDependents().get(0);
        assertEquals("service-order", direct.serviceId());
        assertEquals(1, direct.hops());

        // Indirect (2+ hops)
        Set<String> indirectIds = result.indirectDependents().stream()
                .map(ServiceHop::serviceId)
                .collect(Collectors.toSet());
        assertEquals(Set.of("service-checkout", "service-gateway"), indirectIds);

        // Verify each service is listed ONCE with minimum hops
        List<ServiceHop> all = result.allDependents();
        assertEquals(3, all.size(), "Exactly 3 distinct callers must be returned");

        // Callee databases and disjoint branches must NOT be present
        Set<String> allIds = all.stream().map(ServiceHop::serviceId).collect(Collectors.toSet());
        assertFalse(allIds.contains("service-postgres-payments"));
        assertFalse(allIds.contains("service-redis-cache"));
        assertFalse(allIds.contains("service-auth"));
        assertFalse(allIds.contains("service-inventory"));
        assertFalse(allIds.contains("service-notification"));
    }

    @Test
    @DisplayName("findDependencies on checkout-service returns all downstream dependencies partitioned by depth")
    void testFindDependenciesCheckoutService() {
        DependenciesResult result = repository.findDependencies(TEST_GRAPH, "service-checkout", 4, 20);

        assertEquals("service-checkout", result.sourceServiceId());

        // Direct (1 hop): order-service, inventory-service
        Set<String> directIds = result.directDependencies().stream()
                .map(ServiceHop::serviceId)
                .collect(Collectors.toSet());
        assertEquals(Set.of("service-order", "service-inventory"), directIds);

        // Indirect (2+ hops): payment-service, postgres-orders, postgres-payments, redis-cache
        Set<String> indirectIds = result.indirectDependencies().stream()
                .map(ServiceHop::serviceId)
                .collect(Collectors.toSet());
        assertEquals(Set.of("service-payment", "service-postgres-orders", "service-postgres-payments", "service-redis-cache"), indirectIds);

        // Upstream callers (gateway) and independent branches (auth, notification) must NOT be present
        Set<String> allIds = result.allDependencies().stream().map(ServiceHop::serviceId).collect(Collectors.toSet());
        assertFalse(allIds.contains("service-gateway"));
        assertFalse(allIds.contains("service-auth"));
        assertFalse(allIds.contains("service-notification"));
    }

    @Test
    @DisplayName("findDependencyPath discovers shortest directed path from checkout to payment")
    void testFindDependencyPath() {
        Optional<DependencyPath> pathOpt = repository.findDependencyPath(
                TEST_GRAPH, "service-checkout", "service-payment", 4
        );

        assertTrue(pathOpt.isPresent(), "Path from checkout to payment must exist");
        DependencyPath path = pathOpt.get();

        assertEquals("service-checkout", path.fromServiceId());
        assertEquals("service-payment", path.toServiceId());
        assertEquals(List.of("service-checkout", "service-order", "service-payment"), path.serviceIds());
        assertEquals(2, path.hopCount());
    }

    @Test
    @DisplayName("Depth limit is strictly respected by traversal queries")
    void testDepthLimitRespected() {
        // With maxDepth = 1, only direct callers (order-service) must be returned
        DependentsResult resultDepth1 = repository.findDependents(TEST_GRAPH, "service-payment", 1, 20);

        assertEquals(1, resultDepth1.allDependents().size());
        assertEquals("service-order", resultDepth1.allDependents().get(0).serviceId());
        assertTrue(resultDepth1.indirectDependents().isEmpty(), "Indirect callers must be excluded when maxDepth = 1");
    }

    @Test
    @DisplayName("Direction is strictly respected: inverse DEPENDS_ON query returns empty")
    void testDirectionRespected() {
        // DEPENDS_ON points checkout -> order -> payment. Querying payment -> checkout must return empty.
        Optional<DependencyPath> reversePath = repository.findDependencyPath(
                TEST_GRAPH, "service-payment", "service-checkout", 4
        );

        assertTrue(reversePath.isEmpty(), "No forward DEPENDS_ON path exists from payment to checkout");
    }

    @Test
    @DisplayName("Cycle handling: Traversal terminates cleanly with no duplicate rows when cyclic dependencies exist")
    void testCycleHandling() {
        Graph graph = driver.graph(TEST_GRAPH);

        try {
            // Temporarily inject a cyclic dependency: test-cycle-a <-> test-cycle-b
            graph.query("""
                CREATE (a:Service {id: 'test-cycle-a', name: 'cycle-a', serviceType: 'MICROSERVICE'})
                CREATE (b:Service {id: 'test-cycle-b', name: 'cycle-b', serviceType: 'MICROSERVICE'})
                CREATE (a)-[:DEPENDS_ON]->(b)
                CREATE (b)-[:DEPENDS_ON]->(a)
            """);

            // Query dependents of test-cycle-b with depth = 5
            DependentsResult result = repository.findDependents(TEST_GRAPH, "test-cycle-b", 5, 20);

            // test-cycle-a is caller at hop 1
            // test-cycle-b is caller at hop 2 via (b -> a -> b)
            // Each service MUST appear at most once in allDependents with minimum hops
            List<ServiceHop> all = result.allDependents();
            Set<String> uniqueIds = all.stream().map(ServiceHop::serviceId).collect(Collectors.toSet());

            assertEquals(uniqueIds.size(), all.size(), "Cycle must NOT generate duplicate rows in result");
            assertTrue(uniqueIds.contains("test-cycle-a"), "Direct caller must be present");

            ServiceHop hopA = all.stream().filter(h -> h.serviceId().equals("test-cycle-a")).findFirst().orElseThrow();
            assertEquals(1, hopA.hops(), "Minimum hop count for direct caller in cycle must be 1");

        } finally {
            // Clean up cycle nodes so no other tests are impacted
            try {
                graph.query("MATCH (s:Service) WHERE s.id STARTS WITH 'test-cycle-' DETACH DELETE s");
            } catch (Exception ignored) {
            }
        }
    }
}
