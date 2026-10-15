package com.devtrace.engine;

import com.devtrace.config.EnvLoader;
import com.devtrace.model.*;
import com.devtrace.repository.FalkorGraphRepository;
import com.devtrace.resolver.DefaultEntryPointResolver;
import com.devtrace.seed.FalkorSchemaBootstrap;
import com.devtrace.seed.FalkorSeedService;
import tools.jackson.databind.ObjectMapper;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvestigationEngineIT {

    private static final String TEST_GRAPH = "devtrace_test";
    private static final long T0 = 1792058400000L; // 2026-10-15 10:00:00 UTC

    private static Driver driver;
    private static FalkorGraphRepository repository;
    private static FalkorSeedService seedService;
    private static DefaultInvestigationEngine engine;
    private static ObjectMapper objectMapper;

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
        seedService = new FalkorSeedService(driver, bootstrap);
        seedService.seed(TEST_GRAPH, true);

        repository = new FalkorGraphRepository(driver);
        DefaultEntryPointResolver resolver = new DefaultEntryPointResolver();
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC);

        engine = new DefaultInvestigationEngine(repository, resolver, fixedClock, 3, 86400000L);

        objectMapper = new ObjectMapper();
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
    @Order(1)
    @DisplayName("Act 1 Golden Scenario: Full automated multi-factor investigation and report generation")
    void testAct1GoldenInvestigation() throws Exception {
        InvestigationRequest request = new InvestigationRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                null, null, T0
        );

        InvestigationReport report = engine.investigate(TEST_GRAPH, request);

        // 1. Status and entry points
        assertEquals("RESOLVED_HYPOTHESIS", report.status(), "Report status must be RESOLVED_HYPOTHESIS");
        assertTrue(report.entryPoints().service().isPresent(), "Symptom service must be resolved");
        assertEquals("service-checkout", report.entryPoints().service().get().serviceId());
        assertTrue(report.entryPoints().errorSignature().isPresent(), "Error signature must be resolved");
        assertEquals("err-sig-payment-pool-timeout", report.entryPoints().errorSignature().get().signatureId());

        // 2. Candidate Hypotheses (dynamically computed, not hard-coded)
        List<CandidateHypothesis> hypotheses = report.hypotheses();
        assertFalse(hypotheses.isEmpty(), "Hypotheses must not be empty");

        // Rank 1: payment-service (STRONG tier)
        CandidateHypothesis rank1 = hypotheses.get(0);
        assertEquals(1, rank1.rank(), "Rank 1 must have rank=1");
        assertEquals("service-payment", rank1.serviceId(), "Rank 1 candidate must be service-payment");
        assertEquals(RankingTier.STRONG, rank1.tier(), "service-payment must be STRONG tier");
        assertTrue(rank1.hasConfirmedResolution(), "service-payment must have confirmed resolution");
        assertNotNull(rank1.confirmedResolution());
        assertEquals("res-pay-timeout-fix", rank1.confirmedResolution().id());

        // Evidence on Rank 1
        Set<String> rank1EvidenceRecords = rank1.evidence().stream().map(EvidenceItem::recordId).collect(Collectors.toSet());
        assertTrue(rank1EvidenceRecords.contains("dep-payment-v2.4.1"), "Must reference dep-payment-v2.4.1");
        assertTrue(rank1EvidenceRecords.contains("inc-2026-08-12-pay"), "Must reference inc-2026-08-12-pay");
        assertTrue(rank1EvidenceRecords.contains("res-pay-timeout-fix"), "Must reference res-pay-timeout-fix");

        // Rank 2: inventory-service (MODERATE tier)
        CandidateHypothesis rank2 = hypotheses.get(1);
        assertEquals(2, rank2.rank());
        assertEquals("service-inventory", rank2.serviceId(), "Rank 2 candidate must be service-inventory");
        assertEquals(RankingTier.MODERATE, rank2.tier(), "inventory-service must be MODERATE tier (deployment only, no signature match)");
        assertFalse(rank2.hasConfirmedResolution(), "inventory-service must not have confirmed resolution");

        // Verify inventory ranks below payment
        assertTrue(rank1.rank() < rank2.rank(), "Payment must outrank inventory despite inventory being topologically closer and more recent");

        // 3. Ignored Changes: notification deployment is outside neighborhood
        List<IgnoredChange> ignored = report.ignoredChanges();
        assertFalse(ignored.isEmpty(), "Ignored changes must contain out-of-neighborhood deployments");
        assertTrue(ignored.stream().anyMatch(ic -> ic.changeId().equals("dep-notification-v1.8.0")),
                "dep-notification-v1.8.0 must appear in ignoredChanges");
        assertEquals("recent changes outside the dependency neighborhood, not considered",
                ignored.stream().filter(ic -> ic.changeId().equals("dep-notification-v1.8.0")).findFirst().get().reason());

        // service-notification must NOT be in hypotheses
        assertFalse(hypotheses.stream().anyMatch(h -> h.serviceId().equals("service-notification")),
                "service-notification must NOT appear in ranked hypotheses");

        // 4. Historical Incident Retrieval: auth incident is NOT retrieved
        List<SimilarIncidentRecord> matchedIncidents = report.observedFacts().matchedHistoricalIncidents();
        assertTrue(matchedIncidents.stream().anyMatch(i -> i.incidentId().equals("inc-2026-08-12-pay")),
                "inc-2026-08-12-pay must be retrieved");
        assertFalse(matchedIncidents.stream().anyMatch(i -> i.incidentId().equals("inc-2026-09-01-auth")),
                "Auth incident inc-2026-09-01-auth must NOT be retrieved (different signature)");
        assertFalse(matchedIncidents.stream().anyMatch(i -> i.incidentId().equals("inc-2026-07-20-notif")),
                "Notification incident inc-2026-07-20-notif must NOT be retrieved");

        // 5. Suggested Next Steps
        List<SuggestedNextStep> nextSteps = report.suggestedNextSteps();
        assertFalse(nextSteps.isEmpty());
        assertTrue(nextSteps.stream().anyMatch(s -> "FROM_CONFIRMED_RESOLUTION".equals(s.stepType()) && "res-pay-timeout-fix".equals(s.sourceResolutionId())),
                "Must suggest action from confirmed resolution res-pay-timeout-fix");

        // 6. Graph Relationships: Path & Blast Radius
        DependencyPath pathToPayment = report.graphRelationships().pathsToCandidates().get("service-payment");
        assertNotNull(pathToPayment, "Dependency path to service-payment must exist");
        assertEquals(List.of("service-checkout", "service-order", "service-payment"), pathToPayment.serviceIds());
        assertEquals(2, pathToPayment.hopCount());

        DependentsResult blastRadius = report.graphRelationships().blastRadius();
        assertEquals("service-payment", blastRadius.targetServiceId());
        Set<String> blastIds = blastRadius.allDependents().stream().map(ServiceHop::serviceId).collect(Collectors.toSet());
        assertEquals(Set.of("service-order", "service-checkout", "service-gateway"), blastIds,
                "Blast radius must exactly match upstream callers");

        // 7. Graph Record Existence: Every evidence recordId must actually exist in the graph
        for (CandidateHypothesis h : hypotheses) {
            for (EvidenceItem item : h.evidence()) {
                if (!"findDependencies".equals(item.queryName())) { // recordId is entity ID
                    assertTrue(repository.recordExists(TEST_GRAPH, item.recordId()),
                            "Record ID " + item.recordId() + " in evidence must actually exist in the graph");
                }
            }
        }

        // 8. Print full JSON report
        String jsonReport = objectMapper.writeValueAsString(report);
        System.out.println("\n=======================================================");
        System.out.println("ACT 1 INVESTIGATION REPORT (JSON OUTPUT):");
        System.out.println("=======================================================");
        System.out.println(jsonReport);
        System.out.println("=======================================================\n");
    }

    @Test
    @Order(2)
    @DisplayName("Act 3 Insufficient Evidence: Query with no matching signature and no recent deployments on candidate returns INSUFFICIENT_EVIDENCE")
    void testAct3InsufficientEvidence() {
        InvestigationRequest request = new InvestigationRequest(
                "Authentication service latency is slightly elevated without error logs",
                "service-auth", null, T0
        );

        InvestigationReport report = engine.investigate(TEST_GRAPH, request);

        assertEquals("INSUFFICIENT_EVIDENCE", report.status(), "Status must be INSUFFICIENT_EVIDENCE when no candidate reaches MODERATE");
        List<String> missingEvidence = report.observedFacts().missingEvidence();
        assertFalse(missingEvidence.isEmpty(), "Must list missing evidence items");
        assertTrue(missingEvidence.stream().anyMatch(e -> e.contains("no matching signature")),
                "Must declare missing signature");
        assertTrue(missingEvidence.stream().anyMatch(e -> e.contains("no deployments")),
                "Must declare missing deployments on candidate");
        assertTrue(missingEvidence.stream().anyMatch(e -> e.contains("no similar historical incident")),
                "Must declare missing similar historical incident");

        // Verify top candidate is not named as a confirmed root cause (tier is WEAK)
        assertTrue(report.hypotheses().isEmpty() || report.hypotheses().get(0).tier() == RankingTier.WEAK,
                "No candidate must reach MODERATE or STRONG");
    }

    @Test
    @Order(3)
    @DisplayName("Act 5 Empty Graph: Query against empty graph returns INSUFFICIENT_EVIDENCE with zero candidates, then restores")
    void testAct5EmptyGraph() {
        try {
            // Empty the graph
            driver.graph(TEST_GRAPH).query("MATCH (n) DETACH DELETE n");

            InvestigationRequest request = new InvestigationRequest(
                    "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                    null, null, T0
            );

            InvestigationReport report = engine.investigate(TEST_GRAPH, request);

            assertEquals("INSUFFICIENT_EVIDENCE", report.status(), "Empty graph must produce INSUFFICIENT_EVIDENCE");
            assertTrue(report.hypotheses().isEmpty(), "Empty graph must have zero hypotheses");
            assertTrue(report.observedFacts().missingEvidence().stream().anyMatch(e -> e.contains("zero Service nodes")),
                    "Must explicitly report zero Service nodes in missing evidence");

        } finally {
            // Restore test graph
            seedService.seed(TEST_GRAPH, true);
        }
    }
}
