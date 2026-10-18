package com.devtrace.controller;

import com.devtrace.config.EnvLoader;
import com.devtrace.dto.ConfirmResolutionRequest;
import com.devtrace.dto.CreateIncidentRequest;
import com.devtrace.seed.FalkorSchemaBootstrap;
import com.devtrace.seed.FalkorSeedService;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "devtrace.falkordb.graph=devtrace_test",
        "DEVTRACE_API_TOKEN=test-it-token-999"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncidentControllerIT {

    private static final String TEST_GRAPH = "devtrace_test";
    private static final String IT_TOKEN = "test-it-token-999";
    private static final long T0 = 1792058400000L; // 2026-10-15 10:00:00 UTC

    private static Driver driver;
    private static FalkorSeedService seedService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @BeforeAll
    static void setUpAll() {
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
        seedService = new FalkorSeedService(driver, bootstrap);
        seedService.seed(TEST_GRAPH, true);
    }

    @AfterAll
    static void tearDownAll() {
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
    @Order(1)
    @DisplayName("HTTP End-to-End: Full incident lifecycle flow across REST endpoints")
    void testFullIncidentLifecycleFlow() throws Exception {
        String incident1Id = "inc-2026-10-15-checkout-001";

        // Step 1: Create incident -> 201 Created
        CreateIncidentRequest createReq = new CreateIncidentRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                "checkout-service",
                "err-sig-payment-pool-timeout",
                T0 - 1800000L,
                incident1Id
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value(incident1Id))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.created").value(true));

        // Step 2: Investigate incident -> 200 OK (read-only)
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HYPOTHESES_RANKED"))
                .andExpect(jsonPath("$.hypotheses[0].serviceId").value("service-payment"))
                .andExpect(jsonPath("$.hypotheses[0].tier").value("STRONG"));

        // Step 3: Confirm resolution WITHOUT token -> 401 Unauthorized
        ConfirmResolutionRequest resReq = new ConfirmResolutionRequest(
                "service-payment",
                "Reverted HikariCP connection pool constraints via hotfix config deploy",
                "CONFIG_CHANGE",
                "Patched ConfigMap to set maximumPoolSize=40, connectionTimeout=25000ms, and restarted payment-service pods to restore connection pool headroom.",
                "sre-alice",
                "dep-payment-v2.4.1"
        );

        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(containsString("Missing required 'X-DevTrace-Token'")));

        // Step 4: Confirm resolution WITH token -> 201 Created
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("res-" + incident1Id))
                .andExpect(jsonPath("$.source").value("user-confirmed"))
                .andExpect(jsonPath("$.confirmedBy").value("sre-alice"));

        // Step 5: Repeat identical confirmation -> 200 OK
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("res-" + incident1Id));

        // Step 6: Conflicting payload confirmation -> 409 Conflict
        ConfirmResolutionRequest conflictReq = new ConfirmResolutionRequest(
                "service-payment",
                "Totally different remediation title",
                "RESTART",
                "Restarted payment service pods",
                "sre-bob"
        );
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));

        // Step 7: Unknown incident resolution confirmation -> 404 Not Found
        mockMvc.perform(post("/api/incidents/inc-ghost-999/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Step 8: Invalid body (validation failure) -> 400 Bad Request
        ConfirmResolutionRequest invalidBodyReq = new ConfirmResolutionRequest(
                "", "", "CONFIG_CHANGE", "Action", "sre"
        );
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBodyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Step 9: Investigate a SECOND incident and show both confirmed resolutions
        String incident2Id = "inc-2026-10-15-checkout-002";
        CreateIncidentRequest createReq2 = new CreateIncidentRequest(
                "Orders timing out during payment gateway handoff with connection pool exhaustion",
                "checkout-service",
                "err-sig-payment-pool-timeout",
                T0 + 9000000L,
                incident2Id
        );
        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/incidents/" + incident2Id + "/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HYPOTHESES_RANKED"))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents", hasSize(2)))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[0].incidentId").value(incident1Id))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[0].confirmedResolution.source").value("user-confirmed"))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[1].incidentId").value("inc-2026-08-12-pay"))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[1].confirmedResolution.source").value("synthetic"))
                .andExpect(jsonPath("$.suggestedNextSteps[0].sourceResolutionId").value("res-" + incident1Id))
                .andExpect(jsonPath("$.suggestedNextSteps[1].sourceResolutionId").value("res-pay-timeout-fix"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/incidents/{id} returns RESOLVED status and full confirmed resolution record")
    void testGetResolvedIncident() throws Exception {
        mockMvc.perform(get("/api/incidents/inc-2026-10-15-checkout-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("inc-2026-10-15-checkout-001"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.confirmedResolution.id").value("res-inc-2026-10-15-checkout-001"))
                .andExpect(jsonPath("$.confirmedResolution.confirmedBy").value("sre-alice"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/services/{id}/blast-radius returns callers bounded by depth")
    void testGetBlastRadiusEndpoint() throws Exception {
        mockMvc.perform(get("/api/services/service-payment/blast-radius?maxDepth=4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetServiceId").value("service-payment"))
                .andExpect(jsonPath("$.directDependents", hasSize(1)))
                .andExpect(jsonPath("$.directDependents[0].serviceId").value("service-order"))
                .andExpect(jsonPath("$.totalDependents").value(3));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/demo/golden-incident returns golden scenario with demo: true")
    void testGetDemoGoldenIncident() throws Exception {
        mockMvc.perform(get("/api/demo/golden-incident"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.incidentId").value("inc-2026-10-15-checkout-001"))
                .andExpect(jsonPath("$.serviceHint").doesNotExist())
                .andExpect(jsonPath("$.errorHint").doesNotExist())
                .andExpect(jsonPath("$.startedAt").value(T0));
    }

    @Test
    @Order(5)
    @DisplayName("Free-text driven incident investigation without hints resolves entry points with FREE_TEXT_KEYWORD and identical ranking")
    void testFreeTextDrivenInvestigationWithoutHints() throws Exception {
        seedService.seed(TEST_GRAPH, true);
        String freeTextIncidentId = "inc-2026-10-15-free-text-001";
        CreateIncidentRequest createReq = new CreateIncidentRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                null,
                null,
                T0,
                freeTextIncidentId
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value(freeTextIncidentId))
                .andExpect(jsonPath("$.status").value("OPEN"));

        // Investigate without hints: free-text resolver must match FREE_TEXT_KEYWORD
        mockMvc.perform(post("/api/incidents/" + freeTextIncidentId + "/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HYPOTHESES_RANKED"))
                .andExpect(jsonPath("$.entryPoints.service.serviceId").value("service-checkout"))
                .andExpect(jsonPath("$.entryPoints.service.evidence.matchSource").value("FREE_TEXT_KEYWORD"))
                .andExpect(jsonPath("$.entryPoints.errorSignature.signatureId").value("err-sig-payment-pool-timeout"))
                .andExpect(jsonPath("$.entryPoints.errorSignature.evidence.matchSource").value("FREE_TEXT_KEYWORD"))
                // Verified ranking: payment STRONG rank 1, inventory MODERATE rank 2
                .andExpect(jsonPath("$.hypotheses", hasSize(2)))
                .andExpect(jsonPath("$.hypotheses[0].serviceId").value("service-payment"))
                .andExpect(jsonPath("$.hypotheses[0].tier").value("STRONG"))
                .andExpect(jsonPath("$.hypotheses[0].rank").value(1))
                .andExpect(jsonPath("$.hypotheses[1].serviceId").value("service-inventory"))
                .andExpect(jsonPath("$.hypotheses[1].tier").value("MODERATE"))
                .andExpect(jsonPath("$.hypotheses[1].rank").value(2));
    }

    @Test
    @Order(6)
    @DisplayName("Read-only investigation: calling investigation endpoint twice does not mutate graph nodes or relationships")
    void testInvestigationIsStrictlyReadOnly() throws Exception {
        seedService.seed(TEST_GRAPH, true);
        String roIncidentId = "inc-2026-10-15-readonly-001";
        CreateIncidentRequest createReq = new CreateIncidentRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                null,
                null,
                T0,
                roIncidentId
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        var graph = driver.graph(TEST_GRAPH);
        long initialNodes = ((Number) graph.query("MATCH (n) RETURN count(n) AS cnt").iterator().next().getValue("cnt")).longValue();
        long initialRels = ((Number) graph.query("MATCH ()-[r]->() RETURN count(r) AS cnt").iterator().next().getValue("cnt")).longValue();

        // First investigation call
        mockMvc.perform(post("/api/incidents/" + roIncidentId + "/investigation"))
                .andExpect(status().isOk());

        long afterFirstNodes = ((Number) graph.query("MATCH (n) RETURN count(n) AS cnt").iterator().next().getValue("cnt")).longValue();
        long afterFirstRels = ((Number) graph.query("MATCH ()-[r]->() RETURN count(r) AS cnt").iterator().next().getValue("cnt")).longValue();

        assertEquals(initialNodes, afterFirstNodes, "Node count must not change after first investigation");
        assertEquals(initialRels, afterFirstRels, "Relationship count must not change after first investigation");

        // Second investigation call
        mockMvc.perform(post("/api/incidents/" + roIncidentId + "/investigation"))
                .andExpect(status().isOk());

        long afterSecondNodes = ((Number) graph.query("MATCH (n) RETURN count(n) AS cnt").iterator().next().getValue("cnt")).longValue();
        long afterSecondRels = ((Number) graph.query("MATCH ()-[r]->() RETURN count(r) AS cnt").iterator().next().getValue("cnt")).longValue();

        assertEquals(initialNodes, afterSecondNodes, "Node count must not change after second investigation");
        assertEquals(initialRels, afterSecondRels, "Relationship count must not change after second investigation");
    }

    @Test
    @Order(7)
    @DisplayName("Concurrency: 50 parallel investigations return 200 with identical ranking")
    void testFiftyParallelInvestigationsReturnOkWithIdenticalRanking() throws Exception {
        seedService.seed(TEST_GRAPH, true);
        String concIncidentId = "inc-2026-10-15-conc-inv-001";
        CreateIncidentRequest createReq = new CreateIncidentRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                "service-checkout",
                "err-sig-payment-pool-timeout",
                T0,
                concIncidentId
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        int concurrency = 50;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(16);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(concurrency);
        java.util.List<Long> latenciesMs = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    mockMvc.perform(post("/api/incidents/" + concIncidentId + "/investigation"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("HYPOTHESES_RANKED"))
                            .andExpect(jsonPath("$.hypotheses[0].serviceId").value("service-payment"))
                            .andExpect(jsonPath("$.hypotheses[0].tier").value("STRONG"))
                            .andExpect(jsonPath("$.hypotheses[0].rank").value(1))
                            .andExpect(jsonPath("$.hypotheses[1].serviceId").value("service-inventory"))
                            .andExpect(jsonPath("$.hypotheses[1].tier").value("MODERATE"))
                            .andExpect(jsonPath("$.hypotheses[1].rank").value(2));
                    long durationMs = (System.nanoTime() - start) / 1_000_000L;
                    latenciesMs.add(durationMs);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 50 parallel requests must complete within 30 seconds");
        assertTrue(errors.isEmpty(), "No parallel requests must fail: " + errors);
        assertEquals(concurrency, latenciesMs.size(), "All 50 requests must record latency");

        java.util.Collections.sort(latenciesMs);
        long p50 = latenciesMs.get((int) (concurrency * 0.50));
        long p95 = latenciesMs.get((int) (concurrency * 0.95));
        System.out.printf("[CONCURRENCY_SANITY_CHECK] 50 Parallel Investigations: p50 = %d ms, p95 = %d ms, min = %d ms, max = %d ms%n",
                p50, p95, latenciesMs.get(0), latenciesMs.get(latenciesMs.size() - 1));
    }

    @Test
    @Order(8)
    @DisplayName("Concurrency: 10 parallel identical resolution confirmations result in exactly 1 Resolution node and 201/200 responses")
    void testTenParallelIdenticalConfirmationsCreateExactlyOneResolution() throws Exception {
        seedService.seed(TEST_GRAPH, true);
        String idempIncidentId = "inc-2026-10-15-conc-res-idemp";
        CreateIncidentRequest createReq = new CreateIncidentRequest(
                "Payment timeouts post deployment v2.4.1",
                "service-checkout",
                "err-sig-payment-pool-timeout",
                T0,
                idempIncidentId
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        ConfirmResolutionRequest resReq = new ConfirmResolutionRequest(
                "service-payment",
                "Emergency pool size rollback on payment datasource",
                "CONFIG_CHANGE",
                "Reverted maximumPoolSize to 50 via ConfigMap patch and rolling restarted payment pods.",
                "sre-oncall-lead",
                "dep-payment-v2.4.1"
        );
        String payloadJson = objectMapper.writeValueAsString(resReq);

        int concurrency = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(concurrency);
        java.util.List<Integer> statusCodes = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var mvcResult = mockMvc.perform(post("/api/incidents/" + idempIncidentId + "/resolution")
                                    .header("X-DevTrace-Token", IT_TOKEN)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payloadJson))
                            .andReturn();
                    statusCodes.add(mvcResult.getResponse().getStatus());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 10 parallel identical resolution requests must complete");
        assertTrue(errors.isEmpty(), "No unhandled exceptions during parallel execution: " + errors);
        assertEquals(concurrency, statusCodes.size());

        assertTrue(statusCodes.stream().allMatch(sc -> sc == 201 || sc == 200),
                "All response codes must be 201 or 200, actual: " + statusCodes);
        assertTrue(statusCodes.contains(201), "At least one request must receive 201 Created");

        var graph = driver.graph(TEST_GRAPH);
        var result = graph.query("MATCH (r:Resolution)-[:RESOLVES]->(i:Incident {id: '" + idempIncidentId + "'}) RETURN count(r) AS cnt");
        long resolutionCount = ((Number) result.iterator().next().getValue("cnt")).longValue();
        assertEquals(1, resolutionCount, "Exactly one Resolution node must exist in FalkorDB");
    }

    @Test
    @Order(9)
    @DisplayName("Concurrency: 10 parallel conflicting confirmations store exactly 1 resolution and return 409 for remainder")
    void testTenParallelConflictingConfirmationsStoreOneResolutionAndRejectOthersWith409() throws Exception {
        seedService.seed(TEST_GRAPH, true);
        String conflictIncidentId = "inc-2026-10-15-conc-res-conflict";
        CreateIncidentRequest createReq = new CreateIncidentRequest(
                "Payment timeouts post deployment v2.4.1",
                "service-checkout",
                "err-sig-payment-pool-timeout",
                T0,
                conflictIncidentId
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        int concurrency = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(concurrency);
        java.util.List<Integer> statusCodes = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConfirmResolutionRequest req = new ConfirmResolutionRequest(
                            "service-payment",
                            "Conflicting Resolution #" + index,
                            "CONFIG_CHANGE",
                            "Action details for conflicting fix #" + index,
                            "sre-tester-" + index,
                            null
                    );
                    var mvcResult = mockMvc.perform(post("/api/incidents/" + conflictIncidentId + "/resolution")
                                    .header("X-DevTrace-Token", IT_TOKEN)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();
                    statusCodes.add(mvcResult.getResponse().getStatus());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 10 parallel conflicting requests must complete");
        assertTrue(errors.isEmpty(), "No unhandled exceptions: " + errors);
        assertEquals(concurrency, statusCodes.size());

        long count201 = statusCodes.stream().filter(sc -> sc == 201).count();
        long count409 = statusCodes.stream().filter(sc -> sc == 409).count();

        assertEquals(1, count201, "Exactly 1 conflicting confirmation must win (201 Created), actual: " + statusCodes);
        assertEquals(9, count409, "Remaining 9 conflicting confirmations must be rejected with 409 Conflict, actual: " + statusCodes);

        var graph = driver.graph(TEST_GRAPH);
        var result = graph.query("MATCH (r:Resolution)-[:RESOLVES]->(i:Incident {id: '" + conflictIncidentId + "'}) RETURN count(r) AS cnt");
        long resolutionCount = ((Number) result.iterator().next().getValue("cnt")).longValue();
        assertEquals(1, resolutionCount, "Exactly one Resolution node must exist in FalkorDB");
    }
}
