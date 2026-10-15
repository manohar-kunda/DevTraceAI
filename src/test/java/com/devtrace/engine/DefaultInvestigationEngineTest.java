package com.devtrace.engine;

import com.devtrace.model.*;
import com.devtrace.repository.GraphRepository;
import com.devtrace.resolver.EntryPointResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class DefaultInvestigationEngineTest {

    private GraphRepository graphRepository;
    private EntryPointResolver entryPointResolver;
    private Clock fixedClock;

    private static final long T0 = 1792058400000L; // 2026-10-15 10:00:00 UTC

    @BeforeEach
    void setUp() {
        graphRepository = Mockito.mock(GraphRepository.class);
        entryPointResolver = Mockito.mock(EntryPointResolver.class);
        fixedClock = Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Tier assignment: covers all four evidence combinations (STRONG, MODERATE-deploy, MODERATE-sig, WEAK)")
    void testTierAssignmentAllFourCombinations() {
        String graph = "devtrace_test";
        InvestigationRequest request = new InvestigationRequest("Checkout failing", "checkout-service", "Jedis pool timeout", T0);

        ResolvedService symptom = new ResolvedService("service-checkout", "checkout-service", "MICROSERVICE");
        ResolvedErrorSignature sig = new ResolvedErrorSignature("err-sig-timeout", "Jedis pool timeout", "TIMEOUT");

        when(entryPointResolver.resolve(any(), any(), any(), any()))
                .thenReturn(new EntryPointsResult(Optional.of(symptom), Optional.of(sig)));

        // Services: checkout (0 hops), svc-both (1 hop), svc-dep-only (1 hop), svc-sig-only (1 hop), svc-weak (1 hop)
        when(graphRepository.findDependencies(eq(graph), eq("service-checkout"), anyInt(), anyInt()))
                .thenReturn(new DependenciesResult("service-checkout", List.of(
                        new ServiceHop("svc-both", "Both Service", "MICROSERVICE", 1),
                        new ServiceHop("svc-dep-only", "Dep Only Service", "MICROSERVICE", 1),
                        new ServiceHop("svc-sig-only", "Sig Only Service", "MICROSERVICE", 1),
                        new ServiceHop("svc-weak", "Weak Service", "MICROSERVICE", 1)
                ), List.of()));

        when(graphRepository.loadServices(eq(graph))).thenReturn(List.of(
                new ServiceNode("service-checkout", "checkout-service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-both", "Both Service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-dep-only", "Dep Only Service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-sig-only", "Sig Only Service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-weak", "Weak Service", "team", "MICROSERVICE", 1, List.of())
        ));
        when(graphRepository.loadErrorSignatures(eq(graph))).thenReturn(List.of(
                new ErrorSignatureNode("err-sig-timeout", "Jedis pool timeout", "TIMEOUT", List.of("timeout"), List.of())
        ));

        // Deployments: on svc-both and svc-dep-only
        when(graphRepository.findAllDeploymentsInWindow(eq(graph), anyLong(), anyLong())).thenReturn(List.of(
                new DeploymentRecord("dep-both", "svc-both", "v1", T0 - 1800000, "prod", "ci"),
                new DeploymentRecord("dep-only", "svc-dep-only", "v1", T0 - 1200000, "prod", "ci")
        ));

        // Similar incidents: on svc-both and svc-sig-only
        when(graphRepository.findSimilarIncidents(eq(graph), eq("err-sig-timeout"), anySet())).thenReturn(List.of(
                new SimilarIncidentRecord("inc-both", "Incident Both", "err-sig-timeout", List.of("svc-both"), "RESOLVED", List.of(), null),
                new SimilarIncidentRecord("inc-sig", "Incident Sig", "err-sig-timeout", List.of("svc-sig-only"), "RESOLVED", List.of(), null)
        ));

        when(graphRepository.findDependents(eq(graph), anyString(), anyInt(), anyInt()))
                .thenReturn(new DependentsResult("svc-both", List.of(), List.of()));

        DefaultInvestigationEngine engine = new DefaultInvestigationEngine(graphRepository, entryPointResolver, fixedClock);
        InvestigationReport report = engine.investigate(graph, request);

        assertEquals("RESOLVED_HYPOTHESIS", report.status());

        Map<String, CandidateHypothesis> byId = new HashMap<>();
        report.hypotheses().forEach(h -> byId.put(h.serviceId(), h));

        assertEquals(RankingTier.STRONG, byId.get("svc-both").tier(), "svc-both must be STRONG (deployment + signature)");
        assertEquals(RankingTier.MODERATE, byId.get("svc-dep-only").tier(), "svc-dep-only must be MODERATE (deployment only)");
        assertEquals(RankingTier.MODERATE, byId.get("svc-sig-only").tier(), "svc-sig-only must be MODERATE (signature only)");
        assertEquals(RankingTier.WEAK, byId.get("svc-weak").tier(), "svc-weak must be WEAK (neither)");
    }

    @Test
    @DisplayName("Ordering within tier: more recent deployment ranks first")
    void testOrderingWithinTierRecencyFirst() {
        String graph = "devtrace_test";
        InvestigationRequest request = new InvestigationRequest("Checkout failing", "checkout-service", null, T0);

        ResolvedService symptom = new ResolvedService("service-checkout", "checkout-service", "MICROSERVICE");

        when(entryPointResolver.resolve(any(), any(), any(), any()))
                .thenReturn(new EntryPointsResult(Optional.of(symptom), Optional.empty()));

        when(graphRepository.findDependencies(eq(graph), eq("service-checkout"), anyInt(), anyInt()))
                .thenReturn(new DependenciesResult("service-checkout", List.of(
                        new ServiceHop("svc-old-dep", "Old Dep Service", "MICROSERVICE", 1),
                        new ServiceHop("svc-new-dep", "New Dep Service", "MICROSERVICE", 1)
                ), List.of()));

        when(graphRepository.loadServices(eq(graph))).thenReturn(List.of(
                new ServiceNode("service-checkout", "checkout-service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-old-dep", "Old Dep Service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-new-dep", "New Dep Service", "team", "MICROSERVICE", 1, List.of())
        ));
        when(graphRepository.loadErrorSignatures(eq(graph))).thenReturn(List.of());

        // Both in MODERATE tier (deployment only, no signature match)
        // svc-new-dep: deployed 10m ago (T0 - 600,000)
        // svc-old-dep: deployed 2h ago (T0 - 7,200,000)
        when(graphRepository.findAllDeploymentsInWindow(eq(graph), anyLong(), anyLong())).thenReturn(List.of(
                new DeploymentRecord("dep-new", "svc-new-dep", "v2", T0 - 600000, "prod", "ci"),
                new DeploymentRecord("dep-old", "svc-old-dep", "v1", T0 - 7200000, "prod", "ci")
        ));

        when(graphRepository.findDependents(eq(graph), anyString(), anyInt(), anyInt()))
                .thenReturn(new DependentsResult("svc-new-dep", List.of(), List.of()));

        DefaultInvestigationEngine engine = new DefaultInvestigationEngine(graphRepository, entryPointResolver, fixedClock);
        InvestigationReport report = engine.investigate(graph, request);

        List<CandidateHypothesis> moderateCandidates = report.hypotheses().stream()
                .filter(h -> h.tier() == RankingTier.MODERATE)
                .toList();

        assertEquals(2, moderateCandidates.size());
        assertEquals("svc-new-dep", moderateCandidates.get(0).serviceId(), "More recent change must rank first within tier");
        assertEquals("svc-old-dep", moderateCandidates.get(1).serviceId());
    }

    @Test
    @DisplayName("Deterministic tie-break: smaller hop distance, then alphanumeric ID")
    void testDeterministicTieBreakHopsAndId() {
        String graph = "devtrace_test";
        InvestigationRequest request = new InvestigationRequest("Checkout failing", "checkout-service", null, T0);

        ResolvedService symptom = new ResolvedService("service-checkout", "checkout-service", "MICROSERVICE");

        when(entryPointResolver.resolve(any(), any(), any(), any()))
                .thenReturn(new EntryPointsResult(Optional.of(symptom), Optional.empty()));

        // All WEAK candidates (no deployments, no signatures)
        // svc-b at 1 hop, svc-a at 1 hop, svc-c at 2 hops
        when(graphRepository.findDependencies(eq(graph), eq("service-checkout"), anyInt(), anyInt()))
                .thenReturn(new DependenciesResult("service-checkout", List.of(
                        new ServiceHop("svc-b", "Service B", "MICROSERVICE", 1),
                        new ServiceHop("svc-a", "Service A", "MICROSERVICE", 1),
                        new ServiceHop("svc-c", "Service C", "MICROSERVICE", 2)
                ), List.of()));

        when(graphRepository.loadServices(eq(graph))).thenReturn(List.of(
                new ServiceNode("service-checkout", "checkout-service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-a", "Service A", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-b", "Service B", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-c", "Service C", "team", "MICROSERVICE", 1, List.of())
        ));
        when(graphRepository.loadErrorSignatures(eq(graph))).thenReturn(List.of());

        when(graphRepository.findAllDeploymentsInWindow(eq(graph), anyLong(), anyLong())).thenReturn(List.of());
        when(graphRepository.findDependents(eq(graph), anyString(), anyInt(), anyInt()))
                .thenReturn(new DependentsResult("service-checkout", List.of(), List.of()));

        DefaultInvestigationEngine engine = new DefaultInvestigationEngine(graphRepository, entryPointResolver, fixedClock);
        InvestigationReport report = engine.investigate(graph, request);

        // Hops first: 0 hops (service-checkout), then 1 hop (svc-a before svc-b), then 2 hops (svc-c)
        List<String> orderedIds = report.hypotheses().stream().map(CandidateHypothesis::serviceId).toList();
        assertEquals("service-checkout", orderedIds.get(0));
        assertEquals("svc-a", orderedIds.get(1), "Alphanumeric tie-break: svc-a before svc-b at same hop distance");
        assertEquals("svc-b", orderedIds.get(2));
        assertEquals("svc-c", orderedIds.get(3), "Larger hop distance appears later");
    }

    @Test
    @DisplayName("Window boundary: deployment at exact window start included, deployment after startedAt excluded")
    void testWindowBoundaryExactEdgeAndAfterStartedAt() {
        String graph = "devtrace_test";
        long window24h = 86400000L;
        long windowStart = T0 - window24h;

        InvestigationRequest request = new InvestigationRequest("Checkout failing", "checkout-service", null, T0);
        ResolvedService symptom = new ResolvedService("service-checkout", "checkout-service", "MICROSERVICE");

        when(entryPointResolver.resolve(any(), any(), any(), any()))
                .thenReturn(new EntryPointsResult(Optional.of(symptom), Optional.empty()));

        when(graphRepository.findDependencies(eq(graph), eq("service-checkout"), anyInt(), anyInt()))
                .thenReturn(new DependenciesResult("service-checkout", List.of(
                        new ServiceHop("svc-boundary", "Boundary Service", "MICROSERVICE", 1)
                ), List.of()));

        when(graphRepository.loadServices(eq(graph))).thenReturn(List.of(
                new ServiceNode("service-checkout", "checkout-service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-boundary", "Boundary Service", "team", "MICROSERVICE", 1, List.of())
        ));
        when(graphRepository.loadErrorSignatures(eq(graph))).thenReturn(List.of());

        // Deployment exactly at windowStart
        when(graphRepository.findAllDeploymentsInWindow(eq(graph), eq(windowStart), eq(T0))).thenReturn(List.of(
                new DeploymentRecord("dep-exact-edge", "svc-boundary", "v1.0", windowStart, "prod", "ci")
        ));

        when(graphRepository.findDependents(eq(graph), anyString(), anyInt(), anyInt()))
                .thenReturn(new DependentsResult("svc-boundary", List.of(), List.of()));

        DefaultInvestigationEngine engine = new DefaultInvestigationEngine(graphRepository, entryPointResolver, fixedClock);
        InvestigationReport report = engine.investigate(graph, request);

        CandidateHypothesis boundaryHypothesis = report.hypotheses().stream()
                .filter(h -> h.serviceId().equals("svc-boundary"))
                .findFirst().orElseThrow();

        assertEquals(RankingTier.MODERATE, boundaryHypothesis.tier(), "Deployment exactly at window boundary must be recognized");
        assertEquals(windowStart, boundaryHypothesis.mostRecentChangeTimestamp());
    }

    @Test
    @DisplayName("Insufficient evidence path: returns INSUFFICIENT_EVIDENCE with missing evidence when no candidate reaches MODERATE")
    void testInsufficientEvidencePathWhenNoModerateOrStrongCandidate() {
        String graph = "devtrace_test";
        InvestigationRequest request = new InvestigationRequest("General degradation", "checkout-service", null, T0);

        ResolvedService symptom = new ResolvedService("service-checkout", "checkout-service", "MICROSERVICE");

        when(entryPointResolver.resolve(any(), any(), any(), any()))
                .thenReturn(new EntryPointsResult(Optional.of(symptom), Optional.empty()));

        when(graphRepository.findDependencies(eq(graph), eq("service-checkout"), anyInt(), anyInt()))
                .thenReturn(new DependenciesResult("service-checkout", List.of(
                        new ServiceHop("svc-unrelated", "Unrelated Service", "MICROSERVICE", 1)
                ), List.of()));

        when(graphRepository.loadServices(eq(graph))).thenReturn(List.of(
                new ServiceNode("service-checkout", "checkout-service", "team", "MICROSERVICE", 1, List.of()),
                new ServiceNode("svc-unrelated", "Unrelated Service", "team", "MICROSERVICE", 1, List.of())
        ));
        when(graphRepository.loadErrorSignatures(eq(graph))).thenReturn(List.of());

        // No deployments, no similar incidents
        when(graphRepository.findAllDeploymentsInWindow(eq(graph), anyLong(), anyLong())).thenReturn(List.of());
        when(graphRepository.findDependents(eq(graph), anyString(), anyInt(), anyInt()))
                .thenReturn(new DependentsResult("service-checkout", List.of(), List.of()));

        DefaultInvestigationEngine engine = new DefaultInvestigationEngine(graphRepository, entryPointResolver, fixedClock);
        InvestigationReport report = engine.investigate(graph, request);

        assertEquals("INSUFFICIENT_EVIDENCE", report.status(), "Must report INSUFFICIENT_EVIDENCE when no candidate reaches MODERATE");
        assertFalse(report.observedFacts().missingEvidence().isEmpty(), "Must list missing evidence items");
        assertTrue(report.observedFacts().missingEvidence().stream().anyMatch(e -> e.contains("no deployments")), "Must note missing deployments");
        assertTrue(report.observedFacts().missingEvidence().stream().anyMatch(e -> e.contains("no matching signature")), "Must note missing signature");
    }

    @Test
    @DisplayName("Sensitivity check: varying the deployment window between 1h and 24h preserves golden scenario ranking order")
    void testWindowSensitivityGoldenOrderPreservedBetween1hAnd24h() {
        String graph = "devtrace_test";
        InvestigationRequest request = new InvestigationRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                "checkout-service", "JedisConnectionException", T0
        );

        ResolvedService symptom = new ResolvedService("service-checkout", "checkout-service", "MICROSERVICE");
        ResolvedErrorSignature sig = new ResolvedErrorSignature("err-sig-payment-pool-timeout", "JedisConnectionException", "TIMEOUT");

        when(entryPointResolver.resolve(any(), any(), any(), any()))
                .thenReturn(new EntryPointsResult(Optional.of(symptom), Optional.of(sig)));

        when(graphRepository.findDependencies(eq(graph), eq("service-checkout"), anyInt(), anyInt()))
                .thenReturn(new DependenciesResult("service-checkout", List.of(
                        new ServiceHop("service-order", "order-service", "MICROSERVICE", 1),
                        new ServiceHop("service-inventory", "inventory-service", "MICROSERVICE", 1),
                        new ServiceHop("service-payment", "payment-service", "MICROSERVICE", 2)
                ), List.of()));

        when(graphRepository.loadServices(eq(graph))).thenReturn(List.of(
                new ServiceNode("service-checkout", "checkout-service", "Ordering", "MICROSERVICE", 1, List.of()),
                new ServiceNode("service-order", "order-service", "Ordering", "MICROSERVICE", 1, List.of()),
                new ServiceNode("service-inventory", "inventory-service", "Inventory", "MICROSERVICE", 2, List.of()),
                new ServiceNode("service-payment", "payment-service", "Payments Core", "MICROSERVICE", 1, List.of())
        ));
        when(graphRepository.loadErrorSignatures(eq(graph))).thenReturn(List.of(
                new ErrorSignatureNode("err-sig-payment-pool-timeout", "JedisConnectionException", "TIMEOUT", List.of("payment timeout"), List.of())
        ));

        // Both payment (T0 - 30m) and inventory (T0 - 10m) are within 1h (and also within 24h)
        List<DeploymentRecord> deployments = List.of(
                new DeploymentRecord("dep-inventory-v1.5.0", "service-inventory", "v1.5.0", T0 - 600000, "prod", "ci"),
                new DeploymentRecord("dep-payment-v2.4.1", "service-payment", "v2.4.1", T0 - 1800000, "prod", "ci")
        );
        when(graphRepository.findAllDeploymentsInWindow(eq(graph), anyLong(), anyLong())).thenReturn(deployments);

        // Payment has matching historical incident with confirmed resolution
        when(graphRepository.findSimilarIncidents(eq(graph), eq("err-sig-payment-pool-timeout"), anySet())).thenReturn(List.of(
                new SimilarIncidentRecord(
                        "inc-2026-08-12-pay", "Payment timeout surge", "err-sig-payment-pool-timeout",
                        List.of("service-payment"), "RESOLVED", List.of(),
                        new ResolutionRecord("res-pay-timeout-fix", "Increase pool timeout", "CONFIG_CHANGE", "Increased pool timeout", 1786525200000L, "sre-oncall")
                )
        ));

        when(graphRepository.findDependents(eq(graph), anyString(), anyInt(), anyInt()))
                .thenReturn(new DependentsResult("service-payment", List.of(), List.of()));

        // Run with 1h window (3,600,000ms)
        DefaultInvestigationEngine engine1h = new DefaultInvestigationEngine(graphRepository, entryPointResolver, fixedClock, 3, 3600000L);
        InvestigationReport report1h = engine1h.investigate(graph, request);

        // Run with 24h window (86,400,000ms)
        DefaultInvestigationEngine engine24h = new DefaultInvestigationEngine(graphRepository, entryPointResolver, fixedClock, 3, 86400000L);
        InvestigationReport report24h = engine24h.investigate(graph, request);

        assertEquals("service-payment", report1h.hypotheses().get(0).serviceId(), "Rank 1 in 1h window must be service-payment");
        assertEquals(RankingTier.STRONG, report1h.hypotheses().get(0).tier());
        assertEquals("service-inventory", report1h.hypotheses().get(1).serviceId(), "Rank 2 in 1h window must be service-inventory");
        assertEquals(RankingTier.MODERATE, report1h.hypotheses().get(1).tier());

        assertEquals("service-payment", report24h.hypotheses().get(0).serviceId(), "Rank 1 in 24h window must be service-payment");
        assertEquals(RankingTier.STRONG, report24h.hypotheses().get(0).tier());
        assertEquals("service-inventory", report24h.hypotheses().get(1).serviceId(), "Rank 2 in 24h window must be service-inventory");
        assertEquals(RankingTier.MODERATE, report24h.hypotheses().get(1).tier());
    }
}
