package com.devtrace.service;

import com.devtrace.config.EnvLoader;
import com.devtrace.engine.DefaultInvestigationEngine;
import com.devtrace.exception.*;
import com.devtrace.model.*;
import com.devtrace.repository.FalkorGraphRepository;
import com.devtrace.resolver.DefaultEntryPointResolver;
import com.devtrace.seed.FalkorSchemaBootstrap;
import com.devtrace.seed.FalkorSeedService;
import com.devtrace.seed.SeedResult;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResolutionWriteBackIT {

    private static final String TEST_GRAPH = "devtrace_test";
    private static final long T0 = 1792058400000L; // 2026-10-15 10:00:00 UTC

    private static Driver driver;
    private static FalkorGraphRepository repository;
    private static FalkorSeedService seedService;
    private static DefaultResolutionService resolutionService;
    private static DefaultInvestigationEngine engine;
    private static Clock fixedClock;
    private static ObjectMapper objectMapper;

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

        repository = new FalkorGraphRepository(driver);
        fixedClock = Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC);
        resolutionService = new DefaultResolutionService(repository, fixedClock);

        DefaultEntryPointResolver resolver = new DefaultEntryPointResolver();
        engine = new DefaultInvestigationEngine(repository, resolver, fixedClock, 3, 86400000L);

        objectMapper = new ObjectMapper();
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

    @BeforeEach
    void resetTestGraph() {
        seedService.seed(TEST_GRAPH, true);
    }

    @Test
    @Order(1)
    @DisplayName("Act 4 Before/After: OPEN incident excluded -> Confirm resolution -> Knowledge evolution retrieves both seeded and user-confirmed")
    void testAct4BeforeAndAfterResolutionWriteBack() throws Exception {
        // 1. Create an active OPEN incident in FalkorDB: inc-2026-10-15-checkout-001
        String incidentAId = "inc-2026-10-15-checkout-001";
        IncidentInput incidentA = new IncidentInput(
                incidentAId,
                "Payment timeout surge post deployment v2.4.1",
                "OPEN",
                T0 - 1800000L, // 30 minutes before T0
                "P1",
                "service-checkout",
                "err-sig-payment-pool-timeout"
        );
        resolutionService.createOrEnsureIncident(TEST_GRAPH, incidentA);

        // 2. Run investigation BEFORE resolution confirmation
        InvestigationRequest request = new InvestigationRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                "service-checkout", "err-sig-payment-pool-timeout", T0
        );

        InvestigationReport reportBefore = engine.investigate(TEST_GRAPH, request);
        List<SimilarIncidentRecord> historyBefore = reportBefore.observedFacts().matchedHistoricalIncidents();

        // Verify: OPEN incident A is NEVER returned as historical evidence
        assertFalse(historyBefore.stream().anyMatch(h -> h.incidentId().equals(incidentAId)),
                "OPEN incident A must NOT appear in historical incidents");
        assertEquals(1, historyBefore.size(), "Only the single seeded historical incident must match before resolution");
        assertEquals("inc-2026-08-12-pay", historyBefore.get(0).incidentId());
        assertEquals("synthetic", historyBefore.get(0).confirmedResolution().source());

        // 3. User Mitigation Action: Confirm resolution on incident A via ResolutionService
        ResolutionInput resInput = new ResolutionInput(
                "service-payment",
                "Roll back HikariCP pool limit and increase timeout",
                "CONFIG_CHANGE",
                "Increased HikariCP maximumPoolSize to 50 and connectionTimeout to 30000ms on postgres-payments datasource.",
                "sre-alice",
                "dep-payment-v2.4.1"
        );

        ResolutionRecord confirmedRes = resolutionService.confirmResolution(TEST_GRAPH, incidentAId, resInput);
        assertNotNull(confirmedRes);
        assertEquals("res-" + incidentAId, confirmedRes.id());
        assertEquals("user-confirmed", confirmedRes.source());
        assertEquals("sre-alice", confirmedRes.confirmedBy());
        assertEquals(T0, confirmedRes.confirmedAt());

        // Verify incident status changed to RESOLVED
        Optional<IncidentRecord> resolvedIncident = repository.findIncidentById(TEST_GRAPH, incidentAId);
        assertTrue(resolvedIncident.isPresent());
        assertEquals("RESOLVED", resolvedIncident.get().status());

        // 4. Subsequent Investigation: Investigate incident B with the same error signature
        InvestigationRequest requestB = new InvestigationRequest(
                "Orders timing out during payment gateway handoff with connection pool exhaustion",
                "service-checkout", "err-sig-payment-pool-timeout", T0 + 3600000L
        );

        InvestigationReport reportAfter = engine.investigate(TEST_GRAPH, requestB);
        List<SimilarIncidentRecord> historyAfter = reportAfter.observedFacts().matchedHistoricalIncidents();

        // Verify: Knowledge evolution now retrieves BOTH the original seeded incident AND incident A!
        assertEquals(2, historyAfter.size(), "Historical incidents must now include BOTH seeded and user-confirmed resolutions");

        SimilarIncidentRecord matchIncidentA = historyAfter.stream()
                .filter(h -> h.incidentId().equals(incidentAId))
                .findFirst().orElseThrow();
        assertEquals("user-confirmed", matchIncidentA.confirmedResolution().source());
        assertEquals("sre-alice", matchIncidentA.confirmedResolution().confirmedBy());
        assertEquals(T0, matchIncidentA.confirmedResolution().confirmedAt());

        SimilarIncidentRecord matchSeeded = historyAfter.stream()
                .filter(h -> h.incidentId().equals("inc-2026-08-12-pay"))
                .findFirst().orElseThrow();
        assertEquals("synthetic", matchSeeded.confirmedResolution().source());
        assertEquals("sre-oncall", matchSeeded.confirmedResolution().confirmedBy());

        // 5. Print both reports' matchedHistoricalIncidents side by side
        System.out.println("\n================================================================================");
        System.out.println("ACT 4 KNOWLEDGE EVOLUTION DEMO: SIDE-BY-SIDE MATCHED HISTORICAL INCIDENTS");
        System.out.println("================================================================================");
        System.out.println("--- BEFORE RESOLUTION WRITE-BACK (OPEN incident excluded): ---");
        System.out.println(objectMapper.writeValueAsString(historyBefore));
        System.out.println("\n--- AFTER RESOLUTION WRITE-BACK (contains synthetic + user-confirmed): ---");
        System.out.println(objectMapper.writeValueAsString(historyAfter));
        System.out.println("================================================================================\n");
    }

    @Test
    @Order(2)
    @DisplayName("OPEN incident is strictly excluded from historical incident matching")
    void testOpenIncidentNeverReturnedAsHistoricalEvidence() {
        String openIncidentId = "inc-2026-10-15-active-open";
        IncidentInput openIncident = new IncidentInput(
                openIncidentId,
                "Ongoing active outage",
                "OPEN",
                T0 - 600000L,
                "P1",
                "service-payment",
                "err-sig-payment-pool-timeout"
        );
        resolutionService.createOrEnsureIncident(TEST_GRAPH, openIncident);

        List<SimilarIncidentRecord> matches = repository.findSimilarIncidents(
                TEST_GRAPH, "err-sig-payment-pool-timeout", java.util.Set.of("service-payment")
        );

        assertFalse(matches.stream().anyMatch(m -> m.incidentId().equals(openIncidentId)),
                "An OPEN incident must never be returned by findSimilarIncidents");
        assertTrue(matches.stream().allMatch(m -> "RESOLVED".equals(m.status())),
                "All matched historical incidents must have status RESOLVED");
    }

    @Test
    @Order(3)
    @DisplayName("Validation failures return controlled domain exceptions")
    void testValidationFailuresReturnControlledErrors() {
        String incidentId = "inc-2026-10-15-val-test";
        resolutionService.createOrEnsureIncident(TEST_GRAPH, new IncidentInput(incidentId, "Val Test", T0, "P2"));

        // 1. Unknown service ID -> ServiceNotFoundException
        ResolutionInput unknownServiceInput = new ResolutionInput(
                "service-non-existent", "Fix", "CONFIG_CHANGE", "Action", "sre"
        );
        assertThrows(ServiceNotFoundException.class, () ->
                resolutionService.confirmResolution(TEST_GRAPH, incidentId, unknownServiceInput)
        );

        // 2. Blank summary/title -> InvalidResolutionInputException
        ResolutionInput blankTitleInput = new ResolutionInput(
                "service-payment", "", "CONFIG_CHANGE", "Action", "sre"
        );
        assertThrows(InvalidResolutionInputException.class, () ->
                resolutionService.confirmResolution(TEST_GRAPH, incidentId, blankTitleInput)
        );

        // 3. Blank actionTaken -> InvalidResolutionInputException
        ResolutionInput blankActionInput = new ResolutionInput(
                "service-payment", "Fix", "CONFIG_CHANGE", "", "sre"
        );
        assertThrows(InvalidResolutionInputException.class, () ->
                resolutionService.confirmResolution(TEST_GRAPH, incidentId, blankActionInput)
        );

        // 4. Unknown triggering deployment -> DeploymentNotFoundException
        ResolutionInput unknownDeploymentInput = new ResolutionInput(
                "service-payment", "Fix", "CONFIG_CHANGE", "Action", "sre", "dep-non-existent"
        );
        assertThrows(DeploymentNotFoundException.class, () ->
                resolutionService.confirmResolution(TEST_GRAPH, incidentId, unknownDeploymentInput)
        );

        // 5. Unknown incident ID -> IncidentNotFoundException
        assertThrows(IncidentNotFoundException.class, () ->
                resolutionService.confirmResolution(TEST_GRAPH, "inc-ghost",
                        new ResolutionInput("service-payment", "Fix", "CONFIG_CHANGE", "Action", "sre"))
        );
    }

    @Test
    @Order(4)
    @DisplayName("Idempotency: repeat identical confirmation succeeds without duplication; conflicting payload throws")
    void testIdempotencySuccessAndConflictDetection() {
        String incidentId = "inc-2026-10-15-idemp-test";
        resolutionService.createOrEnsureIncident(TEST_GRAPH, new IncidentInput(incidentId, "Idempotency Test", T0, "P1"));

        ResolutionInput input = new ResolutionInput(
                "service-payment",
                "Increase HikariCP pool",
                "CONFIG_CHANGE",
                "Increased pool size to 50",
                "sre-alice"
        );

        // First confirmation
        ResolutionRecord first = resolutionService.confirmResolution(TEST_GRAPH, incidentId, input);
        assertNotNull(first);
        assertEquals("res-" + incidentId, first.id());

        // Second confirmation with IDENTICAL payload -> succeeds idempotently
        ResolutionRecord second = resolutionService.confirmResolution(TEST_GRAPH, incidentId, input);
        assertNotNull(second);
        assertEquals(first.id(), second.id());
        assertEquals(first.confirmedAt(), second.confirmedAt());

        // Third confirmation with CONFLICTING payload -> throws ResolutionConflictException
        ResolutionInput conflictingInput = new ResolutionInput(
                "service-payment",
                "Different fix entirely",
                "RESTART",
                "Restarted payment service pods",
                "sre-bob"
        );
        assertThrows(ResolutionConflictException.class, () ->
                resolutionService.confirmResolution(TEST_GRAPH, incidentId, conflictingInput)
        );
    }

    @Test
    @Order(5)
    @DisplayName("Dev graph reset cleans up runtime data and restores exact seed counts")
    void testDevGraphResetRestoresSeedCounts() {
        // Add runtime incident and resolution
        String incidentId = "inc-2026-10-15-cleanup-test";
        resolutionService.createOrEnsureIncident(TEST_GRAPH, new IncidentInput(incidentId, "Cleanup Test", T0, "P1"));
        resolutionService.confirmResolution(TEST_GRAPH, incidentId, new ResolutionInput(
                "service-payment", "Temp Fix", "CONFIG_CHANGE", "Action", "sre"
        ));

        // Verify runtime node exists
        assertTrue(repository.findIncidentById(TEST_GRAPH, incidentId).isPresent());

        // Perform clean seed reset
        SeedResult result = seedService.seed(TEST_GRAPH, true);

        // Verify runtime node was wiped
        assertFalse(repository.findIncidentById(TEST_GRAPH, incidentId).isPresent(),
                "Runtime incident must be wiped after reset");

        // Verify seed counts are exactly restored
        assertEquals(10, result.nodeCountsByLabel().get("Service"));
        assertEquals(5, result.nodeCountsByLabel().get("Deployment"));
        assertEquals(3, result.nodeCountsByLabel().get("ErrorSignature"));
        assertEquals(3, result.nodeCountsByLabel().get("Incident"));
        assertEquals(3, result.nodeCountsByLabel().get("Resolution"));
        assertEquals(24, result.totalNodes());
    }
}
